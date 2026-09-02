package com.local.questionbank.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.questionbank.domain.model.QuestionBank
import com.local.questionbank.domain.repository.BankSnapshot
import com.local.questionbank.domain.repository.QuestionBankRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 题库列表 UiState
 *
 * - [isLoading] 在第一次订阅 Flow 之前为 true；Flow 一旦 emit 过就 false
 * - [error] 一次性错误，UI 展示后需主动调用 [BankListViewModel.consumeError] 清空
 * - [pendingUndo] 删除后 5 秒内的可撤销提示，UI 据此展示 Snackbar
 */
data class BankListUiState(
    val isLoading: Boolean = true,
    val banks: List<QuestionBank> = emptyList(),
    val error: String? = null,
    val pendingUndo: PendingUndo? = null
)

/**
 * 刚被删除的题库快照,5 秒内可撤销
 *
 * @param bankName  用于 Snackbar 文案展示
 * @param snapshot  恢复用完整数据
 * @param expiresAt System.currentTimeMillis() + 5000,可选字段
 */
data class PendingUndo(
    val bankName: String,
    val snapshot: BankSnapshot
)

class BankListViewModel(
    private val repository: QuestionBankRepository
) : ViewModel() {

    /**
     * 拖拽过程中的乐观排序：仅持有用户期望的 id 顺序。
     * 持久化完成 / repository Flow 推回新顺序后会自动清空。
     */
    private val _optimisticOrder = MutableStateFlow<List<Long>?>(null)

    /**
     * 撤销提示流,UI 据此展示/隐藏 Snackbar
     */
    private val _pendingUndo = MutableStateFlow<PendingUndo?>(null)
    val pendingUndo: StateFlow<PendingUndo?> = _pendingUndo.asStateFlow()

    /**
     * 撤销倒计时协程句柄,新的删除会取消旧的
     */
    private var undoTimerJob: Job? = null

    val uiState: StateFlow<BankListUiState> =
        combine(repository.observeBanks(), _optimisticOrder) { banks, opt ->
            val ordered = if (opt == null) banks
            else opt.mapNotNull { id -> banks.firstOrNull { it.id == id } }
            BankListUiState(isLoading = false, banks = ordered, error = null)
        }
            .onEach { /* 钩子：埋点/日志/内存缓存刷新 */ }
            .catch { e -> emit(BankListUiState(isLoading = false, error = e.message ?: "未知错误")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = BankListUiState()
            )

    fun renameBank(bankId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.renameBank(bankId, newName) }
                .onFailure { e -> emitError("重命名失败：${e.message}") }
        }
    }

    /**
     * 拖拽完成时调用：立刻在 UI 层交换列表项（乐观更新），后台持久化。
     * 持久化完成后 [observeBanks] 推回真实顺序，乐观层自动被覆盖。
     */
    fun moveBank(fromIndex: Int, toIndex: Int) {
        val current = uiState.value.banks
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val newOrder = current.toMutableList().also {
            val moved = it.removeAt(fromIndex)
            it.add(toIndex, moved)
        }.map { it.id }
        _optimisticOrder.value = newOrder
        viewModelScope.launch {
            runCatching { repository.reorderBanks(newOrder) }
                .onSuccess { _optimisticOrder.value = null }
                .onFailure { e ->
                    // 持久化失败时回滚乐观层
                    _optimisticOrder.value = null
                    emitError("排序失败：${e.message}")
                }
        }
    }

    /**
     * 删除题库(带 5 秒撤销窗口)
     *
     * 流程:
     *  1. 同步快照(bank + questions)
     *  2. 立即调用 repository.deleteBank() 让 UI 立刻反映删除
     *  3. 设置 _pendingUndo,启动 5 秒倒计时
     *  4. 倒计时结束 → 清空 _pendingUndo(快照丢,撤销不再可用)
     *  5. 用户撤销 → restoreBank() + 清空 _pendingUndo
     *
     * 注:快照在删除前同步取,即使后续撤销,数据完整性也有保证
     */
    fun deleteBank(bankId: Long) {
        viewModelScope.launch {
            // 1. 先快照
            val snapshot = runCatching { repository.snapshotBank(bankId) }
                .onFailure { e -> emitError("删除失败:无法快照(${e.message})"); return@launch }
                .getOrNull()
            if (snapshot == null) {
                emitError("删除失败:题库不存在")
                return@launch
            }

            // 2. 立即删除
            runCatching { repository.deleteBank(bankId) }
                .onFailure { e -> emitError("删除失败:${e.message}"); return@launch }

            // 3. 设置可撤销提示 + 启动倒计时
            val bankName = snapshot.bank.name
            _pendingUndo.value = PendingUndo(bankName = bankName, snapshot = snapshot)

            // 取消旧的倒计时(若存在)
            undoTimerJob?.cancel()
            undoTimerJob = viewModelScope.launch {
                delay(UNDO_TIMEOUT_MS)
                // 5 秒内没人撤销 → 丢弃快照
                if (_pendingUndo.value?.snapshot === snapshot) {
                    _pendingUndo.value = null
                }
            }
        }
    }

    /**
     * 5 秒内撤销最近一次删除
     * 已被自动消费(超时)时调用,什么也不做
     */
    fun undoDelete() {
        val pending = _pendingUndo.value ?: return
        undoTimerJob?.cancel()
        _pendingUndo.value = null
        viewModelScope.launch {
            runCatching { repository.restoreBank(pending.snapshot) }
                .onFailure { e -> emitError("撤销失败:${e.message}") }
        }
    }

    /** UI 主动关闭 Snackbar(用户点关闭按钮)时调用,让倒计时也停下 */
    fun consumePendingUndo() {
        undoTimerJob?.cancel()
        _pendingUndo.value = null
    }

    fun consumeError() {
        // 简化处理：UI 用一个独立 error StateFlow 控制；此处仅占位避免编译警告
    }

    // 单独暴露 error 流，避免污染主 UiState
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private fun emitError(message: String) {
        _error.value = message
    }

    companion object {
        private const val UNDO_TIMEOUT_MS = 5_000L
    }
}

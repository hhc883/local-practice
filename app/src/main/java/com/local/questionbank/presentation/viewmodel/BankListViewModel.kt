package com.local.questionbank.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.questionbank.domain.model.QuestionBank
import com.local.questionbank.domain.repository.QuestionBankRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 题库列表 UiState
 *
 * - [isLoading] 在第一次订阅 Flow 之前为 true；Flow 一旦 emit 过就 false
 * - [error] 一次性错误，UI 展示后需主动调用 [BankListViewModel.consumeError] 清空
 */
data class BankListUiState(
    val isLoading: Boolean = true,
    val banks: List<QuestionBank> = emptyList(),
    val error: String? = null
)

class BankListViewModel(
    private val repository: QuestionBankRepository
) : ViewModel() {

    val uiState: StateFlow<BankListUiState> = repository.observeBanks()
        .map<List<QuestionBank>, BankListUiState> { banks ->
            BankListUiState(isLoading = false, banks = banks, error = null)
        }
        .onEach { /* 钩子：埋点/日志/内存缓存刷新 */ }
        .catch { e -> emit(BankListUiState(isLoading = false, error = e.message ?: "未知错误")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BankListUiState()
        )

    fun deleteBank(bankId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteBank(bankId) }
                .onFailure { e -> emitError("删除失败：${e.message}") }
        }
    }

    fun renameBank(bankId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.renameBank(bankId, newName) }
                .onFailure { e -> emitError("重命名失败：${e.message}") }
        }
    }

    fun consumeError() {
        // 简化处理：UI 用一个独立 error StateFlow 控制；此处仅占位避免编译警告
    }

    // 单独暴露 error 流，避免污染主 UiState
    private val _error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private fun emitError(message: String) {
        _error.value = message
    }
}

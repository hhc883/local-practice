package com.local.questionbank.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.model.QuestionType
import com.local.questionbank.domain.repository.AiAssistException
import com.local.questionbank.domain.repository.AiAssistantRepository
import com.local.questionbank.domain.repository.AiSettingsRepository
import com.local.questionbank.domain.repository.QuestionBankRepository
import com.local.questionbank.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 题库 AI 出题状态机
 *
 * 流程:
 *  [Idle] → start() → [LoadingSource] → load 题库原题
 *  [LoadingSource] → [Generating, index=k] → 串行生成,k = 0..N-1
 *  [Generating, index>=N] → [AllReady] → 用户逐题作答
 *  [AllReady] → saveAll() → [Saving] → 完成后 [Finished]
 *  任意阶段 → discardAll() → [Finished]
 */
data class BankAiQuizItem(
    /** AI 生成的新题(尚未入库) */
    val question: Question,
    /** 用户作答(选项下标集合 或 文本) */
    val selected: Set<Int> = emptySet(),
    val textAnswer: String = "",
    val submitted: Boolean = false,
    val isCorrect: Boolean? = null
)

data class BankAiQuizUiState(
    val bankId: Long = 0,
    val bankName: String = "",
    val sourceCount: Int = 0,
    /** 已生成的题(索引对应 source 中第 i 题) */
    val items: List<BankAiQuizItem> = emptyList(),
    /** -1 = 未开始; 0..N-1 = 当前正在生成; N = 全部生成完毕 */
    val generatingIndex: Int = -1,
    val generationError: String? = null,
    val isSaving: Boolean = false,
    val savedCount: Int = 0,
    val isFinished: Boolean = false,
    val needsApiKey: Boolean = false,
    val errorMessage: String? = null
) {
    val totalCount: Int get() = items.size

    val isAllGenerated: Boolean
        get() = items.size == sourceCount && generatingIndex >= sourceCount

    val allSubmitted: Boolean
        get() = items.isNotEmpty() && items.all { it.submitted }

    val correctCount: Int
        get() = items.count { it.isCorrect == true }
}

class BankAiQuizViewModel(
    private val questionRepository: QuestionRepository,
    private val questionBankRepository: QuestionBankRepository,
    private val aiAssistantRepository: AiAssistantRepository,
    private val aiSettingsRepository: AiSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BankAiQuizUiState())
    val uiState: StateFlow<BankAiQuizUiState> = _uiState.asStateFlow()

    private var sourceQuestions: List<Question> = emptyList()

    /**
     * 启动批量出题:
     *  1) 检查 API Key
     *  2) 加载题库原题
     *  3) 串行调 AI 生成同知识点新题
     */
    fun start(bankId: Long, bankName: String) {
        if (_uiState.value.generatingIndex >= 0) return  // 已经在跑
        viewModelScope.launch {
            val key = aiSettingsRepository.observeApiKey().first()
            if (key.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    bankId = bankId, bankName = bankName, needsApiKey = true
                )
                return@launch
            }

            // 加载题库原题
            val source = try {
                questionRepository.observeQuestions(bankId).first()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    bankId = bankId, bankName = bankName,
                    errorMessage = "加载题库失败: ${e.message ?: e.javaClass.simpleName}"
                )
                return@launch
            }

            if (source.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    bankId = bankId, bankName = bankName, sourceCount = 0,
                    isFinished = true, errorMessage = "题库为空,无法生成"
                )
                return@launch
            }

            sourceQuestions = source
            _uiState.value = BankAiQuizUiState(
                bankId = bankId, bankName = bankName, sourceCount = source.size
            )
            generateAll(key)
        }
    }

    private suspend fun generateAll(key: String) {
        for (index in sourceQuestions.indices) {
            _uiState.value = _uiState.value.copy(generatingIndex = index, generationError = null)
            val result = runCatching {
                aiAssistantRepository.generateSimilarQuestion(sourceQuestions[index])
            }
            result.onSuccess { q ->
                val newItems = _uiState.value.items + BankAiQuizItem(question = q)
                _uiState.value = _uiState.value.copy(items = newItems)
            }.onFailure { e ->
                val msg = when (e) {
                    is AiAssistException -> e.message ?: "AI 异常"
                    else -> e.message ?: e.javaClass.simpleName
                }
                // 单道失败:跳过该题,继续下一道(累计错误给用户看)
                _uiState.value = _uiState.value.copy(
                    generationError = "第 ${index + 1} 题生成失败: $msg"
                )
            }
        }
        // 全部完成(无论中间有无失败)
        _uiState.value = _uiState.value.copy(generatingIndex = sourceQuestions.size)
    }

    /** 切换某题的选项(单选/多选) */
    fun toggleOption(itemIndex: Int, optionIndex: Int) {
        val items = _uiState.value.items
        if (itemIndex !in items.indices) return
        val item = items[itemIndex]
        if (item.submitted) return
        val q = item.question
        val newSelected = when (q.type) {
            QuestionType.SINGLE, QuestionType.JUDGE, QuestionType.DEBUG -> setOf(optionIndex)
            else -> if (optionIndex in item.selected) item.selected - optionIndex
                     else item.selected + optionIndex
        }
        _uiState.value = _uiState.value.copy(
            items = items.toMutableList().also { it[itemIndex] = item.copy(selected = newSelected) }
        )
    }

    /** 更新某题的文本输入(填空/编程) */
    fun updateTextAnswer(itemIndex: Int, text: String) {
        val items = _uiState.value.items
        if (itemIndex !in items.indices) return
        val item = items[itemIndex]
        if (item.submitted) return
        _uiState.value = _uiState.value.copy(
            items = items.toMutableList().also { it[itemIndex] = item.copy(textAnswer = text) }
        )
    }

    /** 提交某题作答并判分 */
    fun submitAnswer(itemIndex: Int) {
        val items = _uiState.value.items
        if (itemIndex !in items.indices) return
        val item = items[itemIndex]
        if (item.submitted) return
        val q = item.question
        val correct = when (q.type) {
            QuestionType.PROG -> false
            QuestionType.BLANK -> {
                if (item.textAnswer.isBlank()) false
                else q.answer.any { item.textAnswer.trim().equals(it.trim(), ignoreCase = true) }
            }
            else -> {
                if (item.selected.isEmpty()) false
                else item.selected.sorted().map { it.toString() } == q.answer.sorted()
            }
        }
        _uiState.value = _uiState.value.copy(
            items = items.toMutableList().also {
                it[itemIndex] = item.copy(submitted = true, isCorrect = correct)
            }
        )
    }

    /** 重做某题(清空作答,保留题目) */
    fun resetAnswer(itemIndex: Int) {
        val items = _uiState.value.items
        if (itemIndex !in items.indices) return
        val item = items[itemIndex]
        _uiState.value = _uiState.value.copy(
            items = items.toMutableList().also {
                it[itemIndex] = item.copy(
                    selected = emptySet(), textAnswer = "", submitted = false, isCorrect = null
                )
            }
        )
    }

    /** 把全部 AI 生成的题入库 */
    fun saveAllToBank() {
        val state = _uiState.value
        if (state.items.isEmpty() || state.isSaving) return
        if (state.bankId == 0L) {
            _uiState.value = state.copy(errorMessage = "bankId 异常,无法入库")
            return
        }
        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            var ok = 0
            val items = _uiState.value.items
            for (item in items) {
                val r = runCatching { questionBankRepository.addQuestion(state.bankId, item.question) }
                r.onSuccess { ok++ }
            }
            _uiState.value = _uiState.value.copy(
                isSaving = false, isFinished = true, savedCount = ok,
                errorMessage = if (ok < items.size) "已入库 $ok / ${items.size},部分失败" else null
            )
        }
    }

    fun discardAll() {
        _uiState.value = _uiState.value.copy(isFinished = true)
    }

    fun consumeNeedsApiKey() {
        _uiState.value = _uiState.value.copy(needsApiKey = false)
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
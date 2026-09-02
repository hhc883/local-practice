package com.local.questionbank.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.repository.AiAssistException
import com.local.questionbank.domain.repository.AiAssistantRepository
import com.local.questionbank.domain.repository.QuestionBankRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AI 出题 / JSON 修复 共用的 ViewModel
 *
 * 两种场景共用一个 VM,通过不同的方法触发:
 *  - generateSimilar(question): 答题页用
 *  - fixJson(rawJson, errorMsg): 导入失败页用
 */
data class AiAssistUiState(
    val isLoading: Boolean = false,
    val generatedQuestion: Question? = null,
    val fixedJson: String? = null,
    val diff: List<com.local.questionbank.data.datasource.DiffEntry> = emptyList(),
    val errorMessage: String? = null,
    val errorSuggestion: String? = null,
    val savedToBankId: Long? = null
)

class AiAssistViewModel(
    private val aiAssistantRepository: AiAssistantRepository,
    private val questionBankRepository: QuestionBankRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistUiState())
    val uiState: StateFlow<AiAssistUiState> = _uiState.asStateFlow()

    /** 生成同知识点新题 */
    fun generateSimilar(current: Question) {
        _uiState.value = AiAssistUiState(isLoading = true)
        viewModelScope.launch {
            runCatching { aiAssistantRepository.generateSimilarQuestion(current) }
                .onSuccess { q ->
                    _uiState.value = AiAssistUiState(generatedQuestion = q)
                }
                .onFailure { e -> handleError(e) }
        }
    }

    /** 修复 JSON */
    fun fixJson(rawJson: String, errorMessage: String) {
        _uiState.value = AiAssistUiState(isLoading = true)
        viewModelScope.launch {
            runCatching { aiAssistantRepository.fixJson(rawJson, errorMessage) }
                .onSuccess { result ->
                    _uiState.value = if (result.success) {
                        AiAssistUiState(
                            fixedJson = result.fixedJson,
                            diff = result.diff
                        )
                    } else {
                        AiAssistUiState(
                            errorMessage = result.errorMessage,
                            errorSuggestion = result.errorSuggestion
                        )
                    }
                }
                .onFailure { e -> handleError(e) }
        }
    }

    /** 把生成的新题保存到指定题库 */
    fun saveGeneratedToBank(bankId: Long) {
        val q = _uiState.value.generatedQuestion ?: return
        viewModelScope.launch {
            runCatching { questionBankRepository.addQuestion(bankId, q) }
                .onSuccess { newId ->
                    _uiState.update {
                        it.copy(
                            generatedQuestion = q.copy(id = newId, bankId = bankId),
                            savedToBankId = bankId
                        )
                    }
                }
                .onFailure { e -> handleError(e) }
        }
    }

    fun dismiss() {
        _uiState.value = AiAssistUiState()
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun consumeSavedSignal() {
        _uiState.update { it.copy(savedToBankId = null) }
    }

    private fun handleError(e: Throwable) {
        val msg = when (e) {
            is AiAssistException -> e.message ?: "AI 助手异常"
            else -> e.message ?: e.javaClass.simpleName
        }
        _uiState.value = AiAssistUiState(errorMessage = msg)
    }

    private fun MutableStateFlow<AiAssistUiState>.update(block: (AiAssistUiState) -> AiAssistUiState) {
        value = block(value)
    }
}
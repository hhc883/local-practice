package com.local.questionbank.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.model.QuestionType
import com.local.questionbank.domain.repository.AnswerRepository
import com.local.questionbank.domain.repository.FavoriteRepository
import com.local.questionbank.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 刷题页 UiState（按题目追踪版本）
 *
 * 每道题的作答状态独立存储，互不干扰，HorizontalPager 滑动时不会串题。
 */
data class QuestionUiState(
    val isLoading: Boolean = true,
    val questions: List<Question> = emptyList(),
    val currentIndex: Int = 0,
    /** 已提交判题的题目 ID 集合 */
    val submittedQuestionIds: Set<Long> = emptySet(),
    /** 每道题的已选选项 Map<题目ID, Set<选项下标>> */
    val selectedByQuestion: Map<Long, Set<Int>> = emptyMap(),
    /** 每道题的判题结果 Map<题目ID, Boolean> */
    val isCorrectByQuestion: Map<Long, Boolean> = emptyMap(),
    /** 每道题的填空输入 Map<题目ID, 用户输入文本> */
    val userAnswerTextByQuestion: Map<Long, String> = emptyMap(),
    val isFinished: Boolean = false,
    val isFavorited: Boolean = false,
    val errorMessage: String? = null
) {
    val current: Question? get() = questions.getOrNull(currentIndex)
    val progress: Float
        get() = if (questions.isEmpty()) 0f else (currentIndex + 1f) / questions.size

    fun isQuestionSubmitted(questionId: Long): Boolean = questionId in submittedQuestionIds
    fun getSelected(questionId: Long): Set<Int> = selectedByQuestion[questionId] ?: emptySet()
    fun getUserAnswerText(questionId: Long): String = userAnswerTextByQuestion[questionId] ?: ""

    /** 当前题是否已提交（用于按钮文案判断） */
    val isCurrentSubmitted: Boolean
        get() = current?.id in submittedQuestionIds

    /** 当前题是否可翻页（多选/填空未判题禁止） */
    val canNavigateNext: Boolean
        get() {
            val q = current ?: return true
            return when (q.type) {
                QuestionType.MULTI, QuestionType.BLANK -> q.id in submittedQuestionIds
                else -> true
            }
        }
}

/** 刷题模式：顺序 / 随机 / 错题 / 收藏 */
enum class PracticeMode { ORDERED, RANDOM, WRONG_ONLY, FAVORITE_ONLY }

class QuestionViewModel(
    private val questionRepository: QuestionRepository,
    private val answerRepository: AnswerRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuestionUiState())
    val uiState: StateFlow<QuestionUiState> = _uiState.asStateFlow()

    fun load(bankId: Long, mode: PracticeMode = PracticeMode.ORDERED) {
        _uiState.value = QuestionUiState(isLoading = true)
        viewModelScope.launch {
            runCatching {
                val source: kotlinx.coroutines.flow.Flow<List<Question>> = when (mode) {
                    PracticeMode.ORDERED ->
                        if (bankId == 0L) questionRepository.observeAllOrdered()
                        else questionRepository.observeQuestions(bankId)
                    PracticeMode.RANDOM -> {
                        if (bankId == 0L) questionRepository.observeAllRandom()
                        else questionRepository.observeRandomQuestions(bankId)
                    }
                    PracticeMode.FAVORITE_ONLY ->
                        if (bankId == 0L) questionRepository.observeAllFavoriteQuestions()
                        else questionRepository.observeFavoriteQuestions(bankId)
                    PracticeMode.WRONG_ONLY -> {
                        if (bankId == 0L) {
                            return@runCatching applyQuestions(
                                questionRepository.observeAllWrongQuestions().first()
                            )
                        }
                        val all = questionRepository.observeQuestions(bankId).first()
                        val wrong = answerRepository.observeWrongQuestionIds().first().toHashSet()
                        return@runCatching applyQuestions(all.filter { it.id in wrong })
                    }
                }
                applyQuestions(source.first())
            }.onFailure { e ->
                _uiState.value = QuestionUiState(
                    isLoading = false,
                    errorMessage = e.message ?: "加载失败"
                )
            }
        }
    }

    private fun applyQuestions(list: List<Question>) {
        _uiState.value = QuestionUiState(isLoading = false, questions = list)
        observeCurrentFavorite()
    }

    private var favoriteObserverJob: kotlinx.coroutines.Job? = null

    private fun observeCurrentFavorite() {
        favoriteObserverJob?.cancel()
        val current = _uiState.value.current ?: return
        favoriteObserverJob = viewModelScope.launch {
            favoriteRepository.observeIsFavorited(current.id).collect { favorited ->
                _uiState.update { it.copy(isFavorited = favorited) }
            }
        }
    }

    fun toggleFavorite() {
        val current = _uiState.value.current ?: return
        viewModelScope.launch {
            runCatching { favoriteRepository.toggleFavorite(current.id) }
                .onFailure { e ->
                    _uiState.update { it.copy(errorMessage = "收藏失败：${e.message}") }
                }
        }
    }

    fun toggleOption(questionId: Long, index: Int) {
        val state = _uiState.value
        val q = state.questions.find { it.id == questionId } ?: return
        if (state.isQuestionSubmitted(questionId)) return

        val currentSelected = state.getSelected(questionId)
        val newSelected = if (q.type == QuestionType.SINGLE || q.type == QuestionType.JUDGE || q.type == QuestionType.DEBUG) {
            setOf(index)
        } else {
            if (index in currentSelected) currentSelected - index else currentSelected + index
        }

        _uiState.update {
            it.copy(selectedByQuestion = it.selectedByQuestion + (questionId to newSelected))
        }

        // 单选/判断/挑错题点选即判
        if (q.type == QuestionType.SINGLE || q.type == QuestionType.JUDGE || q.type == QuestionType.DEBUG) {
            submitQuestion(questionId)
        }
    }

    private fun submitQuestion(questionId: Long) {
        val state = _uiState.value
        val q = state.questions.find { it.id == questionId } ?: return
        if (state.isQuestionSubmitted(questionId)) return

        val correct = if (q.type == QuestionType.BLANK) {
            val text = state.getUserAnswerText(questionId)
            if (text.isBlank()) false
            else q.answer.any { text.trim().equals(it.trim(), ignoreCase = true) }
        } else {
            val selected = state.getSelected(questionId)
            if (selected.isEmpty()) false
            else selected.sorted().map { it.toString() } == q.answer.sorted()
        }

        viewModelScope.launch {
            runCatching {
                answerRepository.record(
                    com.local.questionbank.domain.model.AnswerRecord(
                        questionId = q.id,
                        isCorrect = correct
                    )
                )
            }
            _uiState.update {
                it.copy(
                    submittedQuestionIds = it.submittedQuestionIds + questionId,
                    isCorrectByQuestion = it.isCorrectByQuestion + (questionId to correct)
                )
            }
        }
    }

    fun submit() {
        val state = _uiState.value
        val current = state.current ?: return
        submitQuestion(current.id)
    }

    fun submitIfAllowed() {
        val state = _uiState.value
        val current = state.current ?: return
        if ((current.type == QuestionType.MULTI || current.type == QuestionType.BLANK) && !state.isCurrentSubmitted) {
            submitQuestion(current.id)
        }
    }

    fun updateUserAnswerText(questionId: Long, text: String) {
        _uiState.update {
            it.copy(userAnswerTextByQuestion = it.userAnswerTextByQuestion + (questionId to text))
        }
    }

    private fun doNavigateNext() {
        val state = _uiState.value
        val nextIndex = state.currentIndex + 1
        if (nextIndex >= state.questions.size) {
            _uiState.update { it.copy(isFinished = true) }
        } else {
            _uiState.update { it.copy(currentIndex = nextIndex) }
            observeCurrentFavorite()
        }
    }

    fun next() {
        val state = _uiState.value
        val current = state.current ?: return

        if ((current.type == QuestionType.MULTI || current.type == QuestionType.BLANK) && !state.isCurrentSubmitted) {
            submitQuestion(current.id)
        } else {
            doNavigateNext()
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onPageChanged(pageIndex: Int) {
        val state = _uiState.value
        val prev = state.questions.getOrNull(state.currentIndex)
        // 多选/填空未判题时滑动走：丢弃已选
        if (prev != null && !state.isQuestionSubmitted(prev.id)) {
            _uiState.update {
                it.copy(
                    selectedByQuestion = it.selectedByQuestion + (prev.id to emptySet()),
                    userAnswerTextByQuestion = it.userAnswerTextByQuestion + (prev.id to "")
                )
            }
        }
        _uiState.update { it.copy(currentIndex = pageIndex) }
        observeCurrentFavorite()
    }
}

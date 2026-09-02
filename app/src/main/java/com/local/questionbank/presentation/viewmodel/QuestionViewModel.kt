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
    val errorMessage: String? = null,
    /** AI 出题:正在请求 */
    val aiGenerating: Boolean = false,
    /** AI 出题:已生成的新题(展示在底部 sheet) */
    val aiGeneratedQuestion: Question? = null,
    /** AI 出题:用户对生成题已选的下标(SINGLE/MULTI) */
    val aiSelected: Set<Int> = emptySet(),
    /** AI 出题:用户对生成题已输入的文本(BLANK/PROG) */
    val aiTextAnswer: String = "",
    /** AI 出题:用户是否已提交 */
    val aiSubmitted: Boolean = false,
    /** AI 出题:判分结果 */
    val aiIsCorrect: Boolean? = null,
    /** AI 出题:已保存到题库 */
    val aiSavedToast: String? = null,
    /** AI 出题:用户尚未配置 API Key */
    val aiNeedsApiKey: Boolean = false
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

    /** 当前题是否可翻页（多选/填空/编程题未判题禁止） */
    val canNavigateNext: Boolean
        get() {
            val q = current ?: return true
            return when (q.type) {
                QuestionType.MULTI, QuestionType.BLANK, QuestionType.PROG -> q.id in submittedQuestionIds
                else -> true
            }
        }
}

/** 刷题模式：顺序 / 随机 / 错题 / 收藏 */
enum class PracticeMode { ORDERED, RANDOM, WRONG_ONLY, FAVORITE_ONLY }

class QuestionViewModel(
    private val questionRepository: QuestionRepository,
    private val answerRepository: AnswerRepository,
    private val favoriteRepository: FavoriteRepository,
    private val aiAssistantRepository: com.local.questionbank.domain.repository.AiAssistantRepository,
    private val aiSettingsRepository: com.local.questionbank.domain.repository.AiSettingsRepository,
    private val questionBankRepository: com.local.questionbank.domain.repository.QuestionBankRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuestionUiState())
    val uiState: StateFlow<QuestionUiState> = _uiState.asStateFlow()

    /** 当前正在刷题的 mode,供 submitQuestion 判断是否需要在答对后清除错题记录 */
    private var currentMode: PracticeMode = PracticeMode.ORDERED

    /** 当前题库 id(0 表示全局错题/收藏),用于 AI 出题入库 */
    private var currentBankId: Long = 0L

    fun load(bankId: Long, mode: PracticeMode = PracticeMode.ORDERED) {
        currentMode = mode
        currentBankId = bankId
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

    /**
     * 手动将当前题目从错题本中删除(同时清除答题记录)
     * 用户可能在错题本中看到一道题但不想再刷,可主动移除。
     * 删除后立刻从 questions 列表中移除,并翻到下一题(带位置修正)。
     */
    fun removeFromWrongBook() {
        val state = _uiState.value
        val current = state.current ?: return
        if (currentMode != PracticeMode.WRONG_ONLY) return
        viewModelScope.launch {
            runCatching { answerRepository.clearRecords(current.id) }
                .onFailure { e ->
                    _uiState.update { it.copy(errorMessage = "移除失败：${e.message}") }
                    return@launch
                }
            // 删除成功后:从本地 questions 中移除该题
            val newQuestions = state.questions.toMutableList().also {
                it.removeAt(state.currentIndex)
            }
            if (newQuestions.isEmpty()) {
                _uiState.update { it.copy(questions = newQuestions, isFinished = true) }
            } else {
                val newIndex = state.currentIndex.coerceIn(0, newQuestions.lastIndex)
                _uiState.update {
                    it.copy(
                        questions = newQuestions,
                        currentIndex = newIndex
                    )
                }
                observeCurrentFavorite()
            }
        }
    }

    /**
     * 请求 AI 生成同知识点新题
     *
     * 1) 先检查 API Key,缺失则设 aiNeedsApiKey 让 UI 提示用户去设置
     * 2) 否则调 aiAssistantRepository.generateSimilarQuestion,设 aiGeneratedQuestion
     */
    fun requestAiSimilar() {
        val current = _uiState.value.current ?: return
        if (_uiState.value.aiGenerating) return
        viewModelScope.launch {
            // 检查 API Key
            val key = aiSettingsRepository.observeApiKey().first()
            if (key.isNullOrBlank()) {
                _uiState.update { it.copy(aiNeedsApiKey = true) }
                return@launch
            }
            // 重置上一题的所有 AI 答题状态
            _uiState.update {
                it.copy(
                    aiGenerating = true,
                    aiGeneratedQuestion = null,
                    aiSelected = emptySet(),
                    aiTextAnswer = "",
                    aiSubmitted = false,
                    aiIsCorrect = null
                )
            }
            runCatching { aiAssistantRepository.generateSimilarQuestion(current) }
                .onSuccess { q ->
                    _uiState.update {
                        it.copy(aiGenerating = false, aiGeneratedQuestion = q)
                    }
                }
                .onFailure { e ->
                    val msg = when (e) {
                        is com.local.questionbank.domain.repository.AiAssistException -> e.message ?: "AI 异常"
                        else -> e.message ?: e.javaClass.simpleName
                    }
                    _uiState.update {
                        it.copy(aiGenerating = false, errorMessage = "AI 出题失败: $msg")
                    }
                }
        }
    }

    /** 切换 AI 题目选项(SINGLE 单选 / MULTI 多选) */
    fun toggleAiOption(index: Int) {
        val q = _uiState.value.aiGeneratedQuestion ?: return
        if (_uiState.value.aiSubmitted) return
        val current = _uiState.value.aiSelected
        val newSet = when (q.type) {
            QuestionType.SINGLE, QuestionType.JUDGE, QuestionType.DEBUG -> setOf(index)
            else -> if (index in current) current - index else current + index
        }
        _uiState.update { it.copy(aiSelected = newSet) }
    }

    /** 更新 AI 题目的文本输入(BLANK / PROG) */
    fun updateAiTextAnswer(text: String) {
        _uiState.update { it.copy(aiTextAnswer = text) }
    }

    /**
     * 提交 AI 题作答并判分
     *
     * 判分规则与 submitQuestion 一致:
     *  - PROG: 视为"不正确"(代码比对不可靠)
     *  - BLANK: trim + equalsIgnoreCase 与答案任一匹配
     *  - 其它: 选下标集合与答案完全相同
     */
    fun submitAiAnswer() {
        val q = _uiState.value.aiGeneratedQuestion ?: return
        if (_uiState.value.aiSubmitted) return

        val correct = when (q.type) {
            QuestionType.PROG -> false
            QuestionType.BLANK, QuestionType.UNKNOWN -> {
                val text = _uiState.value.aiTextAnswer
                if (text.isBlank()) false
                else q.answer.any { text.trim().equals(it.trim(), ignoreCase = true) }
            }
            else -> {
                val selected = _uiState.value.aiSelected
                if (selected.isEmpty()) false
                else selected.sorted().map { it.toString() } == q.answer.sorted()
            }
        }

        _uiState.update { it.copy(aiSubmitted = true, aiIsCorrect = correct) }
    }

    /** 重做 AI 题(清空作答,保留题目) */
    fun resetAiAnswer() {
        _uiState.update {
            it.copy(
                aiSelected = emptySet(),
                aiTextAnswer = "",
                aiSubmitted = false,
                aiIsCorrect = null
            )
        }
    }

    /**
     * 把 AI 生成的新题保存到当前题库
     * - 当前 bankId=0(全局错题/收藏/全局模式)时,不允许入库,提示用户先去具体题库
     */
    fun saveAiGenerated() {
        val q = _uiState.value.aiGeneratedQuestion ?: return
        if (currentBankId == 0L) {
            _uiState.update {
                it.copy(errorMessage = "当前在全局模式下,无法入库;请进入具体题库后再使用 AI 出题")
            }
            return
        }
        viewModelScope.launch {
            runCatching { questionBankRepository.addQuestion(currentBankId, q) }
                .onSuccess { newId ->
                    _uiState.update {
                        it.copy(
                            aiGeneratedQuestion = q.copy(id = newId, bankId = currentBankId),
                            aiSavedToast = "已加入题库"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(errorMessage = "保存失败: ${e.message ?: e.javaClass.simpleName}") }
                }
        }
    }

    fun dismissAiGenerated() {
        _uiState.update {
            it.copy(
                aiGeneratedQuestion = null,
                aiGenerating = false,
                aiSelected = emptySet(),
                aiTextAnswer = "",
                aiSubmitted = false,
                aiIsCorrect = null
            )
        }
    }

    fun consumeAiNeedsApiKey() {
        _uiState.update { it.copy(aiNeedsApiKey = false) }
    }

    fun consumeAiSavedToast() {
        _uiState.update { it.copy(aiSavedToast = null) }
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

        val correct = when (q.type) {
            QuestionType.PROG -> false   // 编程题不自动判分,提交即视为"未通过"
            QuestionType.BLANK, QuestionType.UNKNOWN -> {
                val text = state.getUserAnswerText(questionId)
                if (text.isBlank()) false
                else q.answer.any { text.trim().equals(it.trim(), ignoreCase = true) }
            }
            else -> {
                val selected = state.getSelected(questionId)
                if (selected.isEmpty()) false
                else selected.sorted().map { it.toString() } == q.answer.sorted()
            }
        }

        viewModelScope.launch {
            runCatching {
                answerRepository.record(
                    com.local.questionbank.domain.model.AnswerRecord(
                        questionId = q.id,
                        isCorrect = correct
                    )
                )
                // 错题本模式:答对一次即从错题本中删除
                if (correct && currentMode == PracticeMode.WRONG_ONLY) {
                    answerRepository.clearRecords(q.id)
                }
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
        val needsSubmitBeforeNext = current.type == QuestionType.MULTI ||
                current.type == QuestionType.BLANK ||
                current.type == QuestionType.PROG
        if (needsSubmitBeforeNext && !state.isCurrentSubmitted) {
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

        val needsSubmitBeforeNext = current.type == QuestionType.MULTI ||
                current.type == QuestionType.BLANK ||
                current.type == QuestionType.PROG
        if (needsSubmitBeforeNext && !state.isCurrentSubmitted) {
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

package com.local.questionbank.presentation.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.local.questionbank.di.AppContainer
import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.model.QuestionType
import com.local.questionbank.presentation.viewmodel.AppViewModelFactory
import com.local.questionbank.presentation.viewmodel.PracticeMode
import com.local.questionbank.presentation.viewmodel.QuestionViewModel

/**
 * 刷题页
 *
 * 行为：
 *  - 加载题库题目（默认顺序，可传 RANDOM / WRONG_ONLY）
 *  - 渲染题干 + 选项
 *  - 提交后显示对错与正确答案
 *  - "下一题" 翻页，"收藏" 仅作 UI 占位（后续接收藏表）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionScreen(
    container: AppContainer,
    bankId: Long,
    modeName: String,
    onBack: () -> Unit
) {
    val viewModel: QuestionViewModel = viewModel(
        factory = AppViewModelFactory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(
        initialPage = state.currentIndex,
        pageCount = { state.questions.size }
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != state.currentIndex) {
            viewModel.onPageChanged(pagerState.currentPage)
        }
    }

    LaunchedEffect(state.currentIndex) {
        if (pagerState.currentPage != state.currentIndex && state.questions.isNotEmpty()) {
            pagerState.scrollToPage(state.currentIndex)
        }
    }

    LaunchedEffect(bankId, modeName) {
        val mode = runCatching { PracticeMode.valueOf(modeName) }.getOrDefault(PracticeMode.ORDERED)
        viewModel.load(bankId, mode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "刷题 ${state.currentIndex + 1} / ${state.questions.size.coerceAtLeast(1)}"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleFavorite() },
                        enabled = state.current != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = if (state.isFavorited) "取消收藏" else "收藏",
                            tint = if (state.isFavorited) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )

            if (state.questions.isNotEmpty()) {
                QuestionTypeBar(
                    questions = state.questions,
                    currentIndex = state.currentIndex,
                    onDotClick = { index ->
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                val question = state.questions.getOrNull(pageIndex) ?: return@HorizontalPager
                val isCurrentPage = pageIndex == state.currentIndex
                QuestionBody(
                    question = question,
                    selected = state.getSelected(question.id),
                    submitted = state.isQuestionSubmitted(question.id),
                    isCorrect = state.isCorrectByQuestion[question.id],
                    correctAnswer = question.answer,
                    onToggle = { index -> viewModel.toggleOption(question.id, index) },
                    userAnswerText = state.getUserAnswerText(question.id),
                    onUserTextChange = { viewModel.updateUserAnswerText(question.id, it) }
                )
            }

            if (!state.isFinished && state.current != null) {
                val isMultiUnsubmitted = !state.isCurrentSubmitted && (state.current?.type == QuestionType.MULTI || state.current?.type == QuestionType.BLANK)
                Button(
                    onClick = {
                        if (isMultiUnsubmitted) {
                            viewModel.submitIfAllowed()
                        } else {
                            viewModel.next()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        when {
                            isMultiUnsubmitted -> "确认答案"
                            else -> "下一题"
                        }
                    )
                }
            } else if (state.isFinished) {
                FinishedView(onBack = onBack)
            }
        }
    }
}

@Composable
private fun QuestionBody(
    question: Question,
    selected: Set<Int>,
    submitted: Boolean,
    isCorrect: Boolean?,
    correctAnswer: List<String>,
    onToggle: (Int) -> Unit,
    userAnswerText: String = "",
    onUserTextChange: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 题干
        Text(
            text = "【${question.type.name}】${question.title}",
            style = MaterialTheme.typography.titleMedium
        )
        // 填空题：显示文本输入框
        if (question.type == QuestionType.BLANK && question.options.isEmpty()) {
            OutlinedTextField(
                value = userAnswerText,
                onValueChange = onUserTextChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitted,
                placeholder = { Text("请输入答案") },
                singleLine = true
            )
        } else {
            // 选项题（普通选项 或 JUDGE 固定选项）
            val displayOptions = if (question.type == QuestionType.JUDGE) {
                listOf("正确", "错误")
            } else {
                question.options
            }
            displayOptions.forEachIndexed { index, optionText ->
                OptionCard(
                    index = index,
                    text = optionText,
                    isSelected = index in selected,
                    isCorrectAnswer = submitted && correctAnswer.contains(index.toString()),
                    isWrongPick = submitted && index in selected && !correctAnswer.contains(index.toString()),
                    onClick = { onToggle(index) }
                )
            }
        }

        // 提交后展示答案 / 解析
        if (submitted) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isCorrect == true)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isCorrect == true) "回答正确" else "回答错误",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "正确答案：${
                            if (question.type == QuestionType.BLANK) correctAnswer.joinToString()
                            else correctAnswer.joinToString { qIndexToLetter(it.toIntOrNull() ?: 0) }
                        }",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!question.analysis.isNullOrBlank()) {
                        Text(
                            text = "解析：${question.analysis}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionCard(
    index: Int,
    text: String,
    isSelected: Boolean,
    isCorrectAnswer: Boolean,
    isWrongPick: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isCorrectAnswer -> MaterialTheme.colorScheme.primary
        isWrongPick -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val bg = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Text(
            text = "${qIndexToLetter(index)}.  $text",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun FinishedView(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "已完成本次刷题 🎉",
                style = MaterialTheme.typography.titleLarge
            )
            Button(
                onClick = onBack,
                modifier = Modifier.padding(top = 16.dp)
            ) { Text("返回题库列表") }
        }
    }
}

@Composable
private fun QuestionTypeBar(
    questions: List<Question>,
    currentIndex: Int,
    onDotClick: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(questions.size) { index ->
            val baseColor = when (questions[index].type) {
                QuestionType.SINGLE -> Color(0xFF2196F3)
                QuestionType.MULTI -> Color(0xFF4CAF50)
                QuestionType.JUDGE -> Color(0xFFFF9800)
                QuestionType.DEBUG -> Color(0xFF9C27B0)
                QuestionType.BLANK -> Color(0xFF00BCD4)
            }
            val isCurrent = index == currentIndex
            val dotModifier = if (isCurrent) {
                Modifier
                    .size(14.dp)
                    .background(Color.White, CircleShape)
                    .padding(2.dp)
                    .background(baseColor, CircleShape)
            } else {
                Modifier
                    .size(10.dp)
                    .background(baseColor, CircleShape)
            }
            Box(
                modifier = dotModifier.clickable { onDotClick(index) }
            )
        }
    }
}

private fun qIndexToLetter(index: Int): String = ('A' + index).toString()

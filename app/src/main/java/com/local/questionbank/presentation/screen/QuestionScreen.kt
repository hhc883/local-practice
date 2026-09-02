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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.local.questionbank.di.AppContainer
import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.model.QuestionType
import com.local.questionbank.presentation.screen.component.QuestionBody
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
                    // AI 出题按钮(全局可见)
                    IconButton(
                        onClick = { viewModel.requestAiSimilar() },
                        enabled = state.current != null && !state.aiGenerating
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI 出题",
                            tint = if (state.aiGenerating) MaterialTheme.colorScheme.onSurfaceVariant
                                   else MaterialTheme.colorScheme.tertiary
                        )
                    }
                    // 错题本模式下显示"移除"按钮
                    if (modeName == "WRONG_ONLY") {
                        IconButton(
                            onClick = { viewModel.removeFromWrongBook() },
                            enabled = state.current != null
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "从错题本移除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
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
                val isMultiUnsubmitted = !state.isCurrentSubmitted && (
                    state.current?.type == QuestionType.MULTI ||
                    state.current?.type == QuestionType.BLANK ||
                    state.current?.type == QuestionType.PROG
                )
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

    // ----- AI 出题底部 sheet -----
    val aiSheetState = rememberModalBottomSheetState()
    var aiSheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.aiGenerating, state.aiGeneratedQuestion) {
        aiSheetVisible = state.aiGenerating || state.aiGeneratedQuestion != null
    }

    val snackbarForAi = remember { SnackbarHostState() }
    LaunchedEffect(state.aiSavedToast) {
        state.aiSavedToast?.let {
            snackbarForAi.showSnackbar(it)
            viewModel.consumeAiSavedToast()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarForAi.showSnackbar(it)
            // 这里不调 viewModel.consumeError(),因为原 errorMessage 是为其他错误准备的
            // 简化处理:用户在 sheet 里能看见错误
        }
    }

    if (aiSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!state.aiGenerating) {
                    viewModel.dismissAiGenerated()
                    aiSheetVisible = false
                }
            },
            sheetState = aiSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "AI 生成新题",
                    style = MaterialTheme.typography.titleMedium
                )
                if (state.aiGenerating) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
                        Text("正在生成,请稍候...")
                    }
                } else {
                    val q = state.aiGeneratedQuestion
                    if (q != null) {
                        // 复用 QuestionBody 显示题面 + 用户作答
                        // 高度限制避免 sheet 过高无法滚动
                        Box(modifier = Modifier.heightIn(max = 480.dp)) {
                            QuestionBody(
                                question = q,
                                selected = state.aiSelected,
                                submitted = state.aiSubmitted,
                                isCorrect = state.aiIsCorrect,
                                correctAnswer = q.answer,
                                onToggle = { viewModel.toggleAiOption(it) },
                                userAnswerText = state.aiTextAnswer,
                                onUserTextChange = { viewModel.updateAiTextAnswer(it) },
                                showQuestionTypeLabel = false
                            )
                        }
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!state.aiSubmitted) {
                                Button(
                                    onClick = { viewModel.submitAiAnswer() },
                                    modifier = Modifier.weight(1f),
                                    enabled = state.aiSelected.isNotEmpty() || state.aiTextAnswer.isNotBlank()
                                ) { Text("提交答案") }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.resetAiAnswer() },
                                    modifier = Modifier.weight(1f)
                                ) { Text("重做") }
                            }
                            OutlinedButton(
                                onClick = { viewModel.requestAiSimilar() },
                                modifier = Modifier.weight(1f)
                            ) { Text("再出一道") }
                        }
                        if (state.aiSubmitted) {
                            Button(
                                onClick = { viewModel.saveAiGenerated() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("加入当前题库") }
                        }
                        androidx.compose.material3.TextButton(
                            onClick = {
                                viewModel.dismissAiGenerated()
                                aiSheetVisible = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }

    // 未配置 API Key 提示对话框
    if (state.aiNeedsApiKey) {
        AlertDialog(
            onDismissRequest = { viewModel.consumeAiNeedsApiKey() },
            title = { Text("需要配置 API Key") },
            text = { Text("AI 出题功能需要先在 AI 助手设置中填写智谱 API Key。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.consumeAiNeedsApiKey() }) { Text("知道了") }
            }
        )
    }

    SnackbarHost(snackbarForAi)
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
                QuestionType.PROG -> Color(0xFFE91E63)
                QuestionType.UNKNOWN -> Color(0xFF9E9E9E)   // 灰色
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


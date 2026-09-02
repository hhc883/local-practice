package com.local.questionbank.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.questionbank.di.AppContainer
import com.local.questionbank.domain.model.QuestionType
import com.local.questionbank.presentation.screen.component.QuestionBody
import com.local.questionbank.presentation.viewmodel.AppViewModelFactory
import com.local.questionbank.presentation.viewmodel.BankAiQuizItem
import com.local.questionbank.presentation.viewmodel.BankAiQuizViewModel

/**
 * 题库批量 AI 出题页
 *
 * 流程:
 *  1. 启动 → 后台串行调 AI 生成新题
 *  2. 进度: "AI 正在生成第 N/M 题..."
 *  3. 每题生成完即可作答
 *  4. 全部生成完 → 显示"加入题库 / 丢弃"按钮
 *  5. 中途按返回 → 弹"未完成将丢失"确认
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankAiQuizScreen(
    container: AppContainer,
    bankId: Long,
    bankName: String,
    onBack: () -> Unit
) {
    val viewModel: BankAiQuizViewModel = viewModel(
        factory = AppViewModelFactory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 进入页面立刻启动
    LaunchedEffect(bankId) {
        if (state.generatingIndex < 0 && !state.isFinished) {
            viewModel.start(bankId, bankName)
        }
    }

    // 错误提示
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }
    LaunchedEffect(state.generationError) {
        state.generationError?.let {
            // 单题生成失败,Snackbar 提示用户(不打断整体流程)
            snackbarHostState.showSnackbar(it)
        }
    }

    // 按返回:未完成时弹确认
    var showQuitDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = !state.isFinished) {
        showQuitDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 出题 · ${state.bankName}") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isFinished) onBack() else showQuitDialog = true
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 顶部进度
            if (state.sourceCount > 0) {
                LinearProgressIndicator(
                    progress = {
                        val done = state.items.size
                        val total = state.sourceCount.coerceAtLeast(1)
                        done.toFloat() / total
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "已生成 ${state.items.size} / ${state.sourceCount} 题",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            when {
                // 全部完成:展示汇总
                state.isFinished -> FinishedSummary(
                    state = state,
                    onSave = { viewModel.saveAllToBank() },
                    onDiscard = { onBack() }
                )
                // 题库为空
                state.sourceCount == 0 && state.generatingIndex < 0 -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.errorMessage ?: "暂无内容",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                // 正常展示列表
                else -> ItemsList(
                    items = state.items,
                    isGenerating = state.generatingIndex in state.items.indices,
                    onToggle = viewModel::toggleOption,
                    onTextChange = viewModel::updateTextAnswer,
                    onSubmit = viewModel::submitAnswer,
                    onReset = viewModel::resetAnswer
                )
            }
        }
    }

    // 退出确认对话框
    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title = { Text("确定退出?") },
            text = { Text("已生成的题和作答不会被保存,直接退出。") },
            confirmButton = {
                TextButton(onClick = {
                    showQuitDialog = false
                    viewModel.discardAll()
                    onBack()
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showQuitDialog = false }) { Text("继续") }
            }
        )
    }

    // API Key 缺失
    if (state.needsApiKey) {
        AlertDialog(
            onDismissRequest = { viewModel.consumeNeedsApiKey() },
            title = { Text("需要配置 API Key") },
            text = { Text("AI 出题功能需要先在 AI 助手设置中填写智谱 API Key。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.consumeNeedsApiKey()
                    onBack()
                }) { Text("知道了") }
            }
        )
    }
}

/**
 * 题目列表(可作答)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemsList(
    items: List<BankAiQuizItem>,
    isGenerating: Boolean,
    onToggle: (Int, Int) -> Unit,
    onTextChange: (Int, String) -> Unit,
    onSubmit: (Int) -> Unit,
    onReset: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 用 idx 作 key(而非 question.id):AI 返回的 Question 默认 id=0,所有题会撞 key
        itemsIndexed(items, key = { idx, _ -> "ai-quiz-$idx" }) { idx, item ->
            AiQuizCard(
                index = idx,
                item = item,
                onToggle = { onToggle(idx, it) },
                onTextChange = { onTextChange(idx, it) },
                onSubmit = { onSubmit(idx) },
                onReset = { onReset(idx) }
            )
        }
        if (isGenerating) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 4.dp))
                    Text("AI 正在生成下一题...")
                }
            }
        }
    }
}

/**
 * 单题卡片:题面 + 作答 + 提交/重做
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiQuizCard(
    index: Int,
    item: BankAiQuizItem,
    onToggle: (Int) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onReset: () -> Unit
) {
    val q = item.question
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                item.submitted && item.isCorrect == true -> MaterialTheme.colorScheme.primaryContainer
                item.submitted && item.isCorrect == false -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 题号
            Text(
                text = "AI 题目 #${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 复用 QuestionBody 渲染题面 + 用户作答 + 提交后答案
            Box(modifier = Modifier.padding(top = 4.dp)) {
                QuestionBody(
                    question = q,
                    selected = item.selected,
                    submitted = item.submitted,
                    isCorrect = item.isCorrect,
                    correctAnswer = q.answer,
                    onToggle = onToggle,
                    userAnswerText = item.textAnswer,
                    onUserTextChange = onTextChange,
                    showQuestionTypeLabel = false,
                    scrollable = false   // LazyColumn item 内禁用内滚动,避免 infinity height 崩溃
                )
            }
            // 操作按钮
            if (!item.submitted) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSubmit,
                        modifier = Modifier.weight(1f),
                        enabled = item.selected.isNotEmpty() || item.textAnswer.isNotBlank()
                    ) { Text("提交") }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f)
                    ) { Text("重做") }
                }
            }
        }
    }
}

/**
 * 全部完成:展示汇总 + "加入题库 / 丢弃"
 */
@Composable
private fun FinishedSummary(
    state: com.local.questionbank.presentation.viewmodel.BankAiQuizUiState,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "AI 出题完成",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "题库:${state.bankName}",
            style = MaterialTheme.typography.titleMedium
        )
        Text("成功生成:${state.items.size} / ${state.sourceCount} 题")
        Text("作答正确:${state.correctCount} / ${state.items.size}")
        if (state.savedCount > 0) {
            Text(
                text = "已入库 ${state.savedCount} 题",
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (state.isSaving) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                Text("正在入库...")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f)
                ) { Text("丢弃") }
                Button(
                    onClick = onSave,
                    enabled = state.items.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("加入题库") }
            }
        }
    }
}
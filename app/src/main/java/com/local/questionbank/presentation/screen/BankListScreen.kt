package com.local.questionbank.presentation.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.questionbank.di.AppContainer
import com.local.questionbank.domain.model.QuestionBank
import com.local.questionbank.presentation.viewmodel.AppViewModelFactory
import com.local.questionbank.presentation.viewmodel.BankListViewModel
import kotlin.math.roundToInt

/**
 * 题库列表页
 *
 * - 列表项点击 → 进入刷题页（顺序模式）
 * - 列表项右侧删除按钮 → 二次确认后删除
 * - **列表项长按 → 拖拽到目标位置后释放，自动持久化新顺序**
 * - 右下角 FAB → 跳转到导入页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankListScreen(
    container: AppContainer,
    onAddBank: () -> Unit,
    onStartPractice: (bankId: Long, mode: String) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenWrongBook: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenBankAiQuiz: (Long, String) -> Unit
) {
    val viewModel: BankListViewModel = viewModel(
        factory = AppViewModelFactory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 监听 pendingUndo:有则展示 Snackbar,5 秒后自动关闭
    LaunchedEffect(pendingUndo) {
        val pending = pendingUndo ?: return@LaunchedEffect
        // 立刻显示,显示时长 5 秒,提供"撤销"动作
        val result = snackbarHostState.showSnackbar(
            message = "已删除\"${pending.bankName}\"",
            actionLabel = "撤销",
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undoDelete()
            SnackbarResult.Dismissed -> viewModel.consumePendingUndo()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(text = "我的题库") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddBank) {
                Icon(Icons.Default.Add, contentDescription = "导入题库")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            // 顶部快捷入口
            GlobalEntryRow(
                onOpenFavorites = onOpenFavorites,
                onOpenWrongBook = onOpenWrongBook,
                onOpenAiAssistant = onOpenAiAssistant
            )
            if (state.banks.isEmpty() && !state.isLoading) {
                EmptyBanksHint(modifier = Modifier.fillMaxSize())
            } else {
                ReorderableBankList(
                    banks = state.banks,
                    listState = listState,
                    onClick = { bank -> onStartPractice(bank.id, "ORDERED") },
                    onFavoriteOnly = { bank -> onStartPractice(bank.id, "FAVORITE_ONLY") },
                    onDelete = { bank -> viewModel.deleteBank(bank.id) },
                    onAiQuiz = { bank -> onOpenBankAiQuiz(bank.id, bank.name) },
                    onMove = { from, to -> viewModel.moveBank(from, to) }
                )
            }
            state.error?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

/**
 * 可拖拽重排的题库列表
 *
 * 实现要点：
 * - 整列包一个 [pointerInput] 处理 longPress → drag 流程
 * - 维护 [draggingId] / [dragOffsetPx] / [targetIndex] 三个状态
 * - 被拖卡片渲染在原位（参与 layout），用 [graphicsLayer] translationZ 抬高 + offset
 * - 其他卡片根据是否处于 "让位区间" 用 [animateItemPlacement] 平滑过渡
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ReorderableBankList(
    banks: List<QuestionBank>,
    listState: LazyListState,
    onClick: (QuestionBank) -> Unit,
    onFavoriteOnly: (QuestionBank) -> Unit,
    onDelete: (QuestionBank) -> Unit,
    onAiQuiz: (QuestionBank) -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit
) {
    val density = LocalDensity.current
    val cardSpacingPx = with(density) { 12.dp.toPx() }

    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var targetIndex by remember { mutableStateOf(-1) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(banks) {
                // 长按某卡片后启动拖拽；移动中持续更新 dragOffset；释放时提交 onMove
                if (banks.isEmpty()) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        // 找到触摸点命中的卡片 index
                        val firstVisible = listState.firstVisibleItemIndex
                        val firstOffset = listState.firstVisibleItemScrollOffset
                        // 估算：每行高度 = itemHeight + cardSpacing（itemHeight 在 ListItem 内统一 100dp）
                        val rowHeight = with(density) { 88.dp.toPx() } + cardSpacingPx
                        val approx = ((offset.y - firstOffset) / rowHeight).toInt()
                        val hitIndex = (firstVisible + approx).coerceIn(0, banks.lastIndex)
                        if (hitIndex in banks.indices) {
                            draggingId = banks[hitIndex].id
                            dragStartIndex = hitIndex
                            targetIndex = hitIndex
                            dragOffsetPx = 0f
                        }
                    },
                    onDragEnd = {
                        val from = dragStartIndex
                        val to = targetIndex
                        if (from in banks.indices && to in banks.indices && from != to) {
                            onMove(from, to)
                        }
                        draggingId = null
                        dragOffsetPx = 0f
                        dragStartIndex = -1
                        targetIndex = -1
                    },
                    onDragCancel = {
                        draggingId = null
                        dragOffsetPx = 0f
                        dragStartIndex = -1
                        targetIndex = -1
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetPx += dragAmount.y
                        // 估算目标 index：累加位移 / 行高
                        val rowHeight = with(density) { 88.dp.toPx() } + cardSpacingPx
                        val delta = (dragOffsetPx / rowHeight).toInt()
                        val newTarget = (dragStartIndex + delta).coerceIn(0, banks.lastIndex)
                        if (newTarget != targetIndex) targetIndex = newTarget
                    }
                )
            },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(items = banks, key = { _, b -> b.id }) { index, bank ->
            val isDragging = bank.id == draggingId
            val from = dragStartIndex
            val to = targetIndex
            // 让位动画：被拖卡片向下时,中间所有卡片上移一格
            val shouldShift = !isDragging &&
                from in banks.indices && to in banks.indices &&
                ((to > from && index in (from + 1)..to) ||
                 (to < from && index in to until from))
            val shiftDp = if (shouldShift) {
                with(density) { 88.dp } + 12.dp
            } else 0.dp
            val shiftAnim by animateFloatAsState(
                targetValue = with(density) { shiftDp.toPx() },
                label = "shift"
            )

            BankCard(
                bank = bank,
                isDragging = isDragging,
                onClick = { if (!isDragging) onClick(bank) },
                onFavoriteOnly = { onFavoriteOnly(bank) },
                onDelete = { onDelete(bank) },
                onAiQuiz = { onAiQuiz(bank) },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = dragOffsetPx
                            shadowElevation = 24f
                            scaleX = 1.03f
                            scaleY = 1.03f
                        } else {
                            translationY = 0f
                        }
                    }
                    .offset { IntOffset(0, -shiftAnim.roundToInt()) }
                    .then(
                        if (isDragging) Modifier.shadow(16.dp) else Modifier
                    )
            )
        }
    }
}

@Composable
private fun GlobalEntryRow(
    onOpenFavorites: () -> Unit,
    onOpenWrongBook: () -> Unit,
    onOpenAiAssistant: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenFavorites),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "  收藏夹",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenWrongBook),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "  错题本",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenAiAssistant),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "  AI 助手",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BankCard(
    bank: QuestionBank,
    isDragging: Boolean,
    onClick: () -> Unit,
    onFavoriteOnly: () -> Unit,
    onDelete: () -> Unit,
    onAiQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(enabled = !isDragging, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = bank.name, style = MaterialTheme.typography.titleMedium)
                if (!bank.description.isNullOrBlank()) {
                    Text(
                        text = bank.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 拖拽手柄：作为长按拖拽的视觉提示
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "长按拖拽排序",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onClick) {
                Icon(Icons.Default.PlayArrow, contentDescription = "开始刷题")
            }
            IconButton(onClick = onFavoriteOnly) {
                Icon(Icons.Default.Star, contentDescription = "只刷收藏")
            }
            IconButton(onClick = onAiQuiz) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "AI 出题",
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除题库")
            }
        }
    }
}

@Composable
private fun EmptyBanksHint(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "暂无题库，点击右下角 + 导入 JSON",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

package com.local.questionbank.presentation.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.local.questionbank.domain.model.FavoriteGroup
import com.local.questionbank.domain.model.Question
import com.local.questionbank.presentation.viewmodel.FavoriteListUiState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 收藏夹页
 *
 * 列表项类型：
 *  - GroupHeader (groupKey = "g-${bank.id}")
 *  - QuestionItem (itemKey = "q-${favoriteDbId}")
 *
 * 长按拖拽：
 *  - GroupHeader 长按可重排分组（复用首页题库顺序）
 *  - QuestionItem 长按可重排该题库下的收藏顺序
 *
 * 删除题目：
 *  - 题目项右侧"×"按钮：点击直接删除 + Snackbar 撤销
 *  - 题目项左滑删除（保留原有交互）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteListScreen(
    uiState: FavoriteListUiState,
    onNavigateBack: () -> Unit,
    onDeleteFavorite: (Long) -> Unit,
    onUndoDelete: (Long) -> Unit,
    onQuestionClick: (Question) -> Unit,
    onMoveGroup: (fromIndex: Int, toIndex: Int) -> Unit,
    onMoveQuestion: (bankId: Long, fromIndex: Int, toIndex: Int) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 展开/折叠状态：按 bankId 记忆,默认全部展开
    val expandedBanks = remember { mutableStateOf<Set<Long>>(emptySet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收藏夹") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            uiState.groups.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "还没有收藏题目",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            else -> {
                ReorderableFavoriteList(
                    uiState = uiState,
                    expandedBanks = expandedBanks.value,
                    onToggleExpanded = { bankId ->
                        expandedBanks.value =
                            if (bankId in expandedBanks.value) expandedBanks.value - bankId
                            else expandedBanks.value + bankId
                    },
                    listState = rememberLazyListState(),
                    onDeleteFavorite = onDeleteFavorite,
                    onUndoDelete = onUndoDelete,
                    onQuestionClick = onQuestionClick,
                    onMoveGroup = onMoveGroup,
                    onMoveQuestion = onMoveQuestion,
                    snackbarHostState = snackbarHostState,
                    scope = scope,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            }
        }
    }
}

/**
 * 收藏夹列表的扁平可拖拽结构
 *
 * 把分组标题与题目项铺平到同一个 LazyColumn(每项一个唯一 key),
 * 拖拽时根据被拖项的类型走不同的 onMove 回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReorderableFavoriteList(
    uiState: FavoriteListUiState,
    expandedBanks: Set<Long>,
    onToggleExpanded: (Long) -> Unit,
    listState: LazyListState,
    onDeleteFavorite: (Long) -> Unit,
    onUndoDelete: (Long) -> Unit,
    onQuestionClick: (Question) -> Unit,
    onMoveGroup: (fromIndex: Int, toIndex: Int) -> Unit,
    onMoveQuestion: (bankId: Long, fromIndex: Int, toIndex: Int) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier
) {
    // 把 (groupIndex, itemType, payload) 拍平成单层 items
    val rows = remember(uiState.groups, expandedBanks) {
        buildList {
            uiState.groups.forEachIndexed { gIdx, group ->
                add(Row.GroupHeader(gIdx, group))
                if (group.bank.id in expandedBanks) {
                    group.favorites.forEachIndexed { qIdx, q ->
                        add(Row.QuestionItem(gIdx, group.bank.id, qIdx, q, group.favoriteDbIds[qIdx]))
                    }
                }
            }
        }
    }

    val density = LocalDensity.current
    val rowSpacingPx = with(density) { 8.dp.toPx() }
    val headerHeightPx = with(density) { 56.dp.toPx() } + rowSpacingPx
    val questionHeightPx = with(density) { 80.dp.toPx() } + rowSpacingPx

    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var targetIndex by remember { mutableStateOf(-1) }

    LazyColumn(
        state = listState,
        modifier = modifier
            .pointerInput(rows) {
                if (rows.isEmpty()) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val firstVisible = listState.firstVisibleItemIndex
                        val firstOffset = listState.firstVisibleItemScrollOffset
                        // 估算每行高度(混合:header ~56dp,q ~80dp),用 header 估算作为下限
                        val approxHeight = headerHeightPx
                        val approx = ((offset.y - firstOffset) / approxHeight).toInt()
                        val hit = (firstVisible + approx).coerceIn(0, rows.lastIndex)
                        draggingKey = rows[hit].key
                        dragStartIndex = hit
                        targetIndex = hit
                        dragOffsetPx = 0f
                    },
                    onDragEnd = {
                        val start = dragStartIndex
                        val to = targetIndex
                        if (start in rows.indices && to in rows.indices && start != to) {
                            val startRow = rows[start]
                            val targetRow = rows[to]
                            // 同类型之间才允许拖动
                            if (startRow::class == targetRow::class) {
                                when (startRow) {
                                    is Row.GroupHeader -> {
                                        onMoveGroup(startRow.groupIndex, (targetRow as Row.GroupHeader).groupIndex)
                                    }
                                    is Row.QuestionItem -> {
                                        onMoveQuestion(
                                            startRow.bankId,
                                            startRow.questionIndex,
                                            (targetRow as Row.QuestionItem).questionIndex
                                        )
                                    }
                                }
                            }
                        }
                        draggingKey = null
                        dragOffsetPx = 0f
                        dragStartIndex = -1
                        targetIndex = -1
                    },
                    onDragCancel = {
                        draggingKey = null
                        dragOffsetPx = 0f
                        dragStartIndex = -1
                        targetIndex = -1
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetPx += dragAmount.y
                        // 自适应行高:按被拖项类型决定
                        val current = rows.getOrNull(dragStartIndex)
                        val approxHeight = when (current) {
                            is Row.GroupHeader -> headerHeightPx
                            is Row.QuestionItem -> questionHeightPx
                            null -> headerHeightPx
                        }
                        val delta = (dragOffsetPx / approxHeight).toInt()
                        val newTarget = (dragStartIndex + delta).coerceIn(0, rows.lastIndex)
                        if (newTarget != targetIndex) targetIndex = newTarget
                    }
                )
            },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items = rows, key = { _, r -> r.key }) { index, row ->
            val isDragging = row.key == draggingKey
            val from = dragStartIndex
            val to = targetIndex
            val startRow = rows.getOrNull(from)
            val targetRow = rows.getOrNull(to)
            val shouldShift = !isDragging &&
                startRow != null && targetRow != null &&
                startRow::class == targetRow::class &&
                ((to > from && index in (from + 1)..to) ||
                 (to < from && index in to until from))
            val shiftHeightPx = if (shouldShift) {
                when (startRow) {
                    is Row.GroupHeader -> headerHeightPx
                    is Row.QuestionItem -> questionHeightPx
                    null -> 0f
                }
            } else 0f
            val shiftAnim by animateFloatAsState(
                targetValue = shiftHeightPx,
                label = "rowShift"
            )

            when (row) {
                is Row.GroupHeader -> {
                    GroupHeaderCard(
                        group = row.group,
                        isExpanded = row.group.bank.id in expandedBanks,
                        isDragging = isDragging,
                        onToggle = { onToggleExpanded(row.group.bank.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragOffsetPx
                                    shadowElevation = 24f
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                } else {
                                    translationY = 0f
                                }
                            }
                            .offset { IntOffset(0, -shiftAnim.roundToInt()) }
                            .then(if (isDragging) Modifier.shadow(12.dp) else Modifier)
                    )
                }
                is Row.QuestionItem -> {
                    QuestionItem(
                        question = row.question,
                        isDragging = isDragging,
                        onClick = { if (!isDragging) onQuestionClick(row.question) },
                        onDelete = {
                            onDeleteFavorite(row.question.id)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "已删除收藏",
                                    actionLabel = "撤销",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    onUndoDelete(row.question.id)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragOffsetPx
                                    shadowElevation = 24f
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                } else {
                                    translationY = 0f
                                }
                            }
                            .offset { IntOffset(0, -shiftAnim.roundToInt()) }
                            .then(if (isDragging) Modifier.shadow(12.dp) else Modifier)
                    )
                }
            }
        }
    }
}

/** 列表的扁平行类型 */
private sealed class Row {
    abstract val key: String

    data class GroupHeader(
        val groupIndex: Int,
        val group: FavoriteGroup
    ) : Row() {
        override val key: String = "g-${group.bank.id}"
    }

    data class QuestionItem(
        val groupIndex: Int,
        val bankId: Long,
        val questionIndex: Int,
        val question: Question,
        val favoriteDbId: Long
    ) : Row() {
        override val key: String = "q-$favoriteDbId"
    }
}

@Composable
private fun GroupHeaderCard(
    group: FavoriteGroup,
    isExpanded: Boolean,
    isDragging: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable(enabled = !isDragging, onClick = onToggle)
            .background(
                if (isDragging) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group.bank.name,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "  ${group.favorites.size} 题",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "折叠" else "展开",
            modifier = Modifier.padding(start = 4.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // 长按提示图标
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "长按拖拽分组",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionItem(
    question: Question,
    isDragging: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFFE53935)
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.White
                    )
                }
            }
        },
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isDragging, onClick = onClick)
                .background(
                    if (isDragging) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "长按拖拽",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = question.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp)
            )
            // 右侧删除按钮
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除收藏",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

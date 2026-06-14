# 刷题页面交互重构设计

**日期：** 2026-06-14
**状态：** 已批准

---

## 1. 概述

重构刷题页面的交互逻辑和 UI 结构：
1. **单选题 / 判断题**：点击选项 → 立即自动判题显示解析 → 点"下一题"翻页
2. **多选题**：选完选项 → 点"下一题"判题显示解析 → 再点"下一题"才翻页（两步）
3. **横向滑动**：整页切换题目，可滑动翻题（多选题判题前滑动不保存答案）
4. **顶部类型标签栏**：彩色圆点表示题库中每题类型，点击可跳转

---

## 2. QuestionUiState 改造

**文件：** `presentation/viewmodel/QuestionUiState.kt`（内嵌于 QuestionViewModel）

**改动点：**
- `submitted` 保持不变，表示当前题是否已判题
- 新增 `canNavigateNext` 计算属性：多选题在判题前不允许翻页

```kotlin
data class QuestionUiState(
    val isLoading: Boolean = true,
    val questions: List<Question> = emptyList(),
    val currentIndex: Int = 0,
    val selected: Set<Int> = emptySet(),
    val submitted: Boolean = false,
    val isCorrect: Boolean? = null,
    val correctAnswer: List<Int> = emptyList(),
    val isFinished: Boolean = false,
    val isFavorited: Boolean = false,
    val errorMessage: String? = null
) {
    val current: Question? get() = questions.getOrNull(currentIndex)
    val progress: Float
        get() = if (questions.isEmpty()) 0f else (currentIndex + 1f) / questions.size

    // 多选题判题前禁止翻页
    val canNavigateNext: Boolean
        get() = current?.type != QuestionType.MULTI || submitted
}
```

---

## 3. QuestionViewModel 逻辑改造

**文件：** `presentation/viewmodel/QuestionViewModel.kt`

### 3.1 toggleOption 行为变更

```kotlin
fun toggleOption(index: Int) {
    val state = _uiState.value
    val current = state.current ?: return
    if (state.submitted) return  // 判题后不能再改答案

    val newSelected = if (current.type == QuestionType.SINGLE || current.type == QuestionType.JUDGE) {
        // 单选/判断：点选即自动判题
        setOf(index)
    } else {
        // 多选：仅更新选项，不触发判题
        if (index in state.selected) state.selected - index else state.selected + index
    }
    _uiState.update { it.copy(selected = newSelected) }

    // 单选/判断：选中立即提交判题
    if (current.type == QuestionType.SINGLE || current.type == QuestionType.JUDGE) {
        submit()
    }
}
```

### 3.2 submit 行为不变，但新增 submitIfAllowed（多选用）

```kotlin
fun submitIfAllowed() {
    // 仅多选题在未判题时调用 submit()
    val state = _uiState.value
    val current = state.current ?: return
    if (current.type == QuestionType.MULTI && !state.submitted) {
        submit()
    }
}
```

### 3.3 next() 行为变更

```kotlin
fun next() {
    val state = _uiState.value
    val current = state.current ?: return

    if (current.type == QuestionType.MULTI && !state.submitted) {
        // 多选题第一步：判题显示解析，不翻页
        submit()
    } else {
        // 已判题或非多选：真正翻页
        doNavigateNext()
    }
}

private fun doNavigateNext() {
    val state = _uiState.value
    val nextIndex = state.currentIndex + 1
    if (nextIndex >= state.questions.size) {
        _uiState.update { it.copy(isFinished = true) }
    } else {
        _uiState.update {
            it.copy(
                currentIndex = nextIndex,
                selected = emptySet(),
                submitted = false,
                isCorrect = null,
                correctAnswer = emptyList()
            )
        }
        observeCurrentFavorite()
    }
}
```

### 3.4 滑动换题时丢弃未判题答案

```kotlin
fun onPageChanged(pageIndex: Int) {
    val prev = _uiState.value.current
    // 如果前一个是多选题且未判题，丢弃已选
    if (prev != null && prev.type == QuestionType.MULTI && !_uiState.value.submitted) {
        _uiState.update { it.copy(selected = emptySet()) }
    }
    // 翻到新题
    _uiState.update { it.copy(currentIndex = pageIndex) }
    observeCurrentFavorite()
}
```

---

## 4. QuestionScreen UI 改造

**文件：** `presentation/screen/QuestionScreen.kt`

### 4.1 新增 import

```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ExpandLess   // 复用
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.local.questionbank.domain.model.QuestionType
```

### 4.2 布局结构

```
Column(fillMaxSize)
├─ TopAppBar（标题 + 星标）
├─ LazyRow（顶部类型标签栏）
├─ LinearProgressIndicator
├─ HorizontalPager（题目主体）
│   └─ QuestionBody（题干+选项+解析卡片）
└─ Row（底部"下一题"按钮）
```

### 4.3 顶部类型标签栏

```kotlin
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
            val color = when (questions[index].type) {
                QuestionType.SINGLE -> Color(0xFF2196F3)     // 蓝色
                QuestionType.MULTI -> Color(0xFF4CAF50) // 绿色
                QuestionType.JUDGE -> Color(0xFFFF9800)  // 橙色
            }
            Box(
                modifier = Modifier
                    .size(if (index == currentIndex) 14.dp else 10.dp)
                    .background(
                        color = color,
                        shape = CircleShape
                    )
                    .then(
                        if (index == currentIndex) Modifier.background(Color.White, CircleShape).padding(2.dp)
                        else Modifier
                    )
                    .clickable { onDotClick(index) }
            )
        }
    }
}
```

### 4.4 HorizontalPager

```kotlin
val pagerState = rememberPagerState(
    initialPage = initialPageIndex,
    pageCount = { state.questions.size }
)

LaunchedEffect(pagerState.currentPage) {
    if (pagerState.currentPage != state.currentIndex) {
        viewModel.onPageChanged(pagerState.currentPage)
    }
}

HorizontalPager(
    state = pagerState,
    modifier = Modifier.fillMaxSize()
) { pageIndex ->
    val question = state.questions.getOrNull(pageIndex) ?: return@HorizontalPager
    // 渲染单题内容，传入 pageIndex 对应的状态
    QuestionBody(
        question = question,
        selected = if (pageIndex == state.currentIndex) state.selected else emptySet(),
        submitted = if (pageIndex == state.currentIndex) state.submitted else false,
        // ...
    )
}
```

### 4.5 底部"下一题"按钮逻辑

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
) {
    val current = state.current
    val isMultiUnsubmitted = current?.type == QuestionType.MULTI && !state.submitted

    Button(
        onClick = {
            if (isMultiUnsubmitted) {
                // 多选第一步：判题
                viewModel.submitIfAllowed()
            } else {
                // 翻到下一题（内部会处理多选第二步）
                viewModel.next()
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            when {
                isMultiUnsubmitted -> "确认答案"
                current?.type == QuestionType.MULTI -> "下一题"
                else -> "下一题"
            }
        )
    }
}
```

---

## 5. 题目类型颜色定义

| 类型 | 颜色 | 说明 |
|---|---|---|
| `SINGLE` | #2196F3 蓝色 | 单选题 |
| `MULTI_SELECT` | #4CAF50 绿色 | 多选题 |
| `TRUE_FALSE` | #FF9800 橙色 | 判断题 |

---

## 6. 实现步骤

1. 修改 `QuestionUiState`（内嵌 data class）— 新增 `canNavigateNext`
2. 修改 `QuestionViewModel.toggleOption()` — 单选/判断点选即判
3. 修改 `QuestionViewModel.submit()` — 保持现有逻辑（写入答题记录）
4. 新增 `QuestionViewModel.submitIfAllowed()`
5. 修改 `QuestionViewModel.next()` — 多选两步翻页逻辑
6. 新增 `QuestionViewModel.onPageChanged()` — 滑动换题丢弃逻辑
7. 修改 `QuestionScreen` — 引入 `HorizontalPager` + `rememberPagerState`
8. 新增 `QuestionTypeBar` Composable — 顶部彩色标签栏
9. 修改底部按钮文字（"确认答案" vs "下一题"）
10. 编译验证

---

## 7. 测试验证点

- [ ] 单选题点选后立即显示对错和解析
- [ ] 判断题点选后立即显示对错和解析
- [ ] 多选题选完后点"确认答案"判题，再点"下一题"才翻页
- [ ] 多选题判题前可以滑动换题（不保存答案）
- [ ] 横向滑动可整页翻题
- [ ] 顶部标签栏彩色圆点点击可跳转
- [ ] 底部进度条正确更新
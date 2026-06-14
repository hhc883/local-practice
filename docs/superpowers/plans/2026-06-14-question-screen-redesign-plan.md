# 刷题页面交互重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构刷题页面交互：点选即判、横向滑动翻题、顶部彩色类型标签栏、多选两步翻页

**Architecture:**
- ViewModel 层改造 `toggleOption` / `submitIfAllowed` / `next` / `onPageChanged` 逻辑
- UI 层将 `Column` + `when` 页面切换替换为 `HorizontalPager` 横向整页滑动
- 顶部新增 `QuestionTypeBar` 显示彩色标签可点击跳转
- 底部按钮文字根据多选是否判题动态变化

**Tech Stack:** Kotlin + Jetpack Compose + HorizontalPager + Material3

---

## 文件变更总览

| 操作 | 文件 |
|---|---|
| 修改 | `presentation/viewmodel/QuestionViewModel.kt` |
| 修改 | `presentation/screen/QuestionScreen.kt` |

---

## Task 1: QuestionUiState 新增 canNavigateNext

**文件：** 修改 `presentation/viewmodel/QuestionViewModel.kt`（`QuestionUiState` data class 内）

在 `progress` 计算属性之后添加：

```kotlin
    // 多选题判题前禁止翻页
    val canNavigateNext: Boolean
        get() = current?.type != QuestionType.MULTI || submitted
```

注意：需要确认文件顶部已有 `import com.local.questionbank.domain.model.QuestionType`，如果没有需要添加。

- [ ] **Step 1: 读取 QuestionViewModel.kt 确认 QuestionType import**
- [ ] **Step 2: 在 QuestionUiState data class 内添加 canNavigateNext**
- [ ] **Step 3: 提交**

---

## Task 2: toggleOption — 单选/判断点选即判

**文件：** 修改 `presentation/viewmodel/QuestionViewModel.kt` 的 `toggleOption` 方法

将现有 `toggleOption` 方法体替换为：

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

- [ ] **Step 1: 替换 toggleOption 方法体**
- [ ] **Step 2: 提交**

---

## Task 3: 新增 submitIfAllowed() 和 doNavigateNext()

**文件：** 修改 `presentation/viewmodel/QuestionViewModel.kt`

在 `submit()` 方法之后、`toggleOption()` 之前添加：

```kotlin
    /**
     * 仅多选题在未判题时触发判题（多选"确认答案"按钮）
     * 单选/判断直接由 toggleOption 调用 submit()，此方法不做任何事
     */
    fun submitIfAllowed() {
        val state = _uiState.value
        val current = state.current ?: return
        if (current.type == QuestionType.MULTI && !state.submitted) {
            submit()
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

- [ ] **Step 1: 添加 submitIfAllowed() 和 doNavigateNext() 方法**
- [ ] **Step 2: 提交**

---

## Task 4: 修改 next() — 多选两步翻页

**文件：** 修改 `presentation/viewmodel/QuestionViewModel.kt` 的 `next()` 方法

将现有 `next()` 方法体替换为：

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
```

- [ ] **Step 1: 替换 next() 方法体**
- [ ] **Step 2: 提交**

---

## Task 5: 新增 onPageChanged() — 滑动换题丢弃逻辑

**文件：** 修改 `presentation/viewmodel/QuestionViewModel.kt`

在 `consumeError()` 方法之后添加：

```kotlin
    /**
     * HorizontalPager 滑动换题时调用
     * 如果前一个是多选题且未判题，丢弃已选答案
     */
    fun onPageChanged(pageIndex: Int) {
        val prev = _uiState.value.current
        if (prev != null && prev.type == QuestionType.MULTI && !_uiState.value.submitted) {
            _uiState.update { it.copy(selected = emptySet()) }
        }
        _uiState.update { it.copy(currentIndex = pageIndex) }
        observeCurrentFavorite()
    }
```

- [ ] **Step 1: 添加 onPageChanged() 方法**
- [ ] **Step 2: 提交**

---

## Task 6: QuestionScreen — 新增 imports 和 QuestionTypeBar

**文件：** 修改 `presentation/screen/QuestionScreen.kt`

**改动 1：新增 imports**

在文件顶部的 imports 区域添加：

```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.local.questionbank.domain.model.QuestionType
```

**注意：** 如果 `import androidx.compose.foundation.layout.size` 已存在则不重复添加。

**改动 2：新增 QuestionTypeBar Composable**

在 `FinishedView` 函数之后、`qIndexToLetter` 之前添加：

```kotlin
@Composable
private fun QuestionTypeBar(
    questions: List<Question>,
    currentIndex: Int,
    onDotClick: (Int) -> Unit
) {
    androidx.compose.foundation.layout.LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(questions.size) { index ->
            val baseColor = when (questions[index].type) {
                QuestionType.SINGLE -> Color(0xFF2196F3)
                QuestionType.MULTI -> Color(0xFF4CAF50)
                QuestionType.JUDGE -> Color(0xFFFF9800)
            }
            val isCurrent = index == currentIndex
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 14.dp else 10.dp)
                    .background(baseColor, CircleShape)
                    .then(
                        if (isCurrent) Modifier
                            .background(Color.White, CircleShape)
                            .padding(2.dp)
                            .background(baseColor, CircleShape)
                        else Modifier
                    )
                    .clickable { onDotClick(index) }
            )
        }
    }
}
```

- [ ] **Step 1: 添加新增 imports**
- [ ] **Step 2: 添加 QuestionTypeBar Composable**
- [ ] **Step 3: 确认 PaddingValues import 存在（从 material3 导入）**
- [ ] **Step 4: 提交**

---

## Task 7: QuestionScreen — 重构为 HorizontalPager

**文件：** 修改 `presentation/screen/QuestionScreen.kt`

### 7.1 新增 PagerState 和相关变量

在 `val state by viewModel.uiState.collectAsStateWithLifecycle()` 之后添加：

```kotlin
    val pagerState = rememberPagerState(
        initialPage = state.currentIndex,
        pageCount = { state.questions.size }
    )
    val scope = rememberCoroutineScope()

    // 同步 pager 滑动 → ViewModel
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != state.currentIndex) {
            viewModel.onPageChanged(pagerState.currentPage)
        }
    }

    // 同步 ViewModel currentIndex → pager（翻页后）
    LaunchedEffect(state.currentIndex) {
        if (pagerState.currentPage != state.currentIndex) {
            pagerState.scrollToPage(state.currentIndex)
        }
    }
```

同时确保文件中已有：
- `import androidx.compose.foundation.pager.rememberPagerState`
- `import androidx.compose.foundation.pager.HorizontalPager`
- `import kotlinx.coroutines.launch`
- `import androidx.compose.runtime.LaunchedEffect`
- `import androidx.compose.runtime.rememberCoroutineScope`

### 7.2 替换 Scaffold 内部的 Column 结构

将原来 `Column { LinearProgressIndicator ... when { ... QuestionBody ... Button } }` 整体替换为：

```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    LinearProgressIndicator(
        progress = { state.progress.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth()
    )

    // 顶部类型标签栏
    if (state.questions.isNotEmpty()) {
        QuestionTypeBar(
            questions = state.questions,
            currentIndex = state.currentIndex,
            onDotClick = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            }
        )
    }

    // 横向滑动主体
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
            selected = if (isCurrentPage) state.selected else emptySet(),
            submitted = if (isCurrentPage) state.submitted else false,
            isCorrect = if (isCurrentPage) state.isCorrect else null,
            correctAnswer = if (isCurrentPage) state.correctAnswer else emptyList(),
            onToggle = if (!state.submitted) viewModel::toggleOption else { _ -> },
            onSubmit = { },
            onNext = { }
        )
    }

    // 底部按钮
    if (!state.isFinished && state.current != null) {
        val isMultiUnsubmitted = state.current?.type == QuestionType.MULTI && !state.submitted
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
```

### 7.3 简化 QuestionBody 调用

由于按钮和判题逻辑都移到了外层，`QuestionBody` 的 `onSubmit` 和 `onNext` 参数：
- 在当前页时：`onSubmit` 传 `{ }`（单选/判断点选即判），`onNext` 传 `{ }`（翻页按钮在外层）
- `onToggle` 正常传入 `viewModel::toggleOption`（但判题后会被空 lambda 替代以防用户修改答案）

- [ ] **Step 1: 添加 PagerState、scope、LaunchedEffect**
- [ ] **Step 2: 替换 Scaffold 内部的 Column 结构**
- [ ] **Step 3: 确认 QuestionBody 的参数传递正确**
- [ ] **Step 4: 提交**

---

## Task 8: 编译验证

**验证点：**
1. `./gradlew assembleDebug` 成功，无编译错误
2. 确认 `QuestionType.MULTI` / `JUDGE` / `SINGLE` 使用正确
3. 确认 `HorizontalPager` 和 `rememberPagerState` import 正确

**命令：**

```bash
cd D:/QuestionBankAndroid && JAVA_HOME=/d/jdk17 ./gradlew assembleDebug 2>&1 | tail -15
```

预期：无错误，BUILD SUCCESSFUL

- [ ] **Step 1: 运行 assembleDebug**
- [ ] **Step 2: 若有编译错误，根据错误信息修复后重新编译**
- [ ] **Step 3: 提交（如有代码变更）**

---

## Task 9: 功能验证测试用例（手动）

- [ ] **Test 1:** 单选题点选后立即显示对错和解析（无需点提交）
- [ ] **Test 2:** 判断题点选后立即显示对错和解析
- [ ] **Test 3:** 多选题选完后点"确认答案"判题，再点"下一题"才翻页
- [ ] **Test 4:** 多选题判题前可以横向滑动换题（不保存答案）
- [ ] **Test 5:** 横向滑动整页翻题流畅
- [ ] **Test 6:** 顶部标签栏彩色圆点点击可跳转
- [ ] **Test 7:** 底部进度条正确更新
- [ ] **Test 8:** 判题后星标收藏按钮状态正确

---

## Spec 覆盖检查

| Spec 需求 | 实现位置 |
|---|---|
| QuestionUiState 新增 `canNavigateNext` | Task 1 |
| toggleOption 单选/判断点选即判 | Task 2 |
| submitIfAllowed() 多选判题入口 | Task 3 |
| next() 多选两步翻页 | Task 4 |
| onPageChanged() 滑动丢弃逻辑 | Task 5 |
| QuestionTypeBar 顶部标签栏 | Task 6 |
| HorizontalPager 横向滑动 | Task 7 |
| 底部按钮文字动态变化 | Task 7 |
| 编译通过 | Task 8 |
| 功能验证 | Task 9 |

所有 spec 需求均有对应任务覆盖，无遗漏。
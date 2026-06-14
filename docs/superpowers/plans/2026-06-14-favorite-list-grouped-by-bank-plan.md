# 收藏列表页（按题库分组）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增一个独立的「收藏列表页」FavoriteListScreen，将用户收藏的题目按所属题库分组展示，支持左滑取消收藏、点击进入刷题。

**Architecture:**
- 数据层：在 `QuestionRepositoryImpl` 中 combine `FavoriteDao.observeAll()`、`QuestionDao.observeAllOrdered()`、`QuestionBankDao.observeAll()` 三个 Flow，在内存中按 `bankId` 分组为 `List<FavoriteGroup>`
- ViewModel 层：新建 `FavoriteListViewModel`，暴露 `FavoriteListUiState` 和删除/撤销方法
- UI 层：新建 `FavoriteListScreen`，用 `SwipeToDismissBox` 实现左滑删除
- 入口改造：`BankListScreen` 顶部收藏夹卡片改为跳 `FAVORITE_LIST` 而非直跳刷题页

**Tech Stack:** Kotlin + Room Flow + Jetpack Compose + 手动 DI（无 Hilt）

---

## 文件变更总览

| 操作 | 文件 |
|---|---|
| 创建 | `domain/model/FavoriteGroup.kt` |
| 创建 | `domain/model/QuestionWithFavoriteMeta.kt` |
| 修改 | `domain/repository/QuestionRepository.kt` |
| 修改 | `data/repository/QuestionRepositoryImpl.kt` |
| 修改 | `di/AppContainer.kt` |
| 创建 | `presentation/viewmodel/FavoriteListViewModel.kt` |
| 修改 | `presentation/viewmodel/AppViewModelFactory.kt` |
| 修改 | `presentation/navigation/Routes.kt` |
| 创建 | `presentation/screen/FavoriteListScreen.kt` |
| 修改 | `presentation/navigation/AppNavGraph.kt` |
| 修改 | `presentation/screen/BankListScreen.kt` |

---

## Task 1: 新增 Domain Model

**文件：** 创建 `domain/model/FavoriteGroup.kt`

```kotlin
package com.local.questionbank.domain.model

/**
 * 按题库分组的收藏视图模型
 *
 * @param bank              题库信息
 * @param favorites         该题库下收藏的题目（按收藏时间倒序）
 * @param latestFavoriteTimestamp 该题库下最近一次收藏的时间戳（用于整组排序）
 */
data class FavoriteGroup(
    val bank: QuestionBank,
    val favorites: List<Question>,
    val latestFavoriteTimestamp: Long
)
```

**文件：** 创建 `domain/model/QuestionWithFavoriteMeta.kt`

```kotlin
package com.local.questionbank.domain.model

/**
 * 收藏题目的元数据封装
 *
 * [Question] 本身不包含收藏时间和标签，这两个字段来自 [com.local.questionbank.data.database.entity.FavoriteEntity]。
 * 本类型用于在 Repository 层将两者绑定后传递给 UI，避免 UI 层直接依赖 Entity。
 *
 * @param question           题目领域模型
 * @param favoriteTimestamp  收藏时间（来自 FavoriteEntity.createTimestamp）
 * @param tag                收藏标签（来自 FavoriteEntity.tag）
 */
data class QuestionWithFavoriteMeta(
    val question: Question,
    val favoriteTimestamp: Long,
    val tag: String
)
```

- [ ] **Step 1: 创建 FavoriteGroup.kt**

```bash
# 验证文件创建
cat app/src/main/java/com/local/questionbank/domain/model/FavoriteGroup.kt
```

- [ ] **Step 2: 创建 QuestionWithFavoriteMeta.kt**

```bash
cat app/src/main/java/com/local/questionbank/domain/model/QuestionWithFavoriteMeta.kt
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/local/questionbank/domain/model/FavoriteGroup.kt app/src/main/java/com/local/questionbank/domain/model/QuestionWithFavoriteMeta.kt
git commit -m "feat: add FavoriteGroup and QuestionWithFavoriteMeta domain models"
```

---

## Task 2: QuestionRepository 新增 observeFavoritesGroupedByBank()

**文件：** 修改 `domain/repository/QuestionRepository.kt:37`

在接口末尾（`observeAllWrongQuestions` 之后）新增方法签名：

```kotlin
/** 按题库分组的收藏题目（每组内按收藏时间倒序，组间按最近收藏时间倒序） */
fun observeFavoritesGroupedByBank(): Flow<List<FavoriteGroup>>
```

- [ ] **Step 1: 添加接口方法**

在 `domain/repository/QuestionRepository.kt` 的 `interface QuestionRepository {}` 末尾添加上述方法声明。

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/local/questionbank/domain/repository/QuestionRepository.kt
git commit -m "feat(QuestionRepository): add observeFavoritesGroupedByBank() signature"
```

---

## Task 3: QuestionRepositoryImpl 实现 observeFavoritesGroupedByBank()

**文件：** 修改 `data/repository/QuestionRepositoryImpl.kt`

**改动点：**
1. 新增构造参数 `questionBankDao: QuestionBankDao`
2. 新增 `import com.local.questionbank.data.database.dao.QuestionBankDao`
3. 新增 `import com.local.questionbank.domain.model.FavoriteGroup`
4. 新增 `import com.local.questionbank.domain.model.QuestionWithFavoriteMeta`
5. 新增 `import kotlinx.coroutines.flow.combine`
6. 新增实现方法

**完整修改后 QuestionRepositoryImpl.kt：**

```kotlin
package com.local.questionbank.data.repository

import com.local.questionbank.data.database.dao.FavoriteDao
import com.local.questionbank.data.database.dao.QuestionBankDao
import com.local.questionbank.data.database.dao.QuestionDao
import com.local.questionbank.data.mapper.EntityMappers.toDomain
import com.local.questionbank.domain.model.FavoriteGroup
import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.model.QuestionWithFavoriteMeta
import com.local.questionbank.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class QuestionRepositoryImpl(
    private val questionDao: QuestionDao,
    private val favoriteDao: FavoriteDao,
    private val questionBankDao: QuestionBankDao   // 新增
) : QuestionRepository {

    // ... 现有方法不变 ...

    override fun observeFavoritesGroupedByBank(): Flow<List<FavoriteGroup>> =
        combine(
            favoriteDao.observeAll(),           // List<FavoriteEntity>
            questionDao.observeAllOrdered(),    // List<QuestionEntity>
            questionBankDao.observeAll()        // List<QuestionBankEntity>  新增
        ) { favorites, questions, banks ->
            val questionMap = questions.associateBy { it.id }
            val bankMap = banks.associateBy { it.id }
            favorites.mapNotNull { fav ->
                questionMap[fav.questionId]?.let { q ->
                    QuestionWithFavoriteMeta(
                        question = q.toDomain(),
                        favoriteTimestamp = fav.createTimestamp,
                        tag = fav.tag
                    )
                }
            }
                .sortedByDescending { it.favoriteTimestamp }
                .groupBy { it.question.bankId }
                .mapNotNull { (bankId, items) ->
                    bankMap[bankId]?.let { bankEntity ->
                        FavoriteGroup(
                            bank = bankEntity.toDomain(),
                            favorites = items.map { it.question },
                            latestFavoriteTimestamp = items.first().favoriteTimestamp
                        )
                    }
                }
                .sortedByDescending { it.latestFavoriteTimestamp }
        }
}
```

**注意：** `bankEntity.toDomain()` 调用 `QuestionBankEntity.toDomain()`，该方法签名在 `EntityMappers.kt:30` 定义为 `fun QuestionBankEntity.toDomain(questionCount: Int = 0): QuestionBank`，可不传 `questionCount` 使用默认值 0。

- [ ] **Step 1: 修改 QuestionRepositoryImpl.kt**
- [ ] **Step 2: 验证编译**（在 Task 10 统一编译验证）

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/local/questionbank/data/repository/QuestionRepositoryImpl.kt
git commit -m "feat(QuestionRepositoryImpl): implement observeFavoritesGroupedByBank() with 3-Flow combine"
```

---

## Task 4: AppContainer 更新 questionRepository 构造

**文件：** 修改 `di/AppContainer.kt:40-42`

将 `questionRepository` 的构造从：
```kotlin
val questionRepository: QuestionRepository by lazy {
    QuestionRepositoryImpl(questionDao, favoriteDao)
}
```
改为：
```kotlin
val questionRepository: QuestionRepository by lazy {
    QuestionRepositoryImpl(questionDao, favoriteDao, bankDao)
}
```

- [ ] **Step 1: 修改 AppContainer.kt 第 40-42 行**
- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/local/questionbank/di/AppContainer.kt
git commit -m "chore(AppContainer): pass bankDao to QuestionRepositoryImpl"
```

---

## Task 5: FavoriteListUiState 和 FavoriteListViewModel

**文件：** 创建 `presentation/viewmodel/FavoriteListViewModel.kt`

```kotlin
package com.local.questionbank.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.questionbank.domain.model.FavoriteGroup
import com.local.questionbank.domain.repository.FavoriteRepository
import com.local.questionbank.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class FavoriteListUiState(
    val isLoading: Boolean = true,
    val groups: List<FavoriteGroup> = emptyList(),
    val errorMessage: String? = null
)

class FavoriteListViewModel(
    private val questionRepository: QuestionRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoriteListUiState())
    val uiState: StateFlow<FavoriteListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            questionRepository.observeFavoritesGroupedByBank()
                .map<List<FavoriteGroup>, FavoriteListUiState> { groups ->
                    FavoriteListUiState(isLoading = false, groups = groups)
                }
                .catch { e ->
                    emit(FavoriteListUiState(isLoading = false, errorMessage = e.message))
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun deleteFavorite(questionId: Long) {
        viewModelScope.launch {
            favoriteRepository.toggleFavorite(questionId)
        }
    }

    fun undoDelete(questionId: Long) {
        viewModelScope.launch {
            favoriteRepository.toggleFavorite(questionId, "默认")
        }
    }
}
```

- [ ] **Step 1: 创建 FavoriteListViewModel.kt**
- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/local/questionbank/presentation/viewmodel/FavoriteListViewModel.kt
git commit -m "feat: add FavoriteListViewModel with grouped favorites state"
```

---

## Task 6: AppViewModelFactory 注册 FavoriteListViewModel

**文件：** 修改 `presentation/viewmodel/AppViewModelFactory.kt:27-39`

在 `creators` Map 中新增：

```kotlin
FavoriteListViewModel::class.java to { c ->
    FavoriteListViewModel(
        questionRepository = c.questionRepository,
        favoriteRepository = c.favoriteRepository
    )
},
```

完整 Map 变为：

```kotlin
private val creators: Map<Class<out ViewModel>, (AppContainer) -> ViewModel> = mapOf(
    BankListViewModel::class.java to { c -> BankListViewModel(c.questionBankRepository) },
    ImportViewModel::class.java to { c ->
        ImportViewModel(c.jsonFileParser, c.questionBankRepository)
    },
    QuestionViewModel::class.java to { c ->
        QuestionViewModel(
            questionRepository = c.questionRepository,
            answerRepository = c.answerRepository,
            favoriteRepository = c.favoriteRepository
        )
    },
    FavoriteListViewModel::class.java to { c ->
        FavoriteListViewModel(
            questionRepository = c.questionRepository,
            favoriteRepository = c.favoriteRepository
        )
    }
)
```

- [ ] **Step 1: 修改 AppViewModelFactory.kt**
- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/local/questionbank/presentation/viewmodel/AppViewModelFactory.kt
git commit -m "feat(AppViewModelFactory): register FavoriteListViewModel"
```

---

## Task 7: Routes.kt 新增 FAVORITE_LIST 常量

**文件：** 修改 `presentation/navigation/Routes.kt:7`

在 `Routes` object 中新增：

```kotlin
const val FAVORITE_LIST = "favorite_list"
```

- [ ] **Step 1: 修改 Routes.kt**
- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/local/questionbank/presentation/navigation/Routes.kt
git commit -m "feat(Routes): add FAVORITE_LIST constant"
```

---

## Task 8: FavoriteListScreen Composable

**文件：** 创建 `presentation/screen/FavoriteListScreen.kt`

```kotlin
package com.local.questionbank.presentation.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.local.questionbank.domain.model.FavoriteGroup
import com.local.questionbank.domain.model.Question
import com.local.questionbank.presentation.viewmodel.FavoriteListUiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteListScreen(
    uiState: FavoriteListUiState,
    onNavigateBack: () -> Unit,
    onDeleteFavorite: (Long) -> Unit,
    onUndoDelete: (Long) -> Unit,
    onQuestionClick: (Question) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                ) {
                    CircularProgressIndicator()
                }
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = uiState.groups,
                        key = { it.bank.id }
                    ) { group ->
                        FavoriteGroupSection(
                            group = group,
                            onDeleteFavorite = onDeleteFavorite,
                            onUndoDelete = onUndoDelete,
                            onQuestionClick = onQuestionClick,
                            snackbarHostState = snackbarHostState,
                            scope = scope
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteGroupSection(
    group: FavoriteGroup,
    onDeleteFavorite: (Long) -> Unit,
    onUndoDelete: (Long) -> Unit,
    onQuestionClick: (Question) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        }
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            group.favorites.forEach { question ->
                FavoriteQuestionItem(
                    question = question,
                    onDelete = { onDeleteFavorite(question.id) },
                    onUndoDelete = { onUndoDelete(question.id) },
                    onClick = { onQuestionClick(question) },
                    snackbarHostState = snackbarHostState,
                    scope = scope
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteQuestionItem(
    question: Question,
    onDelete: () -> Unit,
    onUndoDelete: () -> Unit,
    onClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "已取消收藏",
                        actionLabel = "撤销",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onUndoDelete()
                    }
                }
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFFE53935)
                    else -> Color.Transparent
                },
                label = "swipe_bg_color"
            )
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
        enableDismiss = { it == SwipeToDismissBoxValue.StartToEnd }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Text(
                text = question.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
        }
    }
}
```

- [ ] **Step 1: 创建 FavoriteListScreen.kt**
- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/local/questionbank/presentation/screen/FavoriteListScreen.kt
git commit -m "feat: add FavoriteListScreen with swipe-to-delete and snackbar undo"
```

---

## Task 9: AppNavGraph 注册 FavoriteListScreen 路由

**文件：** 修改 `presentation/navigation/AppNavGraph.kt`

**改动点：**
1. 新增 import `FavoriteListScreen`
2. 在 `NavHost` 的 `composables` 体内，新增 `composable(Routes.FAVORITE_LIST) { ... }`

在现有 `composable(Routes.BANK_LIST) { ... }` 之后、`composable(Routes.IMPORT) { ... }` 之前插入：

```kotlin
composable(Routes.FAVORITE_LIST) {
    val viewModel: FavoriteListViewModel = viewModel(
        factory = AppViewModelFactory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FavoriteListScreen(
        uiState = state,
        onNavigateBack = { navController.popBackStack() },
        onDeleteFavorite = viewModel::deleteFavorite,
        onUndoDelete = viewModel::undoDelete,
        onQuestionClick = { question ->
            navController.navigate(Routes.question(question.bankId, "FAVORITE_ONLY"))
        }
    )
}
```

- [ ] **Step 1: 修改 AppNavGraph.kt**
- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/local/questionbank/presentation/navigation/AppNavGraph.kt
git commit -m "feat(AppNavGraph): register FAVORITE_LIST route and FavoriteListScreen"
```

---

## Task 10: BankListScreen 入口改造

**文件：** 修改 `presentation/screen/BankListScreen.kt`

**改动 1：** `onOpenFavorites` 的使用处（`GlobalEntryRow` 的 Card 点击）改为跳 `Routes.FAVORITE_LIST`：

在 `BankListScreen` composable 内，把 `onOpenFavorites` 传参从：
```kotlin
onOpenFavorites = { navController.navigate(Routes.question(0L, "FAVORITE_ONLY")) }
```
改为：
```kotlin
onOpenFavorites = { navController.navigate(Routes.FAVORITE_LIST) }
```

**注意：** `Routes.FAVORITE_LIST` 需要 import：`com.local.questionbank.presentation.navigation.Routes`

- [ ] **Step 1: 修改 BankListScreen.kt 第 36-37 行**
- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/local/questionbank/presentation/screen/BankListScreen.kt
git commit -m "refactor(BankListScreen): onOpenFavorites now navigates to FAVORITE_LIST"
```

---

## Task 11: 编译验证

**验证点：**
1. `./gradlew assembleDebug` 成功，无编译错误
2. 确认所有 import 正确
3. 确认 `AppContainer` 中 `QuestionRepositoryImpl` 构造参数顺序正确

**命令：**

```bash
cd D:/QuestionBankAndroid && ./gradlew assembleDebug 2>&1 | tail -30
```

预期：无错误，BUILD SUCCESSFUL

- [ ] **Step 1: 运行 assembleDebug**
- [ ] **Step 2: 若有编译错误，根据错误信息修复后重新编译**
- [ ] **Step 3: 提交（如有代码变更）**

---

## Task 12: 功能验证测试用例

手动测试（不写单元测试，靠人工验证）：

- [ ] **Test 1:** 收藏一些题目，然后进入「收藏夹」页面，确认按题库分组显示
- [ ] **Test 2:** 确认每组内题目按收藏时间倒序（最新在前）
- [ ] **Test 3:** 确认组间按最近收藏时间倒序
- [ ] **Test 4:** 左滑一条收藏，出现红色删除背景，点击删除，确认 Snackbar 出现
- [ ] **Test 5:** 在 Snackbar 消失前点击「撤销」，确认题目恢复
- [ ] **Test 6:** 点击收藏题目，确认跳转进入该题库的 FAVORITE_ONLY 刷题模式
- [ ] **Test 7:** 无收藏时进入收藏列表页，确认空状态 UI 正确显示
- [ ] **Test 8:** 确认 BankListScreen 顶部"收藏夹"卡片现在跳转到收藏列表页而非直接刷题

---

## Spec 覆盖检查

| Spec 需求 | 实现位置 |
|---|---|
| 新增 `FavoriteGroup` domain model | Task 1 |
| 新增 `QuestionWithFavoriteMeta` | Task 1 |
| `QuestionRepository` 新增 `observeFavoritesGroupedByBank()` | Task 2 |
| `QuestionRepositoryImpl` 实现该方法 | Task 3 |
| `AppContainer` 更新构造参数 | Task 4 |
| `FavoriteListUiState` + `FavoriteListViewModel` | Task 5 |
| `AppViewModelFactory` 注册 | Task 6 |
| `Routes.FAVORITE_LIST` | Task 7 |
| `FavoriteListScreen` | Task 8 |
| NavGraph 注册 | Task 9 |
| BankListScreen 入口改造 | Task 10 |
| 编译通过 | Task 11 |
| 功能验证 | Task 12 |

所有 spec 需求均有对应任务覆盖，无遗漏。
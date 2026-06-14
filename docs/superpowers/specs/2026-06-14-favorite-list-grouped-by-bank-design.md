# 按题库分类收藏列表页设计

**日期：** 2026-06-14
**状态：** 已批准

---

## 1. 概述

在现有收藏功能基础上，新增一个独立的「收藏列表页」，将用户收藏的题目按所属题库分组展示，支持按题库折叠、左滑取消收藏、点击进入刷题。

**背景：** 现有收藏功能已完整（FavoriteEntity、FavoriteDao、FavoriteRepository、QuestionScreen 星标按钮），但没有统一查看和管理收藏的入口，用户无法直观看到"我收藏了哪些题、在哪个题库"。

---

## 2. 路由与入口

**Routes.kt 新增：**
```kotlin
const val FAVORITE_LIST = "favorite_list"
```

**BankListScreen 改造：** 顶部「收藏夹」卡片点击行为从直接跳转刷题改为跳转 `FavoriteListScreen`：
```kotlin
onOpenFavorites = { navController.navigate(Routes.FAVORITE_LIST) }
```

**返回逻辑：** FavoriteListScreen TopAppBar 返回按钮 `navigateUp()`，自然回到 BankListScreen。

---

## 3. 数据层

### 3.1 Domain Model（新增）

```kotlin
// 文件：domain/model/FavoriteGroup.kt
data class FavoriteGroup(
    val bank: QuestionBank,
    val favorites: List<Question>   // 按收藏时间倒序
)
```

### 3.3 QuestionWithFavoriteMeta（新增 Domain Model）

由于 `Question` 本身不包含收藏时间（`createTimestamp`）和标签（`tag`），引入中间类型：

```kotlin
// 文件：domain/model/QuestionWithFavoriteMeta.kt
data class QuestionWithFavoriteMeta(
    val question: Question,
    val favoriteTimestamp: Long,  // 来自 FavoriteEntity.createTimestamp
    val tag: String               // 来自 FavoriteEntity.tag
)
```

### 3.4 QuestionRepository 新增方法

```kotlin
// 文件：domain/repository/QuestionRepository.kt
interface QuestionRepository {
    // ... 现有方法保留
    fun observeFavoritesGroupedByBank(): Flow<List<FavoriteGroup>>
}
```

**实现逻辑（QuestionRepositoryImpl）：**
1. `combine` 三个 Flow：`favoriteDao.observeAll()`（含 createTimestamp/tag）、`questionDao.observeAllOrdered()`（所有题目）、`questionBankRepository.observeBanks()`
2. 通过 `questionId` 将 `FavoriteEntity` 与 `QuestionEntity` 匹配，得到 `List<QuestionWithFavoriteMeta>`
3. 按 `favoriteTimestamp` 倒序
4. 按 `bankId` groupBy，得到 `List<FavoriteGroup>`
5. 整个组列表按每组最新收藏时间倒序

```kotlin
override fun observeFavoritesGroupedByBank(): Flow<List<FavoriteGroup>> =
    combine(
        favoriteDao.observeAll(),            // List<FavoriteEntity>
        questionDao.observeAllOrdered(),     // List<QuestionEntity>
        questionBankRepository.observeBanks()
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
                bankMap[bankId]?.let { bank ->
                    FavoriteGroup(
                        bank = bank,
                        favorites = items.map { it.question },
                        latestFavoriteTimestamp = items.first().favoriteTimestamp
                    )
                }
            }
            .sortedByDescending { it.latestFavoriteTimestamp }
    }
```

**FavoriteGroup 改造：**
```kotlin
data class FavoriteGroup(
    val bank: QuestionBank,
    val favorites: List<Question>,        // 按收藏时间倒序
    val latestFavoriteTimestamp: Long     // 用于整组排序
)
```

---

## 4. ViewModel 层

### 4.1 FavoriteListUiState

```kotlin
data class FavoriteListUiState(
    val isLoading: Boolean = true,
    val groups: List<FavoriteGroup> = emptyList(),
    val errorMessage: String? = null
)
```

### 4.2 FavoriteListViewModel

```kotlin
class FavoriteListViewModel(
    private val questionRepository: QuestionRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    val uiState: StateFlow<FavoriteListUiState> = questionRepository
        .observeFavoritesGroupedByBank()
        .map<List<FavoriteGroup>, FavoriteListUiState> { groups ->
            FavoriteListUiState(isLoading = false, groups = groups)
        }
        .catch { e -> emit(FavoriteListUiState(isLoading = false, errorMessage = e.message)) }
        .stateIn(...)

    fun deleteFavorite(questionId: Long) {
        viewModelScope.launch {
            favoriteRepository.toggleFavorite(questionId)
        }
    }

    // 撤销：重新插入（tag 使用 "默认"，因为删除时没有保留 tag 信息）
    fun undoDelete(questionId: Long) {
        viewModelScope.launch {
            favoriteRepository.toggleFavorite(questionId, "默认")
        }
    }
}
```

---

## 5. UI 层（FavoriteListScreen）

### 5.1 布局结构

```
Scaffold
└─ TopAppBar（标题"收藏夹"，NavigationIcon 返回）
└─ LazyColumn
    └─ items(groups) { group ->
        FavoriteGroupSection(group)   // 每组 = 一个题库
    }
```

### 5.2 FavoriteGroupSection（每组）

```
Column
├─ Row（组头）  QuestionBank.name + Badge（收藏数量）
└─ Column
    └─ items(group.favorites) { question ->
        FavoriteQuestionItem
    }
```

### 5.3 FavoriteQuestionItem

每行显示：
- 题目标题（Question.title）
- 收藏时间（来自 FavoriteEntity.createTimestamp，需在 group 构造时带入）

**左滑删除：**
```kotlin
SwipeToDismissBox(
    state = dismissState,
    backgroundContent = { Box(...) },  // 红色删除背景 + 图标
    enableDismiss = { direction == SwipeToDismissBoxValue.StartToEnd }
)
```

### 5.4 状态处理

- **空状态：** Center + Icon（StarOutline）+ "还没有收藏题目"
- **加载中：** `CircularProgressIndicator` 居中
- **Snackbar：** 取消收藏后显示"已取消收藏"，带"撤销"按钮，5 秒后自动消失

### 5.5 点击行为

点击任意收藏行 → `navController.navigate(Routes.question(question.bankId, "FAVORITE_ONLY"))`

---

## 6. DI 注入

```kotlin
// AppContainer.kt
val favoriteListViewModel: FavoriteListViewModel by lazy {
    FavoriteListViewModel(questionRepository, favoriteRepository)
}
```

Navigation Graph 中创建 `FavoriteListScreen` 时传入 ViewModel：
```kotlin
composable(Routes.FAVORITE_LIST) {
    val viewModel: FavoriteListViewModel = viewModel(
        viewModelStoreOwner = it.findViewModelStoreOwner()
    )
    FavoriteListScreen(
        uiState = viewModel.uiState.collectAsState().value,
        onNavigateBack = { navController.navigateUp() },
        onDeleteFavorite = viewModel::deleteFavorite,
        onUndoDelete = viewModel::undoDelete,
        onQuestionClick = { question -> navController.navigate(Routes.question(question.bankId, "FAVORITE_ONLY")) }
    )
}
```

---

## 7. 实现步骤（供 writing-plans 使用）

1. 新增 `FavoriteGroup.kt` domain model
2. `QuestionRepository` 新增 `observeFavoritesGroupedByBank()` 接口
3. `QuestionRepositoryImpl` 实现该方法（combine + groupBy）
4. 新增 `FavoriteListUiState` data class
5. 新建 `FavoriteListViewModel`
6. `Routes.kt` 新增 `FAVORITE_LIST` 常量
7. 新建 `FavoriteListScreen` Composable
8. `AppContainer` 注入 `FavoriteListViewModel`
9. Navigation Graph 注册 `FavoriteListScreen` 路由
10. `BankListScreen` 改造入口（`onOpenFavorites` 改为跳 `FAVORITE_LIST`）

---

## 8. 已知待确认点

- ~~Question 是否有 createTimestamp~~ 已解决：引入 `QuestionWithFavoriteMeta` 中间类型，从 `FavoriteEntity.createTimestamp` 获取

---

## 9. 测试验证点

- [ ] 收藏列表页能正确按题库分组显示
- [ ] 每组内收藏题按收藏时间倒序
- [ ] 左滑删除后 Snackbar 出现，5 秒内可撤销
- [ ] 点击收藏题能正确跳转到该题库刷题页
- [ ] 无收藏时显示空状态
- [ ] 从刷题页取消收藏后，返回列表页自动刷新
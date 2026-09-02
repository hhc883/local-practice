package com.local.questionbank.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.questionbank.domain.model.FavoriteGroup
import com.local.questionbank.domain.repository.FavoriteRepository
import com.local.questionbank.domain.repository.QuestionBankRepository
import com.local.questionbank.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class FavoriteListUiState(
    val isLoading: Boolean = true,
    val groups: List<FavoriteGroup> = emptyList(),
    val errorMessage: String? = null
)

/**
 * 收藏夹列表 ViewModel
 *
 * 核心能力：
 *  - 实时观察 [QuestionRepository.observeFavoritesGroupedByBank]
 *  - **乐观更新**两份覆盖层：分组顺序 + 单题顺序
 *    - 持久化完成 / repository Flow 推回真实数据后自动清空
 *  - 题目删除：调用 [FavoriteRepository.toggleFavorite]（已有的删除路径）
 */
class FavoriteListViewModel(
    private val questionRepository: QuestionRepository,
    private val favoriteRepository: FavoriteRepository,
    @Suppress("unused") private val questionBankRepository: QuestionBankRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoriteListUiState())
    val uiState: StateFlow<FavoriteListUiState> = _uiState.asStateFlow()

    /**
     * 分组顺序乐观层：用户拖拽分组后立即在 UI 层交换，repository 持久化后清空。
     * key = bankId, value = 期望的 bankId 顺序
     */
    private val _optimisticGroupOrder = MutableStateFlow<List<Long>?>(null)

    /**
     * 单题顺序乐观层：key = bankId, value = 期望的 favoriteDbId 顺序
     */
    private val _optimisticQuestionOrder = MutableStateFlow<Map<Long, List<Long>>?>(null)

    init {
        viewModelScope.launch {
            combine(
                questionRepository.observeFavoritesGroupedByBank(),
                _optimisticGroupOrder,
                _optimisticQuestionOrder
            ) { groups, optGroup, optQuestion ->
                val orderedGroups = if (optGroup == null) groups
                else optGroup.mapNotNull { id -> groups.firstOrNull { it.bank.id == id } }
                val finalGroups = if (optQuestion == null) orderedGroups
                else orderedGroups.map { g ->
                    val expected = optQuestion[g.bank.id]
                    if (expected == null) g
                    else {
                        val map = g.favorites.zip(g.favoriteDbIds).associate { (q, fid) -> fid to q }
                        FavoriteGroup(
                            bank = g.bank,
                            favorites = expected.mapNotNull { map[it] },
                            favoriteDbIds = expected,
                            latestFavoriteTimestamp = g.latestFavoriteTimestamp
                        )
                    }
                }
                FavoriteListUiState(isLoading = false, groups = finalGroups)
            }
                .catch { e ->
                    emit(FavoriteListUiState(isLoading = false, errorMessage = e.message))
                }
                .collectLatest { state -> _uiState.value = state }
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

    /**
     * 拖动分组：写 QuestionBank.sortIndex，UI 立即交换。
     * 注意：分组排序复用首页题库 sortIndex（用户已确认）。
     */
    fun moveGroup(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.groups
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val newOrder = current.toMutableList().also {
            val moved = it.removeAt(fromIndex)
            it.add(toIndex, moved)
        }.map { it.bank.id }
        _optimisticGroupOrder.value = newOrder
        viewModelScope.launch {
            runCatching { questionBankRepository.reorderBanks(newOrder) }
                .onSuccess { _optimisticGroupOrder.value = null }
                .onFailure { e ->
                    _optimisticGroupOrder.value = null
                    _uiState.value = _uiState.value.copy(errorMessage = "分组排序失败：${e.message}")
                }
        }
    }

    /**
     * 拖动收藏题目：写 Favorite.sortIndex，UI 立即交换。
     * @param bankId  题库 id
     * @param fromIndex  在该题库下的源 index
     * @param toIndex    目标 index
     */
    fun moveQuestion(bankId: Long, fromIndex: Int, toIndex: Int) {
        val group = _uiState.value.groups.firstOrNull { it.bank.id == bankId } ?: return
        val ids = group.favoriteDbIds
        if (fromIndex !in ids.indices || toIndex !in ids.indices || fromIndex == toIndex) return
        val newOrder = ids.toMutableList().also {
            val moved = it.removeAt(fromIndex)
            it.add(toIndex, moved)
        }
        _optimisticQuestionOrder.value = (_optimisticQuestionOrder.value ?: emptyMap()) + (bankId to newOrder)
        viewModelScope.launch {
            runCatching { favoriteRepository.reorderFavorites(newOrder) }
                .onSuccess { _optimisticQuestionOrder.value = null }
                .onFailure { e ->
                    _optimisticQuestionOrder.value = null
                    _uiState.value = _uiState.value.copy(errorMessage = "题目排序失败：${e.message}")
                }
        }
    }
}

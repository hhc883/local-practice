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
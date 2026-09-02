package com.local.questionbank.data.repository

import com.local.questionbank.data.database.dao.FavoriteDao
import com.local.questionbank.data.database.entity.FavoriteEntity
import com.local.questionbank.data.mapper.EntityMappers.toDomain
import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 收藏仓库实现
 *
 * - toggle 走"先查再写"，存在轻微竞态；但本地单用户场景下完全可接受
 * - 若日后引入多端同步，toggle 应改写为 @Transaction
 */
class FavoriteRepositoryImpl(
    private val favoriteDao: FavoriteDao
) : FavoriteRepository {

    override suspend fun toggleFavorite(questionId: Long, tag: String): Boolean {
        val current = favoriteDao.countByQuestion(questionId) > 0
        return if (current) {
            favoriteDao.deleteByQuestion(questionId)
            false
        } else {
            // 新收藏追加到末尾：取当前 max + 1000
            val nextIndex = favoriteDao.maxSortIndex() + 1000L
            favoriteDao.insert(
                FavoriteEntity(
                    questionId = questionId,
                    tag = tag,
                    createTimestamp = System.currentTimeMillis(),
                    sortIndex = nextIndex
                )
            )
            true
        }
    }

    override fun observeIsFavorited(questionId: Long): Flow<Boolean> =
        favoriteDao.observeIsFavorited(questionId)

    override fun observeFavoriteIds(): Flow<List<Long>> =
        favoriteDao.observeFavoriteIds()

    override fun observeFavoriteQuestions(bankId: Long): Flow<List<Question>> =
        favoriteDao.observeFavoriteQuestions(bankId).map { list -> list.map { it.toDomain() } }

    override suspend fun reorderFavorites(orderedFavoriteIds: List<Long>) {
        if (orderedFavoriteIds.isEmpty()) return
        favoriteDao.applyReorder(orderedFavoriteIds)
    }
}

package com.local.questionbank.domain.repository

import com.local.questionbank.domain.model.Question
import kotlinx.coroutines.flow.Flow

/**
 * 收藏仓库
 *
 * 行为约定：
 *  - [toggleFavorite]  若已收藏则删除，否则新增；返回操作后的最终收藏状态
 *  - [observeIsFavorited] 单题的实时收藏状态
 *  - [observeFavoriteIds] 全量收藏 id 集合
 *  - [observeFavoriteQuestions] 限定题库范围内的收藏题目（按收藏时间倒序）
 */
interface FavoriteRepository {

    suspend fun toggleFavorite(questionId: Long, tag: String = "默认"): Boolean

    fun observeIsFavorited(questionId: Long): Flow<Boolean>

    fun observeFavoriteIds(): Flow<List<Long>>

    fun observeFavoriteQuestions(bankId: Long): Flow<List<Question>>
}

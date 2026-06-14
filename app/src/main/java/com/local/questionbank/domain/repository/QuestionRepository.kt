package com.local.questionbank.domain.repository

import com.local.questionbank.domain.model.FavoriteGroup
import com.local.questionbank.domain.model.Question
import kotlinx.coroutines.flow.Flow

/**
 * 题目仓库接口
 *
 * 暴露两种刷题顺序：
 *  - [observeOrdered] 主键顺序，便于顺序练习
 *  - [observeRandom]   每次 Flow 重新订阅时洗牌
 */
interface QuestionRepository {

    fun observeQuestions(bankId: Long): Flow<List<Question>>

    fun observeRandomQuestions(bankId: Long): Flow<List<Question>>

    suspend fun getQuestion(questionId: Long): Question?

    /** 错题 id 集合（用于"加入错题集"或"只看错题"） */
    fun observeWrongQuestionIds(): Flow<List<Long>>

    /** 指定题库下的"收藏"题目（按收藏时间倒序） */
    fun observeFavoriteQuestions(bankId: Long): Flow<List<Question>>

    /** 全部题库范围的"收藏"题目（用于全局收藏夹入口，bankId=0 场景） */
    fun observeAllFavoriteQuestions(): Flow<List<Question>>

    /** 全部题库范围的有序题目（bankId=0 时使用） */
    fun observeAllOrdered(): Flow<List<Question>>

    /** 全部题库范围随机题目 */
    fun observeAllRandom(): Flow<List<Question>>

    /** 全部错题（去重 + 最近一次答错排序） */
    fun observeAllWrongQuestions(): Flow<List<Question>>

    /** 按题库分组的收藏题目（每组内按收藏时间倒序，组间按最近收藏时间倒序） */
    fun observeFavoritesGroupedByBank(): Flow<List<FavoriteGroup>>
}

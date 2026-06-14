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

/**
 * 题目仓库实现
 *
 * 只负责"读"；写入路径统一由 [QuestionBankRepositoryImpl.importBank] 处理
 *
 * 收藏相关查询委托 [FavoriteDao]（虽然概念上与 Question 相关，但收藏表 join 在
 * FavoriteDao 中写更内聚，避免 QuestionDao 越界依赖）
 */
class QuestionRepositoryImpl(
    private val questionDao: QuestionDao,
    private val favoriteDao: FavoriteDao,
    private val questionBankDao: QuestionBankDao
) : QuestionRepository {

    override fun observeQuestions(bankId: Long): Flow<List<Question>> =
        questionDao.observeByBankOrdered(bankId).map { list -> list.map { it.toDomain() } }

    override fun observeRandomQuestions(bankId: Long): Flow<List<Question>> =
        questionDao.observeByBankRandom(bankId).map { list -> list.map { it.toDomain() } }

    override suspend fun getQuestion(questionId: Long): Question? =
        questionDao.findById(questionId)?.toDomain()

    override fun observeWrongQuestionIds(): Flow<List<Long>> =
        questionDao.observeIncorrectQuestionIds()

    override fun observeFavoriteQuestions(bankId: Long): Flow<List<Question>> =
        favoriteDao.observeFavoriteQuestions(bankId).map { list -> list.map { it.toDomain() } }

    override fun observeAllFavoriteQuestions(): Flow<List<Question>> =
        favoriteDao.observeAllFavoriteQuestions().map { list -> list.map { it.toDomain() } }

    override fun observeAllOrdered(): Flow<List<Question>> =
        questionDao.observeAllOrdered().map { list -> list.map { it.toDomain() } }

    override fun observeAllRandom(): Flow<List<Question>> =
        questionDao.observeAllRandom().map { list -> list.map { it.toDomain() } }

    override fun observeAllWrongQuestions(): Flow<List<Question>> =
        questionDao.observeAllWrongQuestions().map { list -> list.map { it.toDomain() } }

    override fun observeFavoritesGroupedByBank(): Flow<List<FavoriteGroup>> =
        combine(
            favoriteDao.observeAll(),           // List<FavoriteEntity>
            questionDao.observeAllOrdered(),    // List<QuestionEntity>
            questionBankDao.observeAll()        // List<QuestionBankEntity>
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
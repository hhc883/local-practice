package com.local.questionbank.data.repository

import com.local.questionbank.data.database.dao.AnswerRecordDao
import com.local.questionbank.data.mapper.EntityMappers.toDomain
import com.local.questionbank.data.mapper.EntityMappers.toEntity
import com.local.questionbank.domain.model.AnswerRecord
import com.local.questionbank.domain.repository.AnswerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 答题记录仓库实现
 */
class AnswerRepositoryImpl(
    private val answerRecordDao: AnswerRecordDao
) : AnswerRepository {

    override suspend fun record(record: AnswerRecord) {
        answerRecordDao.insert(record.toEntity())
    }

    override fun observeByQuestion(questionId: Long): Flow<List<AnswerRecord>> =
        answerRecordDao.observeByQuestion(questionId).map { list -> list.map { it.toDomain() } }

    override fun observeWrongQuestionIds(): Flow<List<Long>> =
        answerRecordDao.observeWrongQuestionIds()

    override suspend fun clearRecords(questionId: Long) {
        answerRecordDao.deleteByQuestion(questionId)
    }

    override suspend fun progress(questionId: Long): AnswerRepository.Progress {
        val total = answerRecordDao.countByQuestion(questionId)
        val correct = answerRecordDao.countCorrectByQuestion(questionId)
        return AnswerRepository.Progress(total = total, correct = correct)
    }
}

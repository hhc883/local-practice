package com.local.questionbank.domain.repository

import com.local.questionbank.domain.model.AnswerRecord
import kotlinx.coroutines.flow.Flow

/**
 * 答题记录仓库
 */
interface AnswerRepository {

    /** 写入一条记录 */
    suspend fun record(record: AnswerRecord)

    /** 某题的全部历史记录 */
    fun observeByQuestion(questionId: Long): Flow<List<AnswerRecord>>

    /** 错题 id 集合（最近一次答错的题） */
    fun observeWrongQuestionIds(): Flow<List<Long>>

    /** 进度统计：某题总答题数 / 答对数 */
    suspend fun progress(questionId: Long): Progress

    data class Progress(val total: Int, val correct: Int)
}

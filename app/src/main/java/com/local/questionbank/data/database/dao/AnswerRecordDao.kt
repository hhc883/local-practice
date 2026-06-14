package com.local.questionbank.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.local.questionbank.data.database.entity.AnswerRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 答题记录 Dao
 *
 * - 写入：[insert]
 * - 错题集聚合：[observeWrongQuestionIds] 过滤最近 N 条错误记录涉及的题目
 * - 统计：[countCorrect] / [countTotal] 供刷题进度条使用
 */
@Dao
interface AnswerRecordDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: AnswerRecordEntity): Long

    /** 某题的全部历史答题记录（时间倒序） */
    @Query("SELECT * FROM answer_record WHERE questionId = :questionId ORDER BY answerTimestamp DESC")
    fun observeByQuestion(questionId: Long): Flow<List<AnswerRecordEntity>>

    /** 最近答错的题 id（去重） */
    @Query(
        """
        SELECT questionId FROM answer_record
        WHERE isCorrect = 0
        GROUP BY questionId
        ORDER BY MAX(answerTimestamp) DESC
        """
    )
    fun observeWrongQuestionIds(): Flow<List<Long>>

    /** 某题总答题数 */
    @Query("SELECT COUNT(*) FROM answer_record WHERE questionId = :questionId")
    suspend fun countByQuestion(questionId: Long): Int

    /** 某题答对数 */
    @Query("SELECT COUNT(*) FROM answer_record WHERE questionId = :questionId AND isCorrect = 1")
    suspend fun countCorrectByQuestion(questionId: Long): Int

    /** 删除某题所有记录（题目删除时由外键级联兜底） */
    @Query("DELETE FROM answer_record WHERE questionId = :questionId")
    suspend fun deleteByQuestion(questionId: Long): Int
}

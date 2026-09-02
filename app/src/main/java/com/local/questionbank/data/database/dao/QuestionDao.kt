package com.local.questionbank.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.local.questionbank.data.database.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 题目表 Dao
 *
 * - 顺序刷题：[observeByBankOrdered]
 * - 随机刷题：[observeByBankRandom]（借助 SQL RANDOM，SQLite 支持）
 * - 错题集：[observeIncorrectQuestionIds] 暴露给上层做差集
 */
@Dao
interface QuestionDao {

    /** 顺序刷题：按主键自增遍历 */
    @Query("SELECT * FROM question WHERE bankId = :bankId ORDER BY id ASC")
    fun observeByBankOrdered(bankId: Long): Flow<List<QuestionEntity>>

    /** 随机刷题：每次 Flow 重新订阅时 SQLite 都会重新洗牌 */
    @Query("SELECT * FROM question WHERE bankId = :bankId ORDER BY RANDOM()")
    fun observeByBankRandom(bankId: Long): Flow<List<QuestionEntity>>

    /** 题目详情（用于答题/判题） */
    @Query("SELECT * FROM question WHERE id = :questionId")
    suspend fun findById(questionId: Long): QuestionEntity?

    /** 批量插入（导入时使用） */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(questions: List<QuestionEntity>): List<Long>

    /**
     * 用指定 id 批量插入题目,用于撤销删除时还原(保留原 id)
     * 冲突策略 REPLACE:撤销场景下不会冲突
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllWithId(questions: List<QuestionEntity>): List<Long>

    /** 单条插入（手动加题） */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(question: QuestionEntity): Long

    /** 删除题库下全部题目（题库删除时由外键级联兜底，此方法备用） */
    @Query("DELETE FROM question WHERE bankId = :bankId")
    suspend fun deleteByBank(bankId: Long): Int

    /** 错题 id 集合（最近一次答题错误的所有题目） */
    @Query(
        """
        SELECT questionId FROM answer_record
        WHERE isCorrect = 0
        GROUP BY questionId
        """
    )
    fun observeIncorrectQuestionIds(): Flow<List<Long>>

    /** 全局顺序刷题（bankId=0 时使用） */
    @Query("SELECT * FROM question ORDER BY id ASC")
    fun observeAllOrdered(): Flow<List<QuestionEntity>>

    /** 全局随机刷题 */
    @Query("SELECT * FROM question ORDER BY RANDOM()")
    fun observeAllRandom(): Flow<List<QuestionEntity>>

    /** 全局错题：按主键返回所有错题（与 AnswerRecord join 限定） */
    @Query(
        """
        SELECT q.* FROM question q
        INNER JOIN (
            SELECT questionId, MAX(answerTimestamp) AS lastTs
            FROM answer_record
            WHERE isCorrect = 0
            GROUP BY questionId
        ) r ON r.questionId = q.id
        ORDER BY r.lastTs DESC
        """
    )
    fun observeAllWrongQuestions(): Flow<List<QuestionEntity>>
}

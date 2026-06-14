package com.local.questionbank.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.local.questionbank.data.database.entity.QuestionBankEntity
import kotlinx.coroutines.flow.Flow

/**
 * 题库表 Dao
 *
 * 所有方法均使用 suspend 或 Flow，避免主线程 IO；
 * 事务方法 [insertBankWithQuestions] 负责在一次事务内写入题库 + 题目列表。
 */
@Dao
interface QuestionBankDao {

    /** 查询所有题库（按创建时间倒序） */
    @Query("SELECT * FROM question_bank ORDER BY createTimestamp DESC")
    fun observeAll(): Flow<List<QuestionBankEntity>>

    /** 主键查询（suspend，用于详情页） */
    @Query("SELECT * FROM question_bank WHERE id = :bankId")
    suspend fun findById(bankId: Long): QuestionBankEntity?

    /** 插入题库，返回自增 id */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bank: QuestionBankEntity): Long

    /** 重命名题库 */
    @Query("UPDATE question_bank SET name = :name WHERE id = :bankId")
    suspend fun rename(bankId: Long, name: String): Int

    /** 删除题库（外键级联会同步删除题目与答题记录） */
    @Query("DELETE FROM question_bank WHERE id = :bankId")
    suspend fun deleteById(bankId: Long): Int

    /**
     * 原子化导入：写入题库后再写题目。
     * Room 的事务保证：若题目插入失败，题库行也会回滚。
     */
    @Transaction
    suspend fun insertBankWithQuestions(
        bank: QuestionBankEntity,
        insertQuestions: suspend (bankId: Long) -> Unit
    ): Long {
        val newBankId = insert(bank)
        insertQuestions(newBankId)
        return newBankId
    }
}

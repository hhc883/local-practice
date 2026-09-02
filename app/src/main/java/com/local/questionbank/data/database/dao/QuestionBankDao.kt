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

    /** 查询所有题库（按用户手动拖拽顺序 [sortIndex] 升序） */
    @Query("SELECT * FROM question_bank ORDER BY sortIndex ASC, id ASC")
    fun observeAll(): Flow<List<QuestionBankEntity>>

    /** 主键查询（suspend，用于详情页） */
    @Query("SELECT * FROM question_bank WHERE id = :bankId")
    suspend fun findById(bankId: Long): QuestionBankEntity?

    /** 插入题库，返回自增 id */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bank: QuestionBankEntity): Long

    /**
     * 用指定 id 插入题库,用于撤销删除时还原(保留原 id 避免外键失效)
     * 发生冲突时直接 REPLACE 覆盖(撤销场景下不存在冲突,因为删除后该 id 已空)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithId(bank: QuestionBankEntity): Long

    /** 重命名题库 */
    @Query("UPDATE question_bank SET name = :name WHERE id = :bankId")
    suspend fun rename(bankId: Long, name: String): Int

    /** 删除题库（外键级联会同步删除题目与答题记录） */
    @Query("DELETE FROM question_bank WHERE id = :bankId")
    suspend fun deleteById(bankId: Long): Int

    /** 当前最大 sortIndex（用于新题库默认追加到末尾） */
    @Query("SELECT COALESCE(MAX(sortIndex), 0) FROM question_bank")
    suspend fun maxSortIndex(): Long

    /** 更新单行 sortIndex（拖拽中实时写回） */
    @Query("UPDATE question_bank SET sortIndex = :newIndex WHERE id = :bankId")
    suspend fun updateSortIndex(bankId: Long, newIndex: Long): Int

    /**
     * 一次性把 [orderedIds] 写为 1000 / 2000 / 3000 ... 的递增 sortIndex。
     * 用于一次拖拽完成后落库，避免在拖拽过程中频繁更新多行。
     */
    @Transaction
    suspend fun applyReorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            updateSortIndex(id, (index + 1).toLong() * 1000L)
        }
    }

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

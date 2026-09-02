package com.local.questionbank.domain.repository

import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.model.QuestionBank
import kotlinx.coroutines.flow.Flow

/**
 * 题库仓库接口
 *
 * 表现层只能依赖本接口；具体实现由 data 层提供
 */
interface QuestionBankRepository {

    /** 观察所有题库（按用户拖拽顺序 [sortIndex] 升序） */
    fun observeBanks(): Flow<List<QuestionBank>>

    /** 查询单个题库 */
    suspend fun getBank(bankId: Long): QuestionBank?

    /** 重命名题库 */
    suspend fun renameBank(bankId: Long, newName: String)

    /** 删除题库（题目与答题记录由外键级联删除） */
    suspend fun deleteBank(bankId: Long)

    /**
     * 原子化导入题库及其全部题目。新题库默认追加到末尾（sortIndex = max + 1000）。
     *
     * @return 新题库 id
     */
    suspend fun importBank(bank: QuestionBank): Long

    /**
     * 在指定题库下追加单题(用于 AI 出题后保存等场景)。
     * 复用 QuestionDao.insertAll,无需事务包装(单行插入本身原子)。
     *
     * @return 新题目 id
     */
    suspend fun addQuestion(bankId: Long, question: Question): Long

    /**
     * 按 [orderedIds] 顺序持久化题库列表的拖拽结果。
     * 持久化后，observeBanks() 会推送新的列表。
     */
    suspend fun reorderBanks(orderedIds: List<Long>)

    /**
     * 快照题库及其全部题目，用于"删除-撤销"场景。
     * 返回 null 表示该题库已不存在。
     */
    suspend fun snapshotBank(bankId: Long): BankSnapshot?

    /**
     * 用快照恢复题库(保留原 id)，整个写操作在一个事务中。
     * 撤销时调用，5 秒超时后快照会被丢弃。
     */
    suspend fun restoreBank(snapshot: BankSnapshot)

    /** 查询某题库下全部题目（用于题库详情） */
    fun observeQuestions(bankId: Long): Flow<List<Question>>
}

/**
 * 题库快照：用于删除-撤销场景
 *
 * 持有原 id,恢复时使用 REPLACE 冲突策略写回
 */
data class BankSnapshot(
    val bank: QuestionBank,
    val questions: List<Question>
)

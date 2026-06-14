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

    /** 观察所有题库（按创建时间倒序） */
    fun observeBanks(): Flow<List<QuestionBank>>

    /** 查询单个题库 */
    suspend fun getBank(bankId: Long): QuestionBank?

    /** 重命名题库 */
    suspend fun renameBank(bankId: Long, newName: String)

    /** 删除题库（题目与答题记录由外键级联删除） */
    suspend fun deleteBank(bankId: Long)

    /**
     * 原子化导入题库及其全部题目
     *
     * @return 新题库 id
     */
    suspend fun importBank(bank: QuestionBank): Long

    /** 查询某题库下全部题目（用于题库详情） */
    fun observeQuestions(bankId: Long): Flow<List<Question>>
}

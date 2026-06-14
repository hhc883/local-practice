package com.local.questionbank.data.repository

import androidx.room.withTransaction
import com.local.questionbank.data.database.AppDatabase
import com.local.questionbank.data.database.dao.QuestionBankDao
import com.local.questionbank.data.database.dao.QuestionDao
import com.local.questionbank.data.mapper.EntityMappers.toDomain
import com.local.questionbank.data.mapper.EntityMappers.toEntity
import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.model.QuestionBank
import com.local.questionbank.domain.repository.QuestionBankRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 题库仓库实现
 *
 * 关键点：
 *  - [importBank] 用 Room 事务，保证"题库主表 + 题目表"原子写入
 *  - IO 全部通过 Room suspend / Flow 自然走 IO 线程，不在此处显式 withContext
 */
class QuestionBankRepositoryImpl(
    private val database: AppDatabase,
    private val bankDao: QuestionBankDao,
    private val questionDao: QuestionDao
) : QuestionBankRepository {

    override fun observeBanks(): Flow<List<QuestionBank>> =
        bankDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getBank(bankId: Long): QuestionBank? {
        val entity = bankDao.findById(bankId) ?: return null
        val snapshot = takeSnapshot(bankId)
        return entity.toDomain().copy(questions = snapshot)
    }

    /** 一次性快照当前题库下的全部题目（顺序） */
    private suspend fun takeSnapshot(bankId: Long): List<Question> =
        questionDao.observeByBankOrdered(bankId).first().map { it.toDomain() }

    override suspend fun renameBank(bankId: Long, newName: String) {
        if (newName.isBlank()) return
        bankDao.rename(bankId, newName.trim())
    }

    override suspend fun deleteBank(bankId: Long) {
        // 题目与答题记录由外键 CASCADE 兜底；显式再删一次以防 schema 升级时漏配
        questionDao.deleteByBank(bankId)
        bankDao.deleteById(bankId)
    }

    override suspend fun importBank(bank: QuestionBank): Long = database.withTransaction {
        // 1) 写入题库
        val newBankId = bankDao.insert(bank.toEntity())
        // 2) 批量写入题目，复用 newBankId
        if (bank.questions.isNotEmpty()) {
            val rows = bank.questions.map { q ->
                q.copy(bankId = newBankId).toEntity()
            }
            questionDao.insertAll(rows)
        }
        newBankId
    }

    override fun observeQuestions(bankId: Long): Flow<List<Question>> =
        questionDao.observeByBankOrdered(bankId).map { list -> list.map { it.toDomain() } }
}

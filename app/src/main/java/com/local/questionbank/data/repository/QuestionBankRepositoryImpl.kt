package com.local.questionbank.data.repository

import androidx.room.withTransaction
import com.local.questionbank.data.database.AppDatabase
import com.local.questionbank.data.database.dao.QuestionBankDao
import com.local.questionbank.data.database.dao.QuestionDao
import com.local.questionbank.data.mapper.EntityMappers.toDomain
import com.local.questionbank.data.mapper.EntityMappers.toEntity
import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.model.QuestionBank
import com.local.questionbank.domain.repository.BankSnapshot
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

    override suspend fun reorderBanks(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        bankDao.applyReorder(orderedIds)
    }

    override suspend fun importBank(bank: QuestionBank): Long = database.withTransaction {
        // 1) 计算 sortIndex：取当前最大值 + 1000，使新题库默认追加到末尾
        //    保留用户已手动设置的顺序
        val nextIndex = bankDao.maxSortIndex() + 1000L
        val bankWithSort = bank.copy(sortIndex = nextIndex)
        // 2) 写入题库
        val newBankId = bankDao.insert(bankWithSort.toEntity())
        // 3) 批量写入题目，复用 newBankId
        if (bank.questions.isNotEmpty()) {
            val rows = bank.questions.map { q ->
                q.copy(bankId = newBankId).toEntity()
            }
            questionDao.insertAll(rows)
        }
        newBankId
    }

    override suspend fun addQuestion(bankId: Long, question: Question): Long {
        // 单题追加：覆盖 bankId,id 由数据库自增分配
        val rows = listOf(question.copy(bankId = bankId, id = 0L).toEntity())
        return questionDao.insertAll(rows).first()
    }

    override fun observeQuestions(bankId: Long): Flow<List<Question>> =
        questionDao.observeByBankOrdered(bankId).map { list -> list.map { it.toDomain() } }

    override suspend fun snapshotBank(bankId: Long): BankSnapshot? {
        val bank = bankDao.findById(bankId) ?: return null
        val questions = takeSnapshot(bankId)
        return BankSnapshot(bank = bank.toDomain(), questions = questions)
    }

    override suspend fun restoreBank(snapshot: BankSnapshot) = database.withTransaction {
        // 1) 还原题库(保留原 id,通过 REPLACE 覆盖)
        bankDao.insertWithId(snapshot.bank.toEntity())
        // 2) 还原题目(保留原 id 与 bankId)
        if (snapshot.questions.isNotEmpty()) {
            val rows = snapshot.questions.map { it.toEntity() }
            questionDao.insertAllWithId(rows)
        }
        // 注:AnswerRecord / Favorite 在 deleteBank 时已被外键 CASCADE 删除,无法恢复
        // 多数题库删除前尚未刷题/收藏,影响可接受
    }
}

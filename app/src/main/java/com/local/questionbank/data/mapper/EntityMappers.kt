package com.local.questionbank.data.mapper

import com.local.questionbank.data.database.entity.AnswerRecordEntity
import com.local.questionbank.data.database.entity.QuestionBankEntity
import com.local.questionbank.data.database.entity.QuestionEntity
import com.local.questionbank.domain.model.AnswerRecord
import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.model.QuestionBank
import com.local.questionbank.domain.model.QuestionType
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Entity ↔ 领域模型 双向转换
 *
 * - 集中处理 JSON 列表列（optionsJson / answerJson）的序列化
 * - 内部缓存 Moshi 适配器，避免每次分配
 */
object EntityMappers {

    private val moshi: Moshi = Moshi.Builder().build()
    private val stringListAdapter: JsonAdapter<List<String>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, String::class.java))
    private val intListAdapter: JsonAdapter<List<Int>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, Integer::class.java))

    // ---------------- 题库 ----------------

    fun QuestionBankEntity.toDomain(questionCount: Int = 0): QuestionBank = QuestionBank(
        id = id,
        name = name,
        description = description,
        createTimestamp = createTimestamp
        // 注：题目集合由 Repository 单独查询后合并，避免 N+1
    )

    fun QuestionBank.toEntity(): QuestionBankEntity = QuestionBankEntity(
        id = id,
        name = name,
        description = description,
        createTimestamp = createTimestamp
    )

    // ---------------- 题目 ----------------

    fun QuestionEntity.toDomain(): Question = Question(
        id = id,
        bankId = bankId,
        type = runCatching { QuestionType.fromRaw(type) }.getOrDefault(QuestionType.SINGLE),
        title = title,
        options = decodeStringList(optionsJson),
        answer = decodeStringList(answerJson),
        analysis = analysis
    )

    fun Question.toEntity(): QuestionEntity = QuestionEntity(
        id = id,
        bankId = bankId,
        type = type.name,
        title = title,
        optionsJson = encodeStringList(options),
        answerJson = encodeStringList(answer),
        analysis = analysis
    )

    // ---------------- 答题记录 ----------------

    fun AnswerRecordEntity.toDomain(): AnswerRecord = AnswerRecord(
        id = id,
        questionId = questionId,
        isCorrect = isCorrect,
        answerTimestamp = answerTimestamp
    )

    fun AnswerRecord.toEntity(): AnswerRecordEntity = AnswerRecordEntity(
        id = id,
        questionId = questionId,
        isCorrect = isCorrect,
        answerTimestamp = answerTimestamp
    )

    // ---------------- JSON 编解码工具 ----------------

    fun encodeStringList(list: List<String>): String = stringListAdapter.toJson(list)

    fun decodeStringList(json: String): List<String> =
        runCatching { stringListAdapter.fromJson(json) }.getOrNull().orEmpty()

    fun encodeIntList(list: List<Int>): String = intListAdapter.toJson(list)

    fun decodeIntList(json: String): List<Int> =
        runCatching { intListAdapter.fromJson(json) }.getOrNull().orEmpty()
}

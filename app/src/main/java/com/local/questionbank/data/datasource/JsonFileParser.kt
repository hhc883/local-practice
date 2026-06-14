package com.local.questionbank.data.datasource

import android.content.Context
import android.net.Uri
import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.model.QuestionBank
import com.local.questionbank.domain.model.QuestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import okio.buffer
import okio.source

/**
 * JSON 文件解析骨架
 *
 * 设计要点：
 *  1. 仅依赖 [Uri]，不读取绝对路径；Activity 端通过 SAF 拿到 Uri 即可
 *  2. IO 全部走 [Dispatchers.IO]，主线程不阻塞
 *  3. 解析阶段就把模板字段做完整性校验，缺字段直接抛出 [ImportFormatException]
 *  4. 把 DTO 转换成领域模型 [QuestionBank]，不再带 JSON 痕迹
 *
 * 调用示例（在 ViewModel 中）：
 * ```
 * val bank = withContext(Dispatchers.IO) {
 *     JsonFileParser(context).parseFromUri(uri)
 * }
 * ```
 */
class JsonFileParser(
    private val context: Context
) {
    // 不再使用反射；adapter 由 KSP 生成。
    private val moshi: Moshi = Moshi.Builder().build()
    private val bankAdapter = moshi.adapter(BankImportDto::class.java)

    /**
     * 解析入口
     *
     * @throws ImportFormatException 模板字段缺失或类型不符
     * @throws java.io.IOException    读取文件失败
     */
    suspend fun parseFromUri(uri: Uri): QuestionBank = withContext(Dispatchers.IO) {
        val rawJson = readText(uri)
        val dto = try {
            bankAdapter.fromJson(rawJson)
        } catch (e: JsonDataException) {
            throw ImportFormatException("JSON 解析失败：${e.message}", e)
        } ?: throw ImportFormatException("JSON 内容为空或结构不匹配")

        validateAndMap(dto)
    }

    // ---------------- 私有：纯 IO ----------------

    private fun readText(uri: Uri): String {
        // SAF：content:// 授权的 Uri，使用 ContentResolver.openInputStream
        val resolver = context.contentResolver
        val input = resolver.openInputStream(uri)
            ?: throw ImportFormatException("无法打开文件流，Uri=$uri")
        return input.use { stream ->
            stream.source().buffer().use { src -> src.readUtf8() }
        }
    }

    // ---------------- 私有：DTO -> 领域模型 + 校验 ----------------

    private fun validateAndMap(dto: BankImportDto): QuestionBank {
        if (dto.bankName.isBlank()) {
            throw ImportFormatException("题库名称 bankName 不能为空")
        }
        if (dto.questions.isEmpty()) {
            throw ImportFormatException("题库内至少包含 1 道题")
        }

        val questions = dto.questions.mapIndexed { index, q ->
            toQuestion(index, q)
        }

        return QuestionBank(
            id = 0L, // 新建，DB 写入后回填
            name = dto.bankName.trim(),
            description = dto.desc?.trim().takeUnless { it.isNullOrEmpty() },
            questions = questions
        )
    }

    private fun toQuestion(index: Int, q: QuestionImportDto): Question {
        if (q.title.isBlank()) {
            throw ImportFormatException("第 ${index + 1} 题题干为空")
        }
        val type = parseType(index, q.type)
        if (type != QuestionType.JUDGE && type != QuestionType.BLANK && q.options.isEmpty()) {
            throw ImportFormatException("第 ${index + 1} 题（${type}）缺少选项")
        }
        if (q.answer.isEmpty()) {
            throw ImportFormatException("第 ${index + 1} 题缺少正确答案")
        }
        val optionIndices = q.options.indices.toList()

        // 填空题答案直接用原始字符串，不转下标
        val answerList: List<String> = if (type == QuestionType.BLANK) {
            q.answer.map { it.trim() }
        } else {
            val answerIndices = q.answer.mapNotNull { it.toIntOrNull() }
            if (answerIndices.isEmpty()) {
                throw ImportFormatException("第 ${index + 1} 题答案必须为整数下标")
            }
            // 答案下标不能越界（非 JUDGE 题）
            if (type != QuestionType.JUDGE) {
                answerIndices.forEach { idx ->
                    if (idx !in optionIndices) {
                        throw ImportFormatException("第 ${index + 1} 题答案下标 $idx 越界")
                    }
                }
            }
            // 答案数量校验
            when (type) {
                QuestionType.SINGLE, QuestionType.DEBUG -> if (answerIndices.size != 1) {
                    throw ImportFormatException("第 ${index + 1} 题 SINGLE/DEBUG 类型答案必须唯一")
                }
                QuestionType.JUDGE -> if (answerIndices.size != 1 || answerIndices.first() !in 0..1) {
                    throw ImportFormatException("第 ${index + 1} 题 JUDGE 答案必须是 0 或 1")
                }
                QuestionType.MULTI, QuestionType.BLANK -> Unit
            }
            answerIndices.map { it.toString() }
        }

        return Question(
            id = 0L,
            bankId = 0L,
            type = type,
            title = q.title.trim(),
            options = q.options.map { it.trim() },
            answer = answerList,
            analysis = q.analysis?.trim().takeUnless { it.isNullOrEmpty() }
        )
    }

    private fun parseType(index: Int, raw: String): QuestionType {
        return when (raw.uppercase()) {
            "SINGLE" -> QuestionType.SINGLE
            "MULTI"  -> QuestionType.MULTI
            "JUDGE"  -> QuestionType.JUDGE
            "DEBUG"  -> QuestionType.DEBUG
            "BLANK", "READ" -> QuestionType.BLANK
            else -> throw ImportFormatException(
                "第 ${index + 1} 题题型非法：$raw，仅支持 SINGLE/MULTI/JUDGE/DEBUG/BLANK/READ"
            )
        }
    }
}

/**
 * 模板解析失败时的业务异常
 * - 与 IO 异常区分，便于上层按类型提示
 */
class ImportFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

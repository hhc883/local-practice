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
    /**
     * 解析入口
     *
     * @throws ImportFormatException 模板字段缺失或类型不符
     * @throws java.io.IOException    读取文件失败
     */
    suspend fun parseFromUri(uri: Uri): QuestionBank = withContext(Dispatchers.IO) {
        val rawJson = readText(uri)
        parseRaw(rawJson)
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

    companion object {
        // 不再使用反射；adapter 由 KSP 生成。
        private val moshi: Moshi = Moshi.Builder().build()
        private val bankAdapter = moshi.adapter(BankImportDto::class.java)

        /**
         * 直接从 JSON 字符串解析（不依赖 Uri / Context），便于在 JVM 单测中复用。
         *
         * @throws ImportFormatException 模板字段缺失或类型不符
         */
        @JvmStatic
        fun parseRaw(rawJson: String): QuestionBank {
            val cleaned = stripUtf8Bom(rawJson)
            val dto = try {
                bankAdapter.fromJson(cleaned)
            } catch (e: JsonDataException) {
                // Moshi 报 "Required value 'type' missing at $.questions[5]" 这种格式
                val detail = buildString {
                    append("Moshi 报错:").appendLine(e.message)
                    appendLine()
                    appendLine("JSON 文件全文(前 500 字符):")
                    appendLine("---")
                    appendLine(cleaned.take(500))
                    if (cleaned.length > 500) appendLine("...(截断)")
                    appendLine("---")
                    appendLine()
                    appendLine("排查建议:")
                    appendLine("1. 检查文件编码:记事本「另存为」选 UTF-8(不要 UTF-8 BOM)")
                    appendLine("2. 确认外层是 { \"bankName\": \"...\", \"questions\": [...] }")
                    appendLine("3. 每道题必须有 type / title / answer 三个字段")
                }
                throw ImportFormatException(
                    "JSON 解析失败:${e.message}",
                    detail = detail,
                    cause = e
                )
            } ?: throw ImportFormatException(
                "JSON 内容为空或结构不匹配",
                detail = "Moshi 解析返回 null,可能根节点不是对象或文件为空。\n前 500 字符:\n${cleaned.take(500)}"
            )

            return validateAndMap(dto)
        }

        /**
         * 剔除 UTF-8 BOM(3 字节 EF BB BF)。
         * 记事本「另存为 → UTF-8」默认带 BOM,会让首字符变成 ﻿ 触发 Moshi 解析错误。
         */
        internal fun stripUtf8Bom(s: String): String =
            if (s.isNotEmpty() && s[0] == '﻿') s.substring(1) else s

        private fun validateAndMap(dto: BankImportDto): QuestionBank {
            if (dto.bankName.isBlank()) {
                throw ImportFormatException(
                    "题库名称 bankName 不能为空",
                    detail = """当前 JSON 结构:
{
  "bankName": "",      ← 此处为空
  "desc": "${dto.desc}",
  "questions": [...]
}"""
                )
            }
            if (dto.questions.isEmpty()) {
                throw ImportFormatException(
                    "题库内至少包含 1 道题",
                    detail = """当前 JSON 结构:
{
  "bankName": "${dto.bankName}",
  "desc": "${dto.desc}",
  "questions": []    ← 数组为空,需要至少 1 道题
}"""
                )
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
                throw ImportFormatException(
                    "第 ${index + 1} 题 title 为空",
                    detail = """题号:${index + 1}(JSON 数组下标 $index)
原始字段:
  type:    "${q.type}"
  title:   ""       ← 此处为空,请填写题干
  options: ${q.options}
  answer:  ${q.answer}
  analysis:${q.analysis ?: "(空)"}"""
                )
            }
            val type = parseType(index, q.type)
            // UNKNOWN 视为填空题(options 可空)
            if (type != QuestionType.JUDGE && type != QuestionType.BLANK && type != QuestionType.PROG && type != QuestionType.UNKNOWN && q.options.isEmpty()) {
                throw ImportFormatException(
                    "第 ${index + 1} 题(${type})缺少选项",
                    detail = """题号:${index + 1}
类型:${type} 需要至少 1 个非空选项
原始字段:
  type:    "${q.type}"
  title:   "${q.title}"
  options: []    ← 此处为空,BLANK/PROG/JUDGE 题除外
  answer:  ${q.answer}"""
                )
            }
            if (q.answer.isEmpty()) {
                throw ImportFormatException(
                    "第 ${index + 1} 题缺少正确答案",
                    detail = """题号:${index + 1}
类型:${type} 的 answer 不能为空
原始字段:
  type:    "${q.type}"
  title:   "${q.title}"
  options: ${q.options}
  answer:  []     ← 此处为空
  answer 格式: 选项题存下标字符串(\"0\"),BLANK/PROG 存原文,JUDGE 用 \"0\" 或 \"1\""""
                )
            }
            val optionIndices = q.options.indices.toList()

            // 填空题 / 编程题 / 未知题型 答案直接用原始字符串，不转下标
            val answerList: List<String> = if (
                type == QuestionType.BLANK || type == QuestionType.PROG || type == QuestionType.UNKNOWN
            ) {
                q.answer.map { it.trim() }
            } else {
                val answerIndices = q.answer.mapNotNull { it.toIntOrNull() }
                if (answerIndices.isEmpty()) {
                    throw ImportFormatException(
                        "第 ${index + 1} 题 answer 必须为整数下标",
                        detail = """题号:${index + 1}
类型:${type} 的 answer 应为下标字符串数组
原始字段:
  type:    "${q.type}"
  options: ${q.options}
  answer:  ${q.answer}    ← 不是整数下标
正确示例: "answer": ["0"] 或 "answer": ["0","2"]"""
                    )
                }
                // 答案下标不能越界（非 JUDGE 题）
                if (type != QuestionType.JUDGE) {
                    answerIndices.forEach { idx ->
                        if (idx !in optionIndices) {
                            throw ImportFormatException(
                                "第 ${index + 1} 题 answer 下标 $idx 越界",
                                detail = """题号:${index + 1}
类型:${type}
原始字段:
  options(共 ${q.options.size} 个,合法下标 0..${q.options.size - 1}):
    ${q.options.mapIndexed { i, opt -> "[$i] $opt" }.joinToString("\n    ")}
  answer:  ${q.answer}    ← 下标 $idx 超出 0..${q.options.size - 1}
修改建议: 把 answer 里的 $idx 改成 0..${q.options.size - 1} 之间的数"""
                            )
                        }
                    }
                }
                // 答案数量校验
                when (type) {
                    QuestionType.SINGLE, QuestionType.DEBUG -> if (answerIndices.size != 1) {
                        throw ImportFormatException(
                            "第 ${index + 1} 题 ${type} 类型答案必须唯一(只 1 个)",
                            detail = """题号:${index + 1}
类型:${type} 只允许 1 个答案
当前 answer: ${q.answer}(共 ${answerIndices.size} 个)
正确示例: "answer": ["0"]"""
                        )
                    }
                    QuestionType.JUDGE -> if (answerIndices.size != 1 || answerIndices.first() !in 0..1) {
                        throw ImportFormatException(
                            "第 ${index + 1} 题 JUDGE 答案必须是 0 或 1",
                            detail = """题号:${index + 1}
类型:JUDGE 的 answer 应为 ["0"](正确)或 ["1"](错误)
当前 answer: ${q.answer}
注意:JUDGE 的 options 字段可为空(系统自动展示"正确/错误")"""
                        )
                    }
                    QuestionType.MULTI, QuestionType.BLANK, QuestionType.PROG -> Unit
                    else -> {}
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
                analysis = q.analysis?.trim().takeUnless { it.isNullOrEmpty() },
                codeSnippet = q.codeSnippet?.trim()?.takeUnless { it.isNullOrEmpty() }
            )
        }

        private fun parseType(index: Int, raw: String): QuestionType {
            return when (raw.uppercase()) {
                "SINGLE" -> QuestionType.SINGLE
                "MULTI"  -> QuestionType.MULTI
                "JUDGE"  -> QuestionType.JUDGE
                "DEBUG"  -> QuestionType.DEBUG
                "BLANK", "READ" -> QuestionType.BLANK
                "PROG"   -> QuestionType.PROG
                // 未知 type 不再抛异常,落 UNKNOWN(按填空题处理)。
                // 顶部会显示黄色 tag 提示用户。
                else -> QuestionType.UNKNOWN
            }
        }
    }
}

/**
 * 模板解析失败时的业务异常
 * - 与 IO 异常区分，便于上层按类型提示
 * - [detail] 携带调试信息(出错字段值、JSON 片段等),
 *   Snackbar 展示简短 [message],详情 AlertDialog 展示完整 [detail]。
 */
class ImportFormatException(
    message: String,
    val detail: String? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)

package com.local.questionbank.data.datasource

import android.content.Context
import android.net.Uri
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.model.QuestionBank
import com.local.questionbank.domain.model.QuestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source

/**
 * CSV 题库解析器
 *
 * ## CSV 格式约定
 *
 * 必须有表头行,字段名固定:
 * ```
 * type,title,optA,optB,optC,optD,answer,analysis,bankName,desc
 * SINGLE,JDK 编译器是?,java.exe,javac.exe,javap.exe,javaw.exe,1,javac 是编译,Java 基础,第一单元
 * MULTI,Java 数据类型,int,String,bool,double,0;3,int 和 double,Java 基础,第一单元
 * JUDGE,Java 由 Sun 开发,T,F,0,正确,Java 基础,第一单元
 * BLANK,源文件名扩展名是?,,,,,java,见题面,Java 基础,第一单元
 * PROG,写 assert,while(reader.hasNextDouble()){...},,2,示例,使用 assert,Java 基础,第一单元
 * ```
 *
 * - 题型(type):SINGLE / MULTI / JUDGE / DEBUG / BLANK / READ / PROG
 * - 选项(optA..optZ,顺序可选):SINGLE/MULTI/JUDGE/DEBUG 必填
 *   - JUDGE 题固定 T/F 两列:T=正确(0),F=错误(1)
 * - 答案(answer):选项下标字符串;";" 分隔多选(MULTI / 多空)
 *   - JUDGE:0 或 1(也接受 T/F)
 *   - BLANK/PROG:原文(支持 `\n` 转义换行)
 * - 解析(analysis):可空
 * - 题库名(bankName)/描述(desc):任意行可填;从首行读取
 */
class CsvFileParser(
    private val context: Context?
) {

    suspend fun parseFromUri(uri: Uri): QuestionBank = withContext(Dispatchers.IO) {
        val rawText = readText(uri)
        parseRaw(rawText)
    }

    /**
     * 直接从字符串解析(JVM 单测可调用)
     *
     * 自动剔除 UTF-8 BOM(记事本另存为默认带 BOM,会让首列表头带 )
     */
    fun parseRaw(rawText: String): QuestionBank {
        val cleaned = stripUtf8Bom(rawText)
        val rows: List<Map<String, String>> = try {
            csvReader().readAllWithHeader(cleaned)
        } catch (e: Exception) {
            throw ImportFormatException(
                "CSV 解析失败:${e.message ?: e.javaClass.simpleName}。" +
                    "提示:用记事本「另存为」编码选 UTF-8(不要 UTF-8 BOM)",
                detail = "kotlin-csv-jvm 报错:${e.message ?: e.javaClass.simpleName}\n" +
                    "常见原因:\n" +
                    "1. 文件不是 UTF-8 编码(记事本另存为选 UTF-8,不要 UTF-8 BOM)\n" +
                    "2. 字段含逗号或换行,未用双引号包裹\n" +
                    "3. 表头缺失 type 列\n\n" +
                    "前 500 字符:\n${cleaned.take(500)}",
                cause = e
            )
        }

        if (rows.isEmpty()) {
            throw ImportFormatException(
                "CSV 文件不包含任何题目。" +
                    "请确认表头存在且至少 1 行题目数据"
            )
        }

        val firstRow = rows.first()
        val typeKey = firstRow.keys.firstOrNull() ?: ""
        // BOM 残留 / 字段映射失败的常见症状:首行 type 取到空
        // 但 type 字段名又常被 BOM 污染成"type"导致查不到
        if (typeKey.startsWith("") || typeKey != "type") {
            // 尝试兼容:如果是 BOM 残留,stripUtf8Bom 已经处理;这里是其他原因
            // 比如 type 列写错成 Type / 题型 等
            if (firstRow["type"].isNullOrBlank() && rows.first().none { it.key.contains("type", ignoreCase = true) }) {
                val actualKeys = firstRow.keys.joinToString(", ")
                throw ImportFormatException(
                    "未找到表头 type 列",
                    detail = """期望表头第一列: type
实际读取的列名: $actualKeys

完整标准表头:
type,title,optA,optB,optC,optD,answer,analysis,bankName,desc

注意:
1. 第一行必须是表头(英文字段名),第二行起才是题目
2. type 必须小写,值见合法列表
3. CSV 文件需用 UTF-8 编码(不要 UTF-8 BOM)"""
                )
            }
        }

        val questions = rows.mapIndexed { idx, row -> toQuestion(idx, row) }

        val bankName = rows.first()["bankName"]?.trim()?.takeIf { it.isNotBlank() }
            ?: "CSV 导入"
        val desc = rows.first()["desc"]?.trim()?.takeIf { it.isNotBlank() }

        return QuestionBank(
            id = 0L,
            name = bankName,
            description = desc,
            questions = questions
        )
    }

    private fun toQuestion(index: Int, row: Map<String, String>): Question {
        val rawType = (row["type"] ?: "").trim().uppercase()
        val title = (row["title"] ?: "").trim()
        val analysis = row["analysis"]?.trim()?.takeIf { it.isNotBlank() }
        // code 列:可选,空列/缺失列视为无代码
        val codeSnippet = row["code"]?.trim()?.takeIf { it.isNotBlank() }
        val rowDisplay = formatRowForDisplay(row)

        if (title.isEmpty()) {
            throw ImportFormatException(
                "第 ${index + 1} 行 title 为空",
                detail = """行号:${index + 1}(第 ${index + 1} 行数据,从第 2 行算起)
原始行内容:
  $rowDisplay

期望字段: type,title,optA..optD,answer,analysis,bankName,desc
当前字段值:
  type:    "${row["type"] ?: ""}"
  title:   ""      ← 此处为空,请填写题干
  answer:  "${row["answer"] ?: ""}""""
            )
        }

        val type = when (rawType) {
            "SINGLE" -> QuestionType.SINGLE
            "MULTI" -> QuestionType.MULTI
            "JUDGE" -> QuestionType.JUDGE
            "DEBUG" -> QuestionType.DEBUG
            "BLANK", "READ" -> QuestionType.BLANK
            "PROG" -> QuestionType.PROG
            // 未知 type 不再抛异常,落 UNKNOWN(按填空题处理)
            else -> QuestionType.UNKNOWN
        }

        val options = OPTION_KEYS.mapNotNull { key ->
            row[key]?.trim()?.takeIf { it.isNotEmpty() }
        }

        if (type != QuestionType.BLANK && type != QuestionType.PROG && type != QuestionType.UNKNOWN) {
            if (options.isEmpty()) {
                throw ImportFormatException(
                    "第 ${index + 1} 行(${type})缺少选项",
                    detail = """行号:${index + 1}
类型:${type} 至少需要 1 个非空选项(optA..optD)
当前选项列均为空(读取到 ${options.size} 个非空选项)

原始行内容:
  $rowDisplay

注意:BLANK/PROG/JUDGE 题可以没有选项(其他题型必须有)"""
                )
            }
        }

        val rawAnswer = (row["answer"] ?: "").trim()
        if (rawAnswer.isEmpty()) {
            throw ImportFormatException(
                "第 ${index + 1} 行缺少 answer",
                detail = """行号:${index + 1}
类型:${type} 的 answer 列不能为空
原始行内容:
  $rowDisplay

answer 格式示例:
  SINGLE/DEBUG: 0
  MULTI:        0;2(分号分隔多个下标)
  JUDGE:        0(正确)或 1(错误)
  BLANK/PROG:   原文"""
            )
        }

        val answer: List<String> = when (type) {
            QuestionType.JUDGE -> listOf(judgeAnswer(index, rawAnswer, row))
            QuestionType.BLANK, QuestionType.PROG, QuestionType.UNKNOWN -> listOf(unescape(rawAnswer))
            else -> rawAnswer.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        }

        // 答案下标越界校验
        if (type != QuestionType.JUDGE && type != QuestionType.BLANK && type != QuestionType.PROG && type != QuestionType.UNKNOWN) {
            val optionIndices = options.indices.toList()
            answer.forEach { idx ->
                val n = idx.toIntOrNull()
                if (n == null || n !in optionIndices) {
                    throw ImportFormatException(
                        "第 ${index + 1} 行答案下标 $idx 越界",
                        detail = """行号:${index + 1}
类型:${type}
选项(共 ${options.size} 个,合法下标 0..${options.size - 1}):
  ${options.mapIndexed { i, opt -> "[$i] $opt" }.joinToString("\n  ")}
原始 answer: "${row["answer"]}"
问题答案: "$idx"    ← 不是合法下标(应改 0..${options.size - 1})

原始行内容:
  $rowDisplay"""
                    )
                }
            }
            when (type) {
                QuestionType.SINGLE, QuestionType.DEBUG -> if (answer.size != 1) {
                    throw ImportFormatException(
                        "第 ${index + 1} 行 SINGLE/DEBUG 必须唯一答案",
                        detail = """行号:${index + 1}
类型:${type} 只允许 1 个答案
当前 answer: "${row["answer"]}"(解析出 ${answer.size} 个)
正确示例: answer 列写 0
原始行内容:
  $rowDisplay"""
                    )
                }
                else -> { /* MULTI 允许多个 */ }
            }
        }

        return Question(
            id = 0L,
            bankId = 0L,
            type = type,
            title = title,
            options = options,
            answer = answer,
            analysis = analysis,
            codeSnippet = codeSnippet
        )
    }

    private fun judgeAnswer(
        index: Int,
        raw: String,
        @Suppress("UNUSED_PARAMETER") row: Map<String, String>
    ): String {
        return when (raw.uppercase()) {
            "T", "TRUE", "0" -> "0"
            "F", "FALSE", "1" -> "1"
            else -> throw ImportFormatException(
                "第 ${index + 1} 行 JUDGE 答案非法:$raw",
                detail = """行号:${index + 1}
类型:JUDGE 行的 answer 必须用以下值之一(大小写不敏感):
  T  / TRUE  / 0  → 正确答案
  F  / FALSE / 1  → 错误答案

原始 answer: "$raw"
原始行内容:
  ${formatRowForDisplay(row)}"""
            )
        }
    }

    /** 把当前解析行 Map 格式化成可读的 key=value 串,用于错误 detail */
    private fun formatRowForDisplay(row: Map<String, String>): String {
        return row.entries.joinToString("\n  ") { "${it.key} = \"${it.value}\"" }
    }

    /**
     * 解析 CSV 中的转义:
     * - \n → 换行
     * - \\ → 反斜杠
     * - \, → 逗号(避免 CSV 解析时与列分隔冲突)
     */
    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    ',' -> { sb.append(','); i += 2 }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    private fun readText(uri: Uri): String {
        val ctx = context
            ?: throw ImportFormatException("CsvFileParser 未初始化 Context")
        val resolver = ctx.contentResolver
        val input = resolver.openInputStream(uri)
            ?: throw ImportFormatException("无法打开文件流,Uri=$uri")
        return input.use { stream ->
            stream.source().buffer().use { src -> src.readUtf8() }
        }
    }

    companion object {
        /** 识别列名:支持最多 26 列(optA..optZ);多于 26 列暂不实现 */
        private val OPTION_KEYS = ('A'..'Z').map { "opt$it" }

        /**
         * 剔除 UTF-8 BOM(3 字节 EF BB BF)。
         * 记事本「另存为 → UTF-8」默认带 BOM,会让首列表头变成 "type"
         * 而 kotlin-csv-jvm 不识别 BOM。
         */
        internal fun stripUtf8Bom(s: String): String =
            if (s.isNotEmpty() && s[0] == '﻿') s.substring(1) else s
    }
}
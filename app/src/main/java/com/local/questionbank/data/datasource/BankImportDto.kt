package com.local.questionbank.data.datasource

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 题库导入 JSON 的顶层结构（严格对应你给出的模板）
 *
 * 命名说明：
 *  - DTO 与领域模型分离，避免污染领域层
 *  - 所有字段均提供默认值，使部分缺字段的旧文件也能被宽容解析
 */
@JsonClass(generateAdapter = true)
data class BankImportDto(
    @Json(name = "bankName") val bankName: String = "",
    @Json(name = "desc")     val desc: String? = null,
    @Json(name = "questions") val questions: List<QuestionImportDto> = emptyList()
)

/**
 * 单题导入结构
 *
 * - [type]  支持 SINGLE / MULTI / JUDGE / DEBUG / BLANK / READ / PROG（解析器会做严格校验）
 * - [options] 对 SINGLE/MULTI 必填，对 JUDGE/BLANK/PROG 可为空
 * - [answer] 字符串数组，元素是选项下标（"0"、"1" …），JUDGE 时为 "0" 或 "1"
 * - [codeSnippet] 题面附加代码片段(可空)。Moshi 缺字段时默认 null,老 JSON 兼容
 */
@JsonClass(generateAdapter = true)
data class QuestionImportDto(
    @Json(name = "type")        val type: String = "SINGLE",
    @Json(name = "title")       val title: String = "",
    @Json(name = "options")     val options: List<String> = emptyList(),
    @Json(name = "answer")      val answer: List<String> = emptyList(),
    @Json(name = "analysis")    val analysis: String? = null,
    @Json(name = "codeSnippet") val codeSnippet: String? = null
)

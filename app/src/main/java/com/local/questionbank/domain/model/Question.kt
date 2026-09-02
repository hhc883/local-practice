package com.local.questionbank.domain.model

/**
 * 领域层 - 题目
 *
 * 设计说明：
 *  - [options] 保持原始顺序，UI 直接 `options.forEachIndexed` 渲染
 *  - [answer] 是选项下标（0-based），比字符串比较更稳；判题时与用户选择下标集合做集合比较
 *  - SINGLE 时 [answer] 必有 1 项；JUDGE 时 [answer] 必有 1 项且只能是 0/1
 */
data class Question(
    val id: Long = 0L,
    val bankId: Long = 0L,
    val type: QuestionType,
    val title: String,
    val options: List<String>,
    val answer: List<String>,  // 选项题存下标字符串["0"]，填空题存答案字符串["Speak.java"]
    val analysis: String? = null,
    /**
     * 题面附加代码片段(等宽字体展示在题干下方)。
     * 例如:"以下程序输出什么?"题型的代码示例。
     * null 或空串 → 不渲染代码块。
     */
    val codeSnippet: String? = null
) {
    /** 是否支持多选 */
    val isMulti: Boolean get() = type == QuestionType.MULTI
}

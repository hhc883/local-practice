package com.local.questionbank.domain.model

/**
 * 题目类型
 *
 * - 字符串值与 Room Entity 中 [com.local.questionbank.data.database.entity.QuestionEntity.type] 保持一致
 * - 解析阶段已统一转大写，这里直接判等即可
 */
enum class QuestionType {
    SINGLE,   // 单选
    MULTI,    // 多选
    JUDGE,    // 判断
    DEBUG,    // 挑错题
    BLANK;    // 填空题

    companion object {
        fun fromRaw(raw: String): QuestionType =
            entries.firstOrNull { it.name == raw.uppercase() }
                ?: throw IllegalArgumentException("未知题型: $raw")
    }
}

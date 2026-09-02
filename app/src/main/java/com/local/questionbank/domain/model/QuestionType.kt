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
    BLANK,    // 填空题
    PROG,     // 编程题
    /**
     * 未知题型(解析兜底):导入 JSON/CSV 时遇到不识别的 type 字符串,
     * 自动归类为 UNKNOWN,按填空题(trim + equalsIgnoreCase)处理。
     * 仅作解析容错用,不由用户在 JSON 里主动写明。
     */
    UNKNOWN;

    companion object {
        /**
         * 从字符串解析题型。匹配不上时返回 [UNKNOWN] 而非抛异常,
         * 保证旧 DB 里如果存了不支持的字符串(如"FOO")也能继续工作。
         */
        fun fromRaw(raw: String): QuestionType =
            entries.firstOrNull { it.name == raw.uppercase() } ?: UNKNOWN
    }
}

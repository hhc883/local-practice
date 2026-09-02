package com.local.questionbank.domain.model

/**
 * AI 调用所需的 prompt 工厂
 *
 * 集中维护 system prompt 的措辞,纯函数无副作用,便于单测覆盖。
 *
 * 注:
 *  - 智谱 GLM-4.7-Flash **不支持** response_format 字段
 *  - 所有提示词都强制要求 AI 返回纯 JSON 文本(不包 Markdown 围栏)
 *  - "fix JSON" 与 "生成新题" 两个场景独立维护,避免 prompt 串扰
 */
data class AiPrompt(
    val systemPrompt: String,
    val userPrompt: String
) {
    companion object {

        /**
         * JSON 修复场景
         */
        fun forJsonFix(rawJson: String, errorMessage: String): AiPrompt {
            val system = """
                你是题库 JSON 修复助手。用户会给出有问题的 JSON 和错误描述。
                请仔细分析错误,在保留原题库结构、字段含义、题目主旨的前提下,输出可被
                JsonFileParser 直接解析的合法 JSON(单选/多选/判断/挑错/填空/编程 6 种题型)。
                严格约束:
                1. 仅返回纯 JSON 文本,**不要**使用 Markdown 代码块包裹(**不要** ```json 等前后缀)
                2. 保持 bankName / desc 不变;questions 数组长度不变
                3. type 必须是 SINGLE / MULTI / JUDGE / DEBUG / BLANK / PROG 之一
                4. answer 是字符串数组:选项题存选项下标字符串(0-based);
                   BLANK/PROG 存原文;JUDGE 用 "0"(正确)或 "1"(错误)
                5. options 与 answer 下标必须对应且不越界
                6. **不要**输出任何解释、注释或多余文字
            """.trimIndent()

            val user = """
                以下 JSON 导入失败:
                错误描述:${errorMessage}

                原 JSON:
                $rawJson

                请输出修复后的合法 JSON(纯文本,无 Markdown 包裹):
            """.trimIndent()

            return AiPrompt(system, user)
        }

        /**
         * AI 出题场景:基于当前题生成同知识点新题
         */
        fun forSimilarQuestion(current: Question): AiPrompt {
            val system = """
                你是出题助手。基于用户给出的题目,生成 1 道同知识点的新题。
                严格约束:
                1. 仅返回纯 JSON 对象(单个 Question),**不要**使用 Markdown 代码块包裹(**不要** ```json 等前后缀)
                2. 题型必须与原题一致
                3. options 与 answer 下标必须对应且不越界
                4. 不允许答案与原题一模一样,要出不同角度的题
                5. answer 格式:选项题存下标字符串数组;BLANK/PROG 存原文;JUDGE 用 "0"/"1"
                6. **不要**输出任何解释、注释或多余文字
                7. 如果新题包含代码片段(读代码才能答),把代码放在 codeSnippet 字段;
                   纯文字题无需此字段。codeSnippet 内的换行请写为 \n,系统会自动转为真实换行。
            """.trimIndent()

            val user = buildString {
                appendLine("原题(供参考):")
                appendLine("{")
                appendLine("  \"type\": \"${current.type.name}\",")
                appendLine("  \"title\": \"${current.title.escape()}\",")
                if (current.options.isNotEmpty()) {
                    appendLine("  \"options\": ${formatJsonArray(current.options)},")
                }
                appendLine("  \"answer\": ${formatJsonArray(current.answer)},")
                if (!current.analysis.isNullOrBlank()) {
                    appendLine("  \"analysis\": \"${current.analysis.escape()}\"")
                }
                appendLine("}")
                appendLine()
                appendLine("请基于上述题目,生成 1 道同知识点的新题(纯 JSON 文本,无 Markdown 包裹):")
            }

            return AiPrompt(system, user)
        }

        private fun formatJsonArray(items: List<String>): String =
            items.joinToString(prefix = "[", postfix = "]") { "\"${it.escape()}\"" }

        private fun String.escape(): String =
            replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    }
}
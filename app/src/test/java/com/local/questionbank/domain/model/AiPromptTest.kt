package com.local.questionbank.domain.model

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AiPrompt 单测
 *
 * 验证 prompt 拼接:
 *  - forJsonFix 必须包含原始 JSON 和错误描述
 *  - forSimilarQuestion 必须包含当前题的 type/title/options
 */
class AiPromptTest {

    @Test
    fun `forJsonFix includes raw json and error message`() {
        val raw = """{"bankName":"test","questions":[]}"""
        val err = "第 1 题答案下标 3 越界"
        val prompt = AiPrompt.forJsonFix(raw, err)

        assertTrue("system 提示包含 JSON 修复意图",
            prompt.systemPrompt.contains("修复") || prompt.systemPrompt.contains("JSON"))
        assertTrue("user 提示含原始 JSON", prompt.userPrompt.contains(raw))
        assertTrue("user 提示含错误描述", prompt.userPrompt.contains(err))
        assertTrue("user 提示含 bankName 字段说明",
            prompt.systemPrompt.contains("bankName"))
    }

    @Test
    fun `forSimilarQuestion embeds current question type and title`() {
        val current = Question(
            id = 100,
            bankId = 1,
            type = QuestionType.MULTI,
            title = "下列哪些是 Java 的基本类型?",
            options = listOf("int", "String", "bool", "double"),
            answer = listOf("0", "3"),
            analysis = "Java 基本类型包括 int、double"
        )
        val prompt = AiPrompt.forSimilarQuestion(current)

        assertTrue("system 提示包含出题意图",
            prompt.systemPrompt.contains("出题") || prompt.systemPrompt.contains("生成"))
        assertTrue("user 提示含题型 MULTI", prompt.userPrompt.contains("MULTI"))
        assertTrue("user 提示含原题 title",
            prompt.userPrompt.contains("Java 的基本类型"))
        assertTrue("user 提示含 options 数组", prompt.userPrompt.contains("int"))
        assertTrue("user 提示含答案", prompt.userPrompt.contains("\"0\""))
    }

    @Test
    fun `forSimilarQuestion escapes newlines in analysis`() {
        val current = Question(
            id = 1,
            bankId = 1,
            type = QuestionType.SINGLE,
            title = "test",
            options = listOf("a", "b"),
            answer = listOf("0"),
            analysis = "line1\nline2"
        )
        val prompt = AiPrompt.forSimilarQuestion(current)
        // 真实换行应被转义成 \\n(以保证生成的 JSON 合法)
        assertTrue("analysis 换行被转义", prompt.userPrompt.contains("line1\\nline2"))
    }
}
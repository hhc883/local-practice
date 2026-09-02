package com.local.questionbank.data.datasource

import com.local.questionbank.domain.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * JsonFileParser 解析测试
 *
 * 当前阶段只覆盖 [samples/XT7.json]（包含 `PROG` 编程题）的端到端解析：
 * - 整个题库可被成功解析
 * - 至少存在一道 PROG 题，answer 保留为带换行的代码原文
 *
 * 注：JsonFileParser.parseRaw() 是 companion 纯函数入口，不依赖 Android Context。
 */
class JsonFileParserTest {

    @Test
    fun `XT7 sample parses successfully and contains a PROG question`() {
        val json = readSample("XT7.json")
        val bank = JsonFileParser.parseRaw(json)

        assertEquals("Java 程序设计基础 · 习题 7", bank.name)

        val prog = bank.questions.firstOrNull { it.type == QuestionType.PROG }
        assertNotNull("样例中应当至少包含一道 PROG 题", prog)
        prog!!
        assertTrue("PROG 题 options 必须为空", prog.options.isEmpty())
        assertEquals("PROG 题 answer 必须为单段代码", 1, prog.answer.size)
        assertTrue(
            "PROG 答案应保留换行（\\n → 真实换行）",
            prog.answer.first().contains("assert")
        )
    }

    @Test
    fun `PROG question allows empty options without raising`() {
        val json = """
            {
              "bankName": "编程题单测",
              "questions": [
                {
                  "type": "PROG",
                  "title": "示例",
                  "options": [],
                  "answer": ["int x = 1;"],
                  "analysis": ""
                }
              ]
            }
        """.trimIndent()
        val bank = JsonFileParser.parseRaw(json)

        assertEquals(1, bank.questions.size)
        val q = bank.questions.single()
        assertEquals(QuestionType.PROG, q.type)
        assertEquals(listOf("int x = 1;"), q.answer)
    }

    @Test
    fun `unknown type still rejected`() {
        val json = """
            {
              "bankName": "非法类型",
              "questions": [
                {
                  "type": "FOO",
                  "title": "示例",
                  "options": ["A", "B"],
                  "answer": ["0"]
                }
              ]
            }
        """.trimIndent()
        try {
            JsonFileParser.parseRaw(json)
            fail("应抛出 ImportFormatException")
        } catch (e: ImportFormatException) {
            assertTrue(e.message!!.contains("题型非法"))
        }
    }

    private fun readSample(name: String): String {
        val resource = JsonFileParserTest::class.java.classLoader!!.getResource("samples/$name")
            ?: error("样例资源缺失: samples/$name")
        return resource.readText(Charsets.UTF_8)
    }
}

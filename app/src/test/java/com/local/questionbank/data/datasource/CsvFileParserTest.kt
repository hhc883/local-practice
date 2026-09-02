package com.local.questionbank.data.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * CsvFileParser JVM 单测
 *
 * 不依赖 Android Context,直接调用 parseRaw。
 *
 * 注:Context 参数在 parseRaw 路径中不被访问,传 null 即可
 */
class CsvFileParserTest {

    /** parseRaw 不走 IO,Context 不被访问;传 null 占位 */
    private fun mockContext(): android.content.Context? = null

    @Test
    fun `parses SINGLE and MULTI rows correctly`() {
        val csv = """
            type,title,optA,optB,optC,optD,answer,analysis,bankName,desc
            SINGLE,JDK 编译器?,java.exe,javac.exe,javap.exe,javaw.exe,1,javac 是编译,Java 基础,入门
            MULTI,Java 类型,int,String,bool,double,0;3,int 和 double,Java 基础,入门
        """.trimIndent()

        val parser = CsvFileParser(mockContext())
        val bank = parser.parseRaw(csv)

        assertEquals("Java 基础", bank.name)
        assertEquals("入门", bank.description)
        assertEquals(2, bank.questions.size)

        val q1 = bank.questions[0]
        assertEquals(com.local.questionbank.domain.model.QuestionType.SINGLE, q1.type)
        assertEquals("JDK 编译器?", q1.title)
        assertEquals(listOf("java.exe", "javac.exe", "javap.exe", "javaw.exe"), q1.options)
        assertEquals(listOf("1"), q1.answer)
        assertEquals("javac 是编译", q1.analysis)

        val q2 = bank.questions[1]
        assertEquals(com.local.questionbank.domain.model.QuestionType.MULTI, q2.type)
        assertEquals(listOf("0", "3"), q2.answer)
    }

    @Test
    fun `parses JUDGE with T F`() {
        val csv = """
            type,title,optA,optB,answer
            JUDGE,Java 由 Sun 开发,T,F,0
        """.trimIndent()

        val parser = CsvFileParser(mockContext())
        val bank = parser.parseRaw(csv)
        val q = bank.questions.single()
        assertEquals(listOf("0"), q.answer)
        assertEquals(listOf("T", "F"), q.options)
    }

    @Test
    fun `parses BLANK and PROG with no options`() {
        val csv = """
            type,title,optA,optB,answer,analysis
            BLANK,扩展名?,,,java,见题面
            PROG,写 assert,,,while(reader.hasNextDouble()){\n  assert true;\n},示例代码
        """.trimIndent()

        val parser = CsvFileParser(mockContext())
        val bank = parser.parseRaw(csv)

        assertEquals(2, bank.questions.size)
        val blank = bank.questions[0]
        assertEquals(com.local.questionbank.domain.model.QuestionType.BLANK, blank.type)
        assertEquals(emptyList<String>(), blank.options)
        assertEquals(listOf("java"), blank.answer)

        val prog = bank.questions[1]
        assertEquals(com.local.questionbank.domain.model.QuestionType.PROG, prog.type)
        // \n → 真实换行
        assertTrue(prog.answer.first().contains("\n"))
    }

    @Test
    fun `parses README and desc from header row only`() {
        val csv = """
            type,title,optA,optB,optC,optD,answer,bankName,desc
            SINGLE,Q1,a,b,c,d,1,我的题库,描述
            SINGLE,Q2,a,b,c,d,0,我的题库,描述
        """.trimIndent()

        val parser = CsvFileParser(mockContext())
        val bank = parser.parseRaw(csv)
        assertEquals("我的题库", bank.name)
        assertEquals("描述", bank.description)
    }

    @Test
    fun `rejects unknown type`() {
        val csv = """
            type,title,optA,optB,answer
            FOO,bad row,a,b,0
        """.trimIndent()

        try {
            CsvFileParser(mockContext()).parseRaw(csv)
            fail("应抛 ImportFormatException")
        } catch (e: ImportFormatException) {
            assertTrue("应提示 type 非法", e.message!!.contains("type 非法"))
            assertTrue("应包含 FOO", e.message!!.contains("FOO"))
        }
    }

    @Test
    fun `rejects out-of-range answer index`() {
        val csv = """
            type,title,optA,optB,answer
            SINGLE,Q1,a,b,5
        """.trimIndent()

        try {
            CsvFileParser(mockContext()).parseRaw(csv)
            fail("应抛 ImportFormatException")
        } catch (e: ImportFormatException) {
            assertTrue("应提示越界", e.message!!.contains("越界"))
        }
    }

    @Test
    fun `rejects SINGLE with multiple answers`() {
        val csv = """
            type,title,optA,optB,answer
            SINGLE,Q1,a,b,0;1
        """.trimIndent()

        try {
            CsvFileParser(mockContext()).parseRaw(csv)
            fail("应抛 ImportFormatException")
        } catch (e: ImportFormatException) {
            assertTrue("应提示唯一答案", e.message!!.contains("唯一答案"))
        }
    }

    @Test
    fun `rejects blank title`() {
        val csv = """
            type,title,optA,optB,answer
            SINGLE,,a,b,0
        """.trimIndent()

        try {
            CsvFileParser(mockContext()).parseRaw(csv)
            fail("应抛 ImportFormatException")
        } catch (e: ImportFormatException) {
            assertTrue("应提示 title 为空", e.message!!.contains("title 为空"))
        }
    }

    @Test
    fun `rejects empty csv`() {
        try {
            CsvFileParser(mockContext()).parseRaw("type,title,optA,optB,answer\n")
            fail("应抛 ImportFormatException")
        } catch (e: ImportFormatException) {
            assertTrue(e.message!!.contains("不包含任何题目"))
        }
    }

    @Test
    fun `accepts extra columns silently`() {
        val csv = """
            type,title,optA,optB,optC,optD,answer,analysis,extra1,extra2
            SINGLE,Q1,a,b,c,d,1,ans,X,Y
        """.trimIndent()
        val bank = CsvFileParser(mockContext()).parseRaw(csv)
        assertEquals(1, bank.questions.size)
    }

    @Test
    fun `parses DEBUG like SINGLE`() {
        val csv = """
            type,title,optA,optB,optC,optD,answer
            DEBUG,挑错题,a,b,c,d,2
        """.trimIndent()
        val bank = CsvFileParser(mockContext()).parseRaw(csv)
        val q = bank.questions.single()
        assertEquals(com.local.questionbank.domain.model.QuestionType.DEBUG, q.type)
        assertEquals(listOf("2"), q.answer)
    }
}
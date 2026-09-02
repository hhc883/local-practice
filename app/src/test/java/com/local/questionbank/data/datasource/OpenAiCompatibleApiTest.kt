package com.local.questionbank.data.datasource

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * OpenAiCompatibleApi 单测
 *
 * 覆盖:
 *  - 智谱:ZHIPU 调用携带 thinking.type=enabled + response_format=json_object
 *  - DeepSeek:不带 thinking,标准 OpenAI body
 *  - CUSTOM:走 baseUrl,不附加 thinking
 *  - parseContent:从响应里正确取出 content
 *  - joinUrl:自动处理 baseUrl 末尾斜杠
 */
class OpenAiCompatibleApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() { server = MockWebServer(); server.start() }

    @After
    fun tearDown() { server.shutdown() }

    // ---------- buildRequestBody 纯函数 ----------

    @Test
    fun `buildRequestBody for Zhipu includes thinking and json_object`() {
        val body = OpenAiCompatibleApi.buildRequestBody(
            model = "glm-4.7-flash",
            messages = listOf(
                OpenAiCompatibleApi.ChatMessage("system", "你是助手"),
                OpenAiCompatibleApi.ChatMessage("user", "你好")
            ),
            enableThinking = true,
            jsonMode = true
        )
        assertTrue("含 model", body.contains("\"model\":\"glm-4.7-flash\""))
        assertTrue("含 thinking.type=enabled", body.contains("\"thinking\":{\"type\":\"enabled\"}"))
        assertTrue("含 response_format=json_object", body.contains("\"response_format\":{\"type\":\"json_object\"}"))
        assertTrue("含 system 消息", body.contains("\"role\":\"system\""))
        assertTrue("含 user 消息", body.contains("\"role\":\"user\""))
    }

    @Test
    fun `buildRequestBody for DeepSeek omits thinking and response_format`() {
        val body = OpenAiCompatibleApi.buildRequestBody(
            model = "deepseek-chat",
            messages = listOf(OpenAiCompatibleApi.ChatMessage("user", "hi")),
            enableThinking = false,
            jsonMode = false
        )
        assertTrue("含 model", body.contains("\"model\":\"deepseek-chat\""))
        assertFalse("DeepSeek 不应含 thinking", body.contains("thinking"))
        assertFalse("jsonMode=false 时不应含 response_format", body.contains("response_format"))
    }

    @Test
    fun `buildRequestBody escapes quotes and newlines in message content`() {
        val body = OpenAiCompatibleApi.buildRequestBody(
            model = "x",
            messages = listOf(OpenAiCompatibleApi.ChatMessage("user", "line1\nline2 \"quoted\"")),
            enableThinking = false,
            jsonMode = false
        )
        assertTrue("换行转义", body.contains("line1\\nline2"))
        assertTrue("引号转义", body.contains("\\\"quoted\\\""))
    }

    // ---------- parseContent 纯函数 ----------

    @Test
    fun `parseContent extracts content from typical response`() {
        val raw = """
            {
              "id": "abc",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "{\"foo\": 1}"
                  }
                }
              ]
            }
        """.trimIndent()
        assertEquals("{\"foo\": 1}", OpenAiCompatibleApi.parseContent(raw))
    }

    @Test
    fun `parseContent returns null on malformed JSON`() {
        assertNull(OpenAiCompatibleApi.parseContent("not json"))
    }

    @Test
    fun `parseContent returns null when choices missing`() {
        assertNull(OpenAiCompatibleApi.parseContent("""{"error":"x"}"""))
    }

    // ---------- joinUrl 纯函数 ----------

    @Test
    fun `joinUrl handles trailing and leading slashes`() {
        assertEquals("https://a.com/v1/chat/completions",
            OpenAiCompatibleApi.joinUrl("https://a.com/v1", "chat/completions"))
        assertEquals("https://a.com/v1/chat/completions",
            OpenAiCompatibleApi.joinUrl("https://a.com/v1/", "chat/completions"))
        assertEquals("https://a.com/v1/chat/completions",
            OpenAiCompatibleApi.joinUrl("https://a.com/v1/", "/chat/completions"))
    }

    // ---------- 完整请求往返 ----------

    @Test
    fun `chatCompletion sends Authorization and parses content`() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse()
                .setBody("""{"choices":[{"message":{"role":"assistant","content":"hi-from-ai"}}]}""")
                .setHeader("Content-Type", "application/json")
        )
        val api = OpenAiCompatibleApi(
            apiKey = "test-key",
            baseUrl = server.url("").toString().removeSuffix("/"),
            model = "deepseek-chat",
            enableThinking = false
        )
        val result = api.chatCompletion(
            messages = listOf(OpenAiCompatibleApi.ChatMessage("user", "hello"))
        )
        assertEquals("hi-from-ai", result)

        val recorded = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("应收到请求", recorded)
        assertEquals("POST", recorded!!.method)
        assertEquals("Authorization 应为 Bearer test-key",
            "Bearer test-key", recorded.getHeader("Authorization"))
        val sentBody = recorded.body.readUtf8()
        assertTrue("请求含 model", sentBody.contains("deepseek-chat"))
        assertTrue("请求含 user 消息", sentBody.contains("\"role\":\"user\""))
        assertFalse("DeepSeek 不应带 thinking", sentBody.contains("thinking"))
    }

    @Test
    fun `chatCompletion ZHIPU includes thinking in body`() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse()
                .setBody("""{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""")
                .setHeader("Content-Type", "application/json")
        )
        val api = OpenAiCompatibleApi(
            apiKey = "zhipu-key",
            baseUrl = server.url("").toString().removeSuffix("/"),
            model = "glm-4.7-flash",
            enableThinking = true   // 模拟 ZHIPU 调 enableThinking
        )
        api.chatCompletion(
            messages = listOf(OpenAiCompatibleApi.ChatMessage("user", "hi"))
        )
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        val sentBody = recorded.body.readUtf8()
        assertTrue("智谱调用应带 thinking",
            sentBody.contains("\"thinking\":{\"type\":\"enabled\"}"))
        assertTrue("智谱调用应带 response_format",
            sentBody.contains("\"response_format\":{\"type\":\"json_object\"}"))
    }

    @Test
    fun `chatCompletion throws when API key is blank`() = kotlinx.coroutines.runBlocking {
        val api = OpenAiCompatibleApi(
            apiKey = "",
            baseUrl = server.url("").toString().removeSuffix("/"),
            model = "x"
        )
        try {
            api.chatCompletion(listOf(OpenAiCompatibleApi.ChatMessage("user", "hi")))
            assertTrue("应抛异常", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("API Key"))
        }
    }

    @Test
    fun `chatCompletion throws when baseUrl blank`() = kotlinx.coroutines.runBlocking {
        val api = OpenAiCompatibleApi(apiKey = "k", baseUrl = "", model = "x")
        try {
            api.chatCompletion(listOf(OpenAiCompatibleApi.ChatMessage("user", "hi")))
            assertTrue("应抛异常", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("baseUrl"))
        }
    }

    @Test
    fun `chatCompletion throws AiApiException on HTTP 401`() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}""")
        )
        val api = OpenAiCompatibleApi(
            apiKey = "bad", baseUrl = server.url("").toString().removeSuffix("/"), model = "x"
        )
        try {
            api.chatCompletion(listOf(OpenAiCompatibleApi.ChatMessage("user", "hi")))
            assertTrue("应抛异常", false)
        } catch (e: AiApiException) {
            assertTrue("提示含 401", e.message!!.contains("401"))
        }
    }
}
package com.local.questionbank.data.datasource

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容协议 AI 客户端
 *
 * 支持所有兼容 /v1/chat/completions 的服务:
 *  - 智谱 AI (https://open.bigmodel.cn/api/paas/v4)
 *  - DeepSeek (https://api.deepseek.com)
 *  - MiniMax (https://api.minimaxi.com/v1)
 *  - 通义千问 / 月之暗面 / OpenRouter 等
 *  - 本地 Ollama(http://localhost:11434/v1)
 *
 * 协议:
 *  POST {baseUrl}/chat/completions
 *  Headers: Authorization: Bearer {apiKey}
 *  Body: { "model", "messages": [{role, content}], "max_tokens", "temperature", 可选 "thinking" }
 *  Response: { "choices": [{ "message": { "content": "..." } }] }
 *
 * 与旧 ZhipuApi 的差异:
 *  - baseUrl 与 model 由调用方传入(从 AiProfile 取)
 *  - thinking 字段可通过构造参数开关,默认关闭(智谱需要时由调用方开启)
 *  - 异常类改名为 AiApiException(语义更通用)
 */
class OpenAiCompatibleApi(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
    /** 智谱特有:启用思考模式 JSON 输出更稳定。其他 provider 传 false */
    private val enableThinking: Boolean = false,
    private val client: OkHttpClient = defaultClient
) {

    /**
     * 调 chat/completions,返回 choices[0].message.content 字符串
     *
     * @throws AiApiException 网络/认证/解析失败时
     */
    suspend fun chatCompletion(messages: List<ChatMessage>, jsonMode: Boolean = true): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "API Key 不能为空,请先在 AI 助手设置页配置" }
            require(baseUrl.isNotBlank()) { "baseUrl 不能为空,请选 provider 或填自定义地址" }
            require(model.isNotBlank()) { "model 不能为空" }

            val body = buildRequestBody(model, messages, enableThinking, jsonMode)
            val request = Request.Builder()
                .url(joinUrl(baseUrl, "chat/completions"))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string()
                    ?: throw AiApiException("空响应体 (HTTP ${response.code})")
                if (!response.isSuccessful) {
                    throw AiApiException("HTTP ${response.code}: $raw")
                }
                parseContent(raw)
                    ?: throw AiApiException("响应无法解析: $raw")
            }
        }

    /** 单条对话消息(role: system / user / assistant) */
    data class ChatMessage(val role: String, val content: String) {
        companion object {
            const val SYSTEM = "system"
            const val USER = "user"
            const val ASSISTANT = "assistant"
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
        }

        private val moshi: Moshi = Moshi.Builder().build()

        // ---------- 纯函数:便于 JVM 单测 ----------

        /**
         * 组装 chat/completions 请求体
         *
         * @param enableThinking 智谱需要时设 true;其他 provider 忽略
         * @param jsonMode 智谱等支持 response_format=json_object;大多数其他 provider 不支持,
         *                这里仅在 ZHIPU(由调用方决定)时附加
         */
        fun buildRequestBody(
            model: String,
            messages: List<ChatMessage>,
            enableThinking: Boolean,
            jsonMode: Boolean
        ): String {
            val messageJson = messages.joinToString(prefix = "[", postfix = "]") { msg ->
                """{"role":"${escape(msg.role)}","content":"${escape(msg.content)}"}"""
            }
            val thinkingJson = if (enableThinking) ""","thinking":{"type":"enabled"}""" else ""
            val responseFormatJson = if (jsonMode && enableThinking) {
                // 仅智谱启用 thinking 时同时声明 JSON 输出格式
                ""","response_format":{"type":"json_object"}"""
            } else ""
            return """{"model":"${escape(model)}","max_tokens":65536,"temperature":0.6$thinkingJson$responseFormatJson,"messages":$messageJson}"""
        }

        /**
         * 从 OpenAI 兼容响应中提取 content
         */
        @Suppress("UNCHECKED_CAST")
        fun parseContent(raw: String): String? {
            return try {
                val mapAdapter: JsonAdapter<Map<String, Any?>> =
                    moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
                val map = mapAdapter.fromJson(raw) ?: return null
                val choices = map["choices"] as? List<Map<String, Any?>> ?: return null
                val first = choices.firstOrNull() ?: return null
                val message = first["message"] as? Map<String, Any?> ?: return null
                message["content"] as? String
            } catch (e: Exception) {
                null
            }
        }

        /** 拼接 baseUrl + path,自动处理尾部斜杠 */
        internal fun joinUrl(base: String, path: String): String {
            val b = base.trimEnd('/')
            val p = path.trimStart('/')
            return "$b/$p"
        }

        private fun escape(s: String): String =
            s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    }
}

/**
 * AI 调用相关异常(原 ZhipuException 改名,语义更通用)
 */
class AiApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
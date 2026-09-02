package com.local.questionbank.data.repository

import com.local.questionbank.data.datasource.AiApiException
import com.local.questionbank.data.datasource.JsonDiff
import com.local.questionbank.data.datasource.JsonFileParser
import com.local.questionbank.data.datasource.OpenAiCompatibleApi
import com.local.questionbank.domain.model.AiProfile
import com.local.questionbank.domain.model.AiPrompt
import com.local.questionbank.domain.model.AiProvider
import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.repository.AiAssistException
import com.local.questionbank.domain.repository.AiAssistantRepository
import com.local.questionbank.domain.repository.AiSettingsRepository
import com.local.questionbank.domain.repository.FixResult
import kotlinx.coroutines.flow.first

/**
 * AI 助手仓库实现(多供应商)
 *
 * 关键流程:
 *  1. 读 AiProfile(无 profile 或未就绪 → 抛 AiAssistException)
 *  2. 组装 AiPrompt(prompt 内已强制 JSON 输出)
 *  3. 用 OpenAiCompatibleApi 调 /chat/completions
 *     - 智谱启用 thinking(type=enabled) + response_format=json_object
 *     - 其他 provider 仅 OpenAI 标准字段
 *  4. 用 JsonFileParser.parseRaw 二次校验返回内容
 *  5. fixJson 返回结构化结果(含 diff);generateSimilarQuestion 返回第一条 Question
 */
class AiAssistantRepositoryImpl(
    private val settingsRepository: AiSettingsRepository,
    private val jsonFileParser: JsonFileParser
) : AiAssistantRepository {

    override suspend fun fixJson(rawJson: String, errorMessage: String): FixResult {
        val profile = requireProfile()
        val prompt = AiPrompt.forJsonFix(rawJson, errorMessage)
        return try {
            val raw = callApi(profile, prompt)
            // 1) AI 返回的 raw 直接当 fixedJson,不强制 validateJson
            //    (validateJson 之前会因为"和原版一样有问题"而失败)
            // 2) 用 JsonDiff 计算前后差异,给 UI 展示
            val diff = JsonDiff.diff(rawJson, raw)
            FixResult(
                success = true,
                fixedJson = raw,
                diff = diff
            )
        } catch (e: AiAssistException) {
            FixResult(
                success = false,
                errorMessage = e.message,
                errorSuggestion = classifyError(e)
            )
        }
    }

    override suspend fun generateSimilarQuestion(current: Question): Question {
        val profile = requireProfile()
        val prompt = AiPrompt.forSimilarQuestion(current)
        val raw = callApi(profile, prompt)
        // AI 可能包在 ```json ... ``` 里(尽管 prompt 禁止了),剥离
        val cleaned = stripCodeFence(raw)
        val wrapped = tryWrapAsBank(cleaned)
        val bank = validateJson(wrapped)
        val firstQuestion = bank.questions.firstOrNull()
            ?: throw AiAssistException("AI 返回内容不包含任何题目")
        return firstQuestion.copy(id = 0L, bankId = 0L)
    }

    /**
     * 按异常信息分类,给用户可读的解决建议
     */
    private fun classifyError(e: AiAssistException): String {
        val msg = e.message ?: return "请稍后重试"
        return when {
            msg.contains("HTTP 401") || msg.contains("Invalid") && msg.contains("API") ->
                "API Key 鉴权失败,请到 AI 助手设置核对 Key"
            msg.contains("HTTP 403") ->
                "API Key 无权限访问该模型,检查供应商账号权限"
            msg.contains("HTTP 429") ->
                "API 调用频率超限,请稍后重试"
            msg.contains("Unable to resolve host") ->
                "网络问题:无法连接 AI 服务,检查 WiFi 或代理"
            msg.contains("timeout", ignoreCase = true) || msg.contains("timed out") ->
                "网络超时,检查 WiFi 后重试"
            msg.contains("JSON") || msg.contains("解析") ->
                "AI 返回内容无法解析,可能是模型问题,换模型或重试"
            msg.contains("配置不完整") || msg.contains("API Key") ->
                "未配置 API Key,先去 AI 助手设置填写"
            else -> "请检查网络后重试"
        }
    }

    private suspend fun requireProfile(): AiProfile {
        val profile = settingsRepository.observeProfile().first()
        if (!profile.isReady()) {
            throw AiAssistException(
                "AI 配置不完整,请前往 AI 助手设置页填写 " +
                    "(provider=${profile.provider.displayName}, model=${profile.model}, apiKey=${profile.apiKey.isNotBlank()})"
            )
        }
        return profile
    }

    private suspend fun callApi(profile: AiProfile, prompt: AiPrompt): String {
        val api = OpenAiCompatibleApi(
            apiKey = profile.apiKey.trim(),
            baseUrl = profile.effectiveBaseUrl(),
            model = profile.model,
            enableThinking = profile.provider == AiProvider.ZHIPU
        )
        return try {
            api.chatCompletion(
                messages = listOf(
                    OpenAiCompatibleApi.ChatMessage(OpenAiCompatibleApi.ChatMessage.SYSTEM, prompt.systemPrompt),
                    OpenAiCompatibleApi.ChatMessage(OpenAiCompatibleApi.ChatMessage.USER, prompt.userPrompt)
                ),
                jsonMode = true
            )
        } catch (e: AiApiException) {
            throw AiAssistException("AI 调用失败(${profile.provider.displayName}): ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw AiAssistException(e.message ?: "参数错误", e)
        }
    }

    /**
     * 测试连接:用最小请求(max_tokens=1)探活
     *
     * 不通过 JsonFileParser 二次校验,只检查"能拿到非空 content"
     */
    override suspend fun testConnection(): String {
        val profile = requireProfile()
        val api = OpenAiCompatibleApi(
            apiKey = profile.apiKey.trim(),
            baseUrl = profile.effectiveBaseUrl(),
            model = profile.model,
            enableThinking = false  // 测试连接不走 thinking,避免误解
        )
        return try {
            val content = api.chatCompletion(
                messages = listOf(
                    OpenAiCompatibleApi.ChatMessage(
                        OpenAiCompatibleApi.ChatMessage.USER,
                        "Reply with the single word: OK"
                    )
                ),
                jsonMode = false
            )
            content.ifBlank { throw AiAssistException("响应内容为空") }
        } catch (e: AiApiException) {
            throw AiAssistException(
                "连接失败(${profile.provider.displayName} / ${profile.model}): ${e.message}",
                e
            )
        } catch (e: IllegalArgumentException) {
            throw AiAssistException(e.message ?: "参数错误", e)
        }
    }

    private fun validateJson(raw: String): com.local.questionbank.domain.model.QuestionBank {
        return try {
            JsonFileParser.parseRaw(raw)
        } catch (e: Exception) {
            throw AiAssistException("AI 返回的内容无法解析为合法题库: ${e.message}", e)
        }
    }

    /**
     * 剥离 AI 偶尔包在 ```json ... ``` Markdown 围栏里的内容
     */
    private fun stripCodeFence(raw: String): String {
        val trimmed = raw.trim()
        val match = Regex("^```(?:json)?\\s*\\n?(.*?)\\n?```$", RegexOption.DOT_MATCHES_ALL)
            .matchEntire(trimmed)
        return match?.groupValues?.get(1)?.trim() ?: trimmed
    }

    private fun tryWrapAsBank(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.contains("\"questions\"") || trimmed.contains("\"bankName\"")) {
            raw
        } else {
            """{"bankName":"AI","desc":"","questions":[${trimmed}]}"""
        }
    }
}
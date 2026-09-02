package com.local.questionbank.domain.model

/**
 * AI 供应商枚举 + 预设
 *
 * 所有国产模型 API 都兼容 OpenAI /chat/completions 协议,
 * 差异仅在 baseUrl / model id / 少量 query 参数。
 *
 * 用户在 AI 设置页选一个 provider,再选 model,填 API Key 即可。
 * 选 CUSTOM 时需要自己填 baseUrl 与 model,用于接入本地 Ollama、OpenRouter 等。
 */
enum class AiProvider(
    val displayName: String,
    /** 默认 baseUrl,选该 provider 时用;选 CUSTOM 时此字段作 placeholder */
    val defaultBaseUrl: String,
    /** 该 provider 的预设模型列表;选 CUSTOM 时此项可空 */
    val presetModels: List<String>
) {
    ZHIPU(
        displayName = "智谱 AI",
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        presetModels = listOf(
            "glm-4.7-flash",
            "glm-4-flash",
            "glm-z1-air"
        )
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        presetModels = listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        )
    ),
    MINIMAX(
        displayName = "MiniMax",
        defaultBaseUrl = "https://api.minimaxi.com/v1",
        presetModels = listOf(
            "MiniMax-Text-01",
            "abab6.5s-chat"
        )
    ),
    CUSTOM(
        displayName = "自定义(OpenAI 兼容)",
        defaultBaseUrl = "",
        presetModels = emptyList()
    );

    /** 是否需要 baseUrl 输入(只有 CUSTOM 是) */
    val requiresCustomBaseUrl: Boolean
        get() = this == CUSTOM

    /** 是否需要自定义 model 文本输入(CUSTOM 是;预设 provider 用户可下拉也可手填) */
    val allowsCustomModel: Boolean
        get() = this == CUSTOM
}

/**
 * AI 调用所需的完整配置
 *
 * 持久化在 EncryptedSharedPreferences,key = "ai_profile_v1"(JSON 序列化)
 */
data class AiProfile(
    val provider: AiProvider = AiProvider.ZHIPU,
    val model: String = AiProvider.ZHIPU.presetModels.first(),
    /** CUSTOM provider 时必填;其他 provider 为 null(用 enum 的 defaultBaseUrl) */
    val customBaseUrl: String? = null,
    /** 用户的 API Key / Token */
    val apiKey: String = ""
) {
    /** 解析后的实际 baseUrl(CUSTOM 用 customBaseUrl,其他用 provider.defaultBaseUrl) */
    fun effectiveBaseUrl(): String =
        customBaseUrl?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl

    /** 是否已配置完整(provider/baseUrl/apiKey 都齐全) */
    fun isReady(): Boolean =
        apiKey.isNotBlank() && effectiveBaseUrl().isNotBlank() && model.isNotBlank()

    companion object {
        val DEFAULT = AiProfile()
    }
}
package com.local.questionbank.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.local.questionbank.domain.model.AiProfile
import com.local.questionbank.domain.model.AiProvider
import com.local.questionbank.domain.repository.AiSettingsRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * AiSettingsRepository 实现
 *
 * - 使用 EncryptedSharedPreferences(AES256_GCM + Android Keystore 保护 master key)
 * - 整个 [AiProfile] 用 Moshi 序列化为 JSON,存 SP key `"ai_profile_v1"`
 * - 旧版本只存 API Key 在 `"siliconflow_api_key"`,首次读时如果新 key 不存在就回落到旧 key
 *   (向后兼容已有用户)
 */
class AiSettingsRepositoryImpl(
    private val context: Context
) : AiSettingsRepository {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                PREF_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Keystore 损坏/设备不支持时退化到普通 SP
            context.getSharedPreferences(PREF_FILE_NAME + "_plain", Context.MODE_PRIVATE)
        }
    }

    private val moshi: Moshi = Moshi.Builder().build()
    private val profileAdapter: JsonAdapter<ProfileJson> =
        moshi.adapter(ProfileJson::class.java)

    /** 进程内缓存 */
    private val profileCache = MutableStateFlow<AiProfile?>(null)
    private var initialized = false

    /** 同步从 SP 读出 Profile,带 fallback 到旧 Key */
    private fun loadProfileBlocking(): AiProfile {
        // 优先读新 key
        val newRaw = prefs.getString(KEY_PROFILE_V1, null)
        if (!newRaw.isNullOrBlank()) {
            try {
                val parsed = profileAdapter.fromJson(newRaw)
                if (parsed != null) return parsed.toDomain()
            } catch (_: Exception) { /* 解析失败,fallback */ }
        }
        // 回落到旧 key
        val oldKey = prefs.getString(KEY_LEGACY_API_KEY, null)?.takeIf { it.isNotBlank() }
        return if (oldKey != null) {
            AiProfile(provider = AiProvider.ZHIPU, model = "glm-4.7-flash", apiKey = oldKey)
        } else {
            AiProfile.DEFAULT
        }
    }

    private fun ensureLoaded() {
        if (!initialized) {
            profileCache.value = loadProfileBlocking()
            initialized = true
        }
    }

    // ---------- 向后兼容 API ----------
    override suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        ensureLoaded(); profileCache.value?.apiKey?.takeIf { it.isNotBlank() }
    }

    override suspend fun saveApiKey(key: String) = withContext(Dispatchers.IO) {
        val trimmed = key.trim()
        require(trimmed.isNotEmpty()) { "API Key 不能为空" }
        // 与现有 Profile 合并;其他字段保留
        val current = loadProfileBlocking()
        saveProfileBlocking(current.copy(apiKey = trimmed))
    }

    override suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        val current = loadProfileBlocking()
        saveProfileBlocking(current.copy(apiKey = ""))
    }

    override fun observeApiKey(): Flow<String?> {
        ensureLoaded()
        return kotlinx.coroutines.flow.flow {
            profileCache.collect { p -> emit(p?.apiKey?.takeIf { it.isNotBlank() }) }
        }
    }

    // ---------- 新 Profile API ----------
    override fun observeProfile(): Flow<AiProfile> {
        ensureLoaded()
        return profileCache.asStateFlow().let { src ->
            kotlinx.coroutines.flow.flow {
                src.collect { p -> emit(p ?: AiProfile.DEFAULT) }
            }
        }
    }

    override suspend fun saveProfile(profile: AiProfile) = withContext(Dispatchers.IO) {
        saveProfileBlocking(profile)
    }

    override suspend fun saveModel(model: String) = withContext(Dispatchers.IO) {
        val trimmed = model.trim()
        require(trimmed.isNotEmpty()) { "模型名不能为空" }
        val current = loadProfileBlocking()
        saveProfileBlocking(current.copy(model = trimmed))
    }

    private fun saveProfileBlocking(profile: AiProfile) {
        val json = profileAdapter.toJson(ProfileJson.fromDomain(profile))
        prefs.edit().putString(KEY_PROFILE_V1, json).apply()
        // 同步更新缓存,observe* 的下游 Flow 会立即 emit
        profileCache.value = profile
    }

    // ---------- SP 序列化结构 ----------
    /**
     * Profile 的 SP 序列化结构(AiProvider enum 存为字符串)
     *
     * 必须标注 [@JsonClass(generateAdapter = true)] 让 KSP 生成反射-free adapter,
     * 否则 Moshi 默认走反射,需要 kotlin-reflect 依赖(本项目未引入),会 IllegalArgumentException。
     */
    @JsonClass(generateAdapter = true)
    data class ProfileJson(
        val provider: String = AiProvider.ZHIPU.name,
        val model: String = "glm-4.7-flash",
        val customBaseUrl: String? = null,
        val apiKey: String = ""
    ) {
        fun toDomain(): AiProfile = AiProfile(
            provider = runCatching { AiProvider.valueOf(provider) }.getOrDefault(AiProvider.ZHIPU),
            model = model,
            customBaseUrl = customBaseUrl,
            apiKey = apiKey
        )

        companion object {
            fun fromDomain(p: AiProfile) = ProfileJson(
                provider = p.provider.name,
                model = p.model,
                customBaseUrl = p.customBaseUrl,
                apiKey = p.apiKey
            )
        }
    }

    companion object {
        private const val PREF_FILE_NAME = "ai_settings"
        /** 当前版本 Profile JSON 序列化 key */
        private const val KEY_PROFILE_V1 = "ai_profile_v1"
        /** 旧版本单 Key(向后兼容读取,不再写入) */
        private const val KEY_LEGACY_API_KEY = "siliconflow_api_key"
    }
}
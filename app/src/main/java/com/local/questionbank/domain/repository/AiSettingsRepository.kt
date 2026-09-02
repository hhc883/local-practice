package com.local.questionbank.domain.repository

import com.local.questionbank.domain.model.AiProfile
import kotlinx.coroutines.flow.Flow

/**
 * AI 设置仓库:管理 provider / model / baseUrl / API Key 的存储与读取
 *
 * 数据结构:整个 [AiProfile] 序列化为 JSON 存一个 SP key(`ai_profile_v1`)。
 * 向后兼容:旧版本只存了 API Key,本接口保留 [observeApiKey] 用于老代码;
 * 新代码优先使用 [observeProfile]。
 */
interface AiSettingsRepository {
    /** 当前已保存的 API Key,未配置返回 null */
    suspend fun getApiKey(): String?

    /** 保存 API Key(覆盖);保留向后兼容,内部转 [saveProfile] */
    suspend fun saveApiKey(key: String)

    /** 清除 API Key */
    suspend fun clearApiKey()

    /** 观察 API Key 变化;未配置时 emit null */
    fun observeApiKey(): Flow<String?>

    /** 观察完整 Profile(推荐使用) */
    fun observeProfile(): Flow<AiProfile>

    /** 保存完整 Profile */
    suspend fun saveProfile(profile: AiProfile)

    /**
     * 单独更新 model(切模型不动 provider/key);
     * 内部委托 [saveProfile]。
     */
    suspend fun saveModel(model: String)
}
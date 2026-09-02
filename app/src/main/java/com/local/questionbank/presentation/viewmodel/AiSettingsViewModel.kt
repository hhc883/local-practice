package com.local.questionbank.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.questionbank.domain.model.AiProfile
import com.local.questionbank.domain.model.AiProvider
import com.local.questionbank.domain.repository.AiAssistException
import com.local.questionbank.domain.repository.AiAssistantRepository
import com.local.questionbank.domain.repository.AiSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * AI 设置页 ViewModel
 *
 * - 显示当前 Profile(provider/model/apiKey 是否已配置)
 * - 切换 provider / model,实时更新 UI
 * - 保存整个 Profile(校验 provider 与 model 必填,CUSTOM 还要 baseUrl)
 * - 清除(整 Profile 重置)
 */
data class AiSettingsUiState(
    val isConfigured: Boolean = false,
    val provider: AiProvider = AiProvider.ZHIPU,
    val model: String = AiProvider.ZHIPU.presetModels.first(),
    /** CUSTOM 时用户填的 baseUrl,持久化到 AiProfile.customBaseUrl */
    val customBaseUrl: String = "",
    /** 仅显示 API Key 末 4 位 */
    val maskedKey: String = "",
    val errorMessage: String? = null,
    /** 测试连接状态 */
    val isTesting: Boolean = false,
    /** 测试连接结果文本(成功/失败,显示后由 UI consume) */
    val testResult: TestResult? = null
) {
    enum class TestResult { SUCCESS, FAILED }
    val availableModels: List<String>
        get() = provider.presetModels
    val showCustomBaseUrl: Boolean
        get() = provider.requiresCustomBaseUrl
    val allowsCustomModel: Boolean
        get() = true
}

class AiSettingsViewModel(
    private val settingsRepository: AiSettingsRepository,
    private val assistantRepository: AiAssistantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiSettingsUiState())
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeProfile().collect { profile ->
                _uiState.value = AiSettingsUiState(
                    isConfigured = profile.isReady(),
                    provider = profile.provider,
                    model = profile.model,
                    customBaseUrl = profile.customBaseUrl.orEmpty(),
                    maskedKey = maskKey(profile.apiKey)
                )
            }
        }
    }

    fun selectProvider(provider: AiProvider) {
        // 切 provider:模型默认切到该 provider 第一个预设;如果是 CUSTOM,保留当前 model 让用户填
        val newModel = when {
            provider == AiProvider.CUSTOM -> _uiState.value.model.takeIf { it.isNotBlank() }
                ?: "custom-model"
            else -> provider.presetModels.firstOrNull() ?: _uiState.value.model
        }
        _uiState.update {
            it.copy(provider = provider, model = newModel)
        }
    }

    fun selectModel(model: String) {
        _uiState.update { it.copy(model = model) }
    }

    fun updateCustomBaseUrl(text: String) {
        _uiState.update { it.copy(customBaseUrl = text.trim()) }
    }

    fun updateApiKey(key: String) {
        // 仅更新掩码显示 + 暂存待保存的 key
        _uiState.update { it.copy(maskedKey = maskKey(key)) }
        pendingApiKey = key
    }

    /** 待保存的 API Key(用户编辑时暂存) */
    private var pendingApiKey: String? = null

    /**
     * 保存完整 Profile
     *
     * 校验:
     *  - apiKey 必填(如果改了)
     *  - CUSTOM 时 customBaseUrl 必填
     *  - model 必填
     */
    fun saveProfile() {
        val s = _uiState.value
        val pendingKey = pendingApiKey
        val apiKeyToSave = if (!pendingKey.isNullOrBlank()) pendingKey.trim() else null

        // 校验
        if (s.model.isBlank()) {
            _uiState.update { it.copy(errorMessage = "模型不能为空") }
            return
        }
        if (s.provider == AiProvider.CUSTOM && s.customBaseUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "自定义供应商需要填 baseUrl") }
            return
        }
        // 如果是初次配置且未填 key,提示
        if (!s.isConfigured && apiKeyToSave == null) {
            _uiState.update { it.copy(errorMessage = "请填写 API Key") }
            return
        }

        viewModelScope.launch {
            runCatching {
                val current = settingsRepository.observeProfile().first()
                val newProfile = current.copy(
                    provider = s.provider,
                    model = s.model,
                    customBaseUrl = if (s.provider == AiProvider.CUSTOM) s.customBaseUrl.ifBlank { null } else null,
                    apiKey = apiKeyToSave ?: current.apiKey
                )
                settingsRepository.saveProfile(newProfile)
                pendingApiKey = null
            }.onFailure { e ->
                _uiState.update { it.copy(errorMessage = e.message ?: "保存失败") }
            }
        }
    }

    fun clearProfile() {
        viewModelScope.launch {
            runCatching { settingsRepository.clearApiKey() }
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message ?: "清除失败") } }
            pendingApiKey = null
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 测试当前配置是否通畅
     *
     * 用当前显示中的 provider/model/baseUrl/apiKey 调一次最小请求。
     * 注意:即使没保存也能测,直接用 UI 当前状态。
     */
    fun testConnection() {
        if (_uiState.value.isTesting) return
        val s = _uiState.value
        // 用 UI 当前状态临时构造 Profile(不依赖是否保存)
        val profile = run {
            // baseUrl 校验
            if (s.provider == AiProvider.CUSTOM && s.customBaseUrl.isBlank()) {
                _uiState.update { it.copy(testResult = AiSettingsUiState.TestResult.FAILED) }
                _uiState.update { it.copy(errorMessage = "自定义供应商需要填 baseUrl") }
                return
            }
            val effectiveKey = pendingApiKey?.takeIf { it.isNotBlank() } ?: s.maskedKey
            if (effectiveKey.isBlank() || effectiveKey == "****" || effectiveKey.all { it == '*' }) {
                _uiState.update { it.copy(testResult = AiSettingsUiState.TestResult.FAILED) }
                _uiState.update { it.copy(errorMessage = "请先填写 API Key") }
                return
            }
            AiProfile(
                provider = s.provider,
                model = s.model,
                customBaseUrl = if (s.provider == AiProvider.CUSTOM) s.customBaseUrl.ifBlank { null } else null,
                apiKey = pendingApiKey?.trim() ?: ""
            )
        }
        // 上面早退时已经设过 testResult + errorMessage;只有模型有效才会到这里
        _uiState.update { it.copy(isTesting = true, testResult = null, errorMessage = null) }
        viewModelScope.launch {
            try {
                val reply = assistantRepository.testConnection()
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        testResult = AiSettingsUiState.TestResult.SUCCESS,
                        errorMessage = "连接成功(返回: ${reply.take(40).replace("\n", " ")})"
                    )
                }
            } catch (e: AiAssistException) {
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        testResult = AiSettingsUiState.TestResult.FAILED,
                        errorMessage = e.message ?: "连接失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        testResult = AiSettingsUiState.TestResult.FAILED,
                        errorMessage = "连接失败: ${e.message ?: e.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun consumeTestResult() {
        _uiState.update { it.copy(testResult = null) }
    }

    /** 把 key 末 4 位显示出来,其余星号 */
    private fun maskKey(key: String): String {
        if (key.isBlank()) return ""
        if (key.length <= 4) return "****"
        return "*".repeat(key.length - 4) + key.takeLast(4)
    }

    private fun MutableStateFlow<AiSettingsUiState>.update(block: (AiSettingsUiState) -> AiSettingsUiState) {
        value = block(value)
    }
}
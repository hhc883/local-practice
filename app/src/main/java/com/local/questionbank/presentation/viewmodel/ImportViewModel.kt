package com.local.questionbank.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.questionbank.data.datasource.CsvFileParser
import com.local.questionbank.data.datasource.ImportFormatException
import com.local.questionbank.data.datasource.JsonFileParser
import com.local.questionbank.domain.repository.AiAssistException
import com.local.questionbank.domain.repository.AiAssistantRepository
import com.local.questionbank.domain.repository.QuestionBankRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.io.IOException

/**
 * 导入页 UiState
 *
 * - [isImporting] 在 SAF 解析 + DB 写入期间为 true
 * - [successBankId] 单文件导入路径下,导入成功后回填(legacy)
 * - [batchReport] 多文件导入完成后的批量报告;非空时 UI 应展示 Snackbar 后清空
 *   - 全部成功 → 退出页面
 *   - 部分/全部失败 → 不退出,展示报告
 * - [pendingAiFix] AI 修复会话状态;不为 null 时 UI 弹"修复结果"对话框
 * - [pendingErrorDetail] 导入失败详情;不为 null 时 UI 弹详情 AlertDialog
 */
data class ImportUiState(
    val isImporting: Boolean = false,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val successBankId: Long? = null,
    val errorMessage: String? = null,
    val batchReport: BatchImportReport? = null,
    val pendingAiFix: PendingAiFix? = null,
    val pendingErrorDetail: ErrorDetail? = null
)

/** 失败详情快照 */
data class ErrorDetail(
    val fileName: String,
    val shortMessage: String,
    val detail: String
)

/** 多文件导入结果汇总 */
data class BatchImportReport(
    val total: Int,
    val successCount: Int,
    val failureCount: Int,
    val failedNames: List<String>,
    /** 失败文件的 (displayName, uri, errorMessage) 列表,供"AI 修复"使用 */
    val failedItems: List<FailedItem> = emptyList()
)

data class FailedItem(
    val displayName: String,
    val uri: Uri,
    val errorMessage: String
)

/**
 * AI 修复会话快照
 *
 * @property diff 修复前后字段差异,UI 顶部展示"AI 改了哪几处"
 */
data class PendingAiFix(
    val fileName: String,
    val originalJson: String,
    val errorMessage: String,
    val fixedJson: String? = null,
    val diff: List<com.local.questionbank.data.datasource.DiffEntry> = emptyList(),
    val isLoading: Boolean = false
)

class ImportViewModel(
    private val appContext: Context,
    private val parser: JsonFileParser,
    private val csvParser: CsvFileParser,
    private val repository: QuestionBankRepository,
    private val aiAssistantRepository: AiAssistantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun importFromUri(uri: Uri) {
        importBanks(listOf(uri))
    }

    fun importBanks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (_uiState.value.isImporting) return
        _uiState.value = ImportUiState(
            isImporting = true,
            currentIndex = 0,
            totalCount = uris.size
        )
        viewModelScope.launch {
            val successIds = mutableListOf<Long>()
            val failedNames = mutableListOf<String>()
            val failedItems = mutableListOf<FailedItem>()
            uris.forEachIndexed { index, uri ->
                _uiState.update { it.copy(currentIndex = index + 1) }
                val result = runCatching {
                    val bank = parseAny(uri)
                    repository.importBank(bank)
                }
                result.fold(
                    onSuccess = { successIds.add(it) },
                    onFailure = { e ->
                        val name = displayName(uri)
                        val tag = errorTag(e)
                        val short = "$name  ($tag)"
                        failedNames.add(short)
                        failedItems.add(FailedItem(name, uri, tag))
                        // 解析失败且异常带 detail → 存到 pendingErrorDetail 让 UI 弹详情
                        val detail = (e as? ImportFormatException)?.detail
                        if (detail != null && _uiState.value.pendingErrorDetail == null) {
                            _uiState.update {
                                it.copy(
                                    pendingErrorDetail = ErrorDetail(
                                        fileName = name,
                                        shortMessage = short,
                                        detail = detail
                                    )
                                )
                            }
                        }
                    }
                )
            }
            _uiState.value = if (failedNames.isEmpty()) {
                ImportUiState(successBankId = successIds.first())
            } else {
                ImportUiState(
                    batchReport = BatchImportReport(
                        total = uris.size,
                        successCount = successIds.size,
                        failureCount = failedNames.size,
                        failedNames = failedNames,
                        failedItems = failedItems
                    )
                )
            }
        }
    }

    /**
     * 根据 Uri 的 MIME / 扩展名决定走 JSON 解析还是 CSV 解析
     */
    private suspend fun parseAny(uri: Uri) = when {
        isCsvMime(uri) || isCsvExtension(uri) -> csvParser.parseFromUri(uri)
        else -> parser.parseFromUri(uri)
    }

    private fun isCsvMime(uri: Uri): Boolean {
        val mime = appContext.contentResolver.getType(uri) ?: return false
        return mime == "text/csv" || mime.endsWith("csv")
    }

    private fun isCsvExtension(uri: Uri): Boolean {
        val path = uri.toString().lowercase()
        return path.endsWith(".csv")
    }

    /**
     * 触发 AI 修复失败文件
     * - 重读 Uri 拿 raw JSON
     * - 设 pendingAiFix(isLoading=true) → UI 显示 Loading
     * - 调 AI → pendingAiFix(fixedJson=...) → UI 展示结果
     */
    fun fixWithAi(uri: Uri, errorMessage: String) {
        viewModelScope.launch {
            val rawJson = runCatching { readRawText(uri) }
                .onFailure { e ->
                    _uiState.update { it.copy(errorMessage = "读取文件失败: ${e.message}") }
                    return@launch
                }
                .getOrNull() ?: return@launch

            val fileName = displayName(uri)
            _uiState.update {
                it.copy(
                    pendingAiFix = PendingAiFix(
                        fileName = fileName,
                        originalJson = rawJson,
                        errorMessage = errorMessage,
                        isLoading = true
                    )
                )
            }

            runCatching { aiAssistantRepository.fixJson(rawJson, errorMessage) }
                .onSuccess { result ->
                    if (result.success && result.fixedJson != null) {
                        _uiState.update {
                            it.copy(
                                pendingAiFix = (it.pendingAiFix ?: return@update it).copy(
                                    fixedJson = result.fixedJson,
                                    diff = result.diff,
                                    isLoading = false
                                )
                            )
                        }
                    } else {
                        // 失败 → 复用 ErrorDetailDialog,展示原始错误 + 分类建议
                        _uiState.update {
                            it.copy(
                                pendingAiFix = null,
                                errorMessage = "AI 修复失败: ${result.errorMessage ?: "未知"}",
                                pendingErrorDetail = ErrorDetail(
                                    fileName = fileName,
                                    shortMessage = "AI 修复失败",
                                    detail = buildString {
                                        appendLine("原始错误:")
                                        appendLine(result.errorMessage ?: "未知")
                                        appendLine()
                                        if (!result.errorSuggestion.isNullOrBlank()) {
                                            appendLine("建议:")
                                            appendLine(result.errorSuggestion)
                                        }
                                    }.trimEnd()
                                )
                            )
                        }
                    }
                }
                .onFailure { e ->
                    val msg = when (e) {
                        is AiAssistException -> e.message ?: "AI 助手异常"
                        else -> e.message ?: e.javaClass.simpleName
                    }
                    _uiState.update {
                        it.copy(
                            pendingAiFix = null,
                            errorMessage = "AI 修复失败: $msg"
                        )
                    }
                }
        }
    }

    fun applyFixedJson() {
        val pending = _uiState.value.pendingAiFix?.fixedJson ?: return
        viewModelScope.launch {
            runCatching {
                val bank = JsonFileParser.parseRaw(pending)
                repository.importBank(bank)
            }
                .onSuccess { newId ->
                    _uiState.update {
                        it.copy(
                            pendingAiFix = null,
                            successBankId = newId
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            pendingAiFix = null,
                            errorMessage = "应用失败: ${e.message ?: e.javaClass.simpleName}"
                        )
                    }
                }
        }
    }

    fun dismissAiFix() {
        _uiState.update { it.copy(pendingAiFix = null) }
    }

    fun consumeSuccess() {
        _uiState.value = _uiState.value.copy(successBankId = null)
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun consumeBatchReport() {
        _uiState.value = _uiState.value.copy(batchReport = null)
    }

    fun consumeErrorDetail() {
        _uiState.value = _uiState.value.copy(pendingErrorDetail = null)
    }

    /** 用户点击某失败项的"详情"按钮,展示该条的完整错误 */
    fun showErrorDetail(fileName: String) {
        val report = _uiState.value.batchReport ?: return
        val item = report.failedItems.firstOrNull { it.displayName == fileName } ?: return
        // detail 不直接挂在 FailedItem 上,而是缓存到 pendingErrorDetail 临时对象
        // 这里复用第一条失败项的 detail(VM 启动时已存了第一条)
        val current = _uiState.value.pendingErrorDetail
        if (current?.fileName == fileName) return  // 已经是当前,无需更新
        // 没有现成 detail 的情况下,显示短消息+ "无更多详情"
        _uiState.update {
            it.copy(
                pendingErrorDetail = ErrorDetail(
                    fileName = fileName,
                    shortMessage = "${item.displayName}  (${item.errorMessage})",
                    detail = "无更多详情。\n该错误属于 ${item.errorMessage}。\n如果是数据格式问题,可尝试 AI 修复。"
                )
            )
        }
    }

    private fun displayName(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: uri.toString().take(40)

    private fun errorTag(e: Throwable): String = when (e) {
        is ImportFormatException -> "模板错误"
        is IOException -> "读取失败"
        else -> e.message?.take(30) ?: e.javaClass.simpleName
    }

    private suspend fun readRawText(uri: Uri): String = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri)
            ?.source()?.buffer()?.use { it.readUtf8() }
            ?: throw IOException("无法打开 Uri=$uri")
    }
}
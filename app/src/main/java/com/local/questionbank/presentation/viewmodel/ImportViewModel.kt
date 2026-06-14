package com.local.questionbank.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.questionbank.data.datasource.ImportFormatException
import com.local.questionbank.data.datasource.JsonFileParser
import com.local.questionbank.domain.repository.QuestionBankRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * 导入页 UiState
 *
 * - [isImporting] 仅在 SAF 解析 + DB 写入期间为 true
 * - [successBankId] 导入成功后回填，UI 据此 popBackStack
 */
data class ImportUiState(
    val isImporting: Boolean = false,
    val successBankId: Long? = null,
    val errorMessage: String? = null
)

class ImportViewModel(
    private val parser: JsonFileParser,
    private val repository: QuestionBankRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /**
     * 解析 + 导入
     *
     * @param uri 通过 SAF 拿到的 content:// Uri，生命周期由调用方持有
     */
    fun importFromUri(uri: Uri) {
        if (_uiState.value.isImporting) return
        _uiState.value = ImportUiState(isImporting = true)
        viewModelScope.launch {
            val result = runCatching {
                val bank = parser.parseFromUri(uri)
                repository.importBank(bank)
            }
            _uiState.value = result.fold(
                onSuccess = { newId -> ImportUiState(successBankId = newId) },
                onFailure = { e ->
                    val msg = when (e) {
                        is ImportFormatException -> "模板错误：${e.message}"
                        is IOException -> "文件读取失败：${e.message ?: "无法打开文件"}"
                        else -> "导入失败：${e.message ?: e.javaClass.simpleName}"
                    }
                    ImportUiState(errorMessage = msg)
                }
            )
        }
    }

    fun consumeSuccess() {
        _uiState.value = _uiState.value.copy(successBankId = null)
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

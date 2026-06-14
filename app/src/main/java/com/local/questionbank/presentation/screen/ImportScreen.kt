package com.local.questionbank.presentation.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.questionbank.di.AppContainer
import com.local.questionbank.presentation.viewmodel.AppViewModelFactory
import com.local.questionbank.presentation.viewmodel.ImportViewModel

/**
 * 导入题库页
 *
 * 流程：
 *  1. 用户点击"选择 JSON"
 *  2. 系统 SAF 弹窗 → 选中后回调 Uri
 *  3. ViewModel 解析 + 写入 Room，成功后回 popBackStack
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onImported: () -> Unit
) {
    val viewModel: ImportViewModel = viewModel(
        factory = AppViewModelFactory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF 启动器
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.importFromUri(uri)
    }

    // 成功导入 → 退出
    LaunchedEffect(state.successBankId) {
        if (state.successBankId != null) {
            viewModel.consumeSuccess()
            onImported()
        }
    }
    // 错误提示
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "导入题库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "选择符合模板的 JSON 文件：",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = """
                    {
                      "bankName": "题库名称",
                      "desc": "题库描述",
                      "questions": [
                        { "type": "SINGLE", "title": "...", "options": ["A","B"], "answer": ["0"], "analysis": "..." }
                      ]
                    }
                """.trimIndent(),
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { launcher.launch(arrayOf("application/json", "*/*")) },
                enabled = !state.isImporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (state.isImporting) "导入中..." else "选择 JSON 文件")
            }
        }
    }
}

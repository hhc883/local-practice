package com.local.questionbank.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.questionbank.di.AppContainer
import com.local.questionbank.domain.model.AiProvider
import com.local.questionbank.presentation.viewmodel.AiSettingsViewModel
import com.local.questionbank.presentation.viewmodel.AppViewModelFactory

/**
 * AI 助手设置页
 *
 * - 选供应商(智谱 / DeepSeek / MiniMax / 自定义)
 * - 选模型(预设 provider 有下拉;自定义时自由输入)
 * - 填 baseUrl(仅自定义)
 * - 填 API Key
 *
 * 已配置时显示当前 Key 末 4 位掩码,可整体清除
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: AiSettingsViewModel = viewModel(
        factory = AppViewModelFactory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var inputKey by remember { mutableStateOf("") }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 助手设置") },
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
                text = "AI 配置",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "支持智谱 AI / DeepSeek / MiniMax 三家预设,以及任何 OpenAI 兼容 API(本地 Ollama 等)。\nKey 仅保存在本机加密存储中,不会上传到任何服务器。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. 供应商下拉
            ProviderDropdown(
                selected = state.provider,
                onSelect = { viewModel.selectProvider(it) }
            )

            // 2. 模型选择 / 输入
            ModelInput(
                state = state,
                onModelChange = { viewModel.selectModel(it) }
            )

            // 3. baseUrl(仅 CUSTOM)
            if (state.showCustomBaseUrl) {
                OutlinedTextField(
                    value = state.customBaseUrl,
                    onValueChange = { viewModel.updateCustomBaseUrl(it) },
                    label = { Text("Base URL(不含 /chat/completions)") },
                    placeholder = { Text("https://api.example.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // 4. API Key
            Text(
                text = if (state.isConfigured) "当前 Key:${state.maskedKey}" else "API Key(必填)",
                style = MaterialTheme.typography.labelMedium
            )
            OutlinedTextField(
                value = inputKey,
                onValueChange = {
                    inputKey = it
                    viewModel.updateApiKey(it)
                },
                label = { Text(if (state.isConfigured) "新 Key(留空保留旧值)" else "API Key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 5. 保存 + 测试连接(并列)
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.saveProfile()
                        inputKey = ""
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isTesting
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(" 保存", modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isTesting
                ) {
                    if (state.isTesting) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.height(0.dp))
                        Text(" 测试中…", modifier = Modifier.padding(start = 6.dp))
                    } else {
                        Text("测试连接")
                    }
                }
            }
            // 测试连接完成 — Snackbar(errorMessage)已经给详细反馈,
            // 这里把 testResult 立即消费,避免下方留灰色残留行
            state.testResult?.let {
                viewModel.consumeTestResult()
            }

            // 6. 清除
            if (state.isConfigured) {
                OutlinedButton(
                    onClick = { viewModel.clearProfile() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("清除全部配置") }
            }

            Text(
                text = "说明:未配置时 App 仍可完全离线使用。AI 出题 / JSON 修复需要联网。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ProviderDropdown(
    selected: AiProvider,
    onSelect: (AiProvider) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("供应商") },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AiProvider.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.displayName) },
                    onClick = {
                        onSelect(p)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelInput(
    state: com.local.questionbank.presentation.viewmodel.AiSettingsUiState,
    onModelChange: (String) -> Unit
) {
    // 预设 provider 有列表,显示下拉;自定义 provider 退化为文本输入
    if (state.provider.presetModels.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedTextField(
                value = state.model,
                onValueChange = {},
                readOnly = true,
                label = { Text("模型(可下拉选预设,或填其他)") },
                trailingIcon = {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                state.provider.presetModels.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m) },
                        onClick = {
                            onModelChange(m)
                            expanded = false
                        }
                    )
                }
            }
        }
    } else {
        // CUSTOM:纯文本输入
        OutlinedTextField(
            value = state.model,
            onValueChange = { onModelChange(it) },
            label = { Text("模型 ID") },
            placeholder = { Text("例如 qwen2.5:7b") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}
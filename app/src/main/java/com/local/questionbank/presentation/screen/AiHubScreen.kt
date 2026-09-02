package com.local.questionbank.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.questionbank.di.AppContainer
import com.local.questionbank.presentation.viewmodel.AiSettingsViewModel
import com.local.questionbank.presentation.viewmodel.AppViewModelFactory

/**
 * AI 助手主页
 *
 * - 顶部展示当前 provider / model(便于切换后立即确认)
 * - 两个功能入口说明:JSON 修复 / AI 出新题
 * - 底部"去设置"快捷入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHubScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onGoToImport: () -> Unit,
    onGoToSettings: () -> Unit
) {
    val settingsViewModel: AiSettingsViewModel = viewModel(
        factory = AppViewModelFactory(container)
    )
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 助手") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onGoToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "AI 设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部当前模型提示卡
            CurrentModelCard(
                providerName = settings.provider.displayName,
                modelName = settings.model,
                isConfigured = settings.isConfigured
            )

            Text(
                text = "AI 助手能做什么",
                style = MaterialTheme.typography.titleMedium
            )

            AiHubCard(
                icon = Icons.Default.Build,
                title = "JSON 一键修复",
                description = "导入失败时,AI 会读取原 JSON 和错误描述,自动修复后让你确认入库。\n\n使用场景:导入题目页面 → 选 JSON → 失败 Snackbar 中点「AI 修复」。"
            )
            AiHubCard(
                icon = Icons.Default.QuestionAnswer,
                title = "AI 出新题",
                description = "在任意题库里刷题时,点顶部 AutoAwesome 图标,AI 基于当前题生成同知识点新题。\n\n使用场景:刷题页顶部 → AutoAwesome 图标 → 生成的题可加入当前题库。"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onGoToImport),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Text(
                        text = "  去导入页面(可触发 AI 修复)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentModelCard(
    providerName: String,
    modelName: String,
    isConfigured: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConfigured)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = if (isConfigured) MaterialTheme.colorScheme.tertiary
                       else MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = if (isConfigured) "当前模型" else "未配置 AI",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isConfigured) "$providerName / $modelName"
                           else "请到 AI 设置页填写 API Key",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun AiHubCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
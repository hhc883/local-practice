package com.local.questionbank.presentation.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.questionbank.di.AppContainer
import com.local.questionbank.presentation.viewmodel.AppViewModelFactory
import com.local.questionbank.presentation.viewmodel.ImportViewModel

/**
 * 导入题库页
 *
 * 流程:
 *  1. 用户点击"选择 JSON 文件(可多选)",SAF 弹文件选择器
 *  2. ViewModel 串行解析 + 写入 Room
 *  3. 失败的文件可在报告区域点 "AI 修复" → 弹窗展示修复后 JSON → 应用
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

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.importBanks(uris)
    }

    LaunchedEffect(state.successBankId) {
        if (state.successBankId != null) {
            viewModel.consumeSuccess()
            onImported()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeError()
        }
    }
    LaunchedEffect(state.batchReport) {
        state.batchReport?.let { report ->
            val msg = buildString {
                append("导入完成:成功 ${report.successCount} / 共 ${report.total}")
                if (report.failureCount > 0) append(",失败 ${report.failureCount}")
            }
            val detail = if (report.failedNames.isNotEmpty()) {
                report.failedNames.joinToString("\n") { "• $it" }
            } else null
            val result = snackbarHostState.showSnackbar(
                message = if (detail != null) "$msg\n$detail" else msg,
                actionLabel = "知道了",
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed || result == SnackbarResult.Dismissed) {
                viewModel.consumeBatchReport()
            }
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
                text = "选择符合模板的 JSON 或 CSV 文件(可多选):",
                style = MaterialTheme.typography.titleMedium
            )
            // 模板查看器(可展开/收起,默认收起避免页面太长)
            TemplateViewer()
            if (state.isImporting && state.totalCount > 1) {
                LinearProgressIndicator(
                    progress = { state.currentIndex.toFloat() / state.totalCount.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "正在导入 ${state.currentIndex} / ${state.totalCount}...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { launcher.launch(arrayOf("application/json", "text/csv", "*/*")) },
                enabled = !state.isImporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when {
                        state.isImporting && state.totalCount > 1 -> "导入中 (${state.currentIndex}/${state.totalCount})"
                        state.isImporting -> "导入中..."
                        else -> "选择 JSON 文件(可多选)"
                    }
                )
            }
            // 失败项列表 + AI 修复按钮
            state.batchReport?.let { report ->
                if (report.failedItems.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "失败项 (${report.failureCount}):",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        report.failedItems.forEach { item ->
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        item.errorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                TextButton(onClick = {
                                    viewModel.showErrorDetail(item.displayName)
                                }) {
                                    Text("详情")
                                }
                                OutlinedButton(onClick = {
                                    viewModel.fixWithAi(item.uri, item.errorMessage)
                                }) {
                                    Text("AI 修复")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 导入错误详情对话框(独立于 AI 修复对话框)
    val errDetail = state.pendingErrorDetail
    if (errDetail != null) {
        AlertDialog(
            onDismissRequest = { viewModel.consumeErrorDetail() },
            title = { Text("导入失败:${errDetail.fileName}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                ) {
                    Text(
                        text = "简短错误:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = errDetail.shortMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "详细信息:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = errDetail.detail,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeErrorDetail() }) {
                    Text("关闭")
                }
            }
        )
    }

    // AI 修复对话框
    val pending = state.pendingAiFix
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { if (!pending.isLoading) viewModel.dismissAiFix() },
            title = { Text("AI 修复: ${pending.fileName}") },
            text = {
                if (pending.isLoading) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
                        Text("AI 正在修复...")
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "原错误:${pending.errorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        // diff 列表:展示 AI 修了哪些字段
                        if (pending.diff.isNotEmpty()) {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "AI 修改了 ${pending.diff.size} 处:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(4.dp))
                            pending.diff.forEach { entry ->
                                Text(
                                    text = "• ${entry.path}  ${entry.description}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "AI 修复结果(预览):",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = pending.fixedJson ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (!pending.isLoading && pending.fixedJson != null) {
                    Button(onClick = { viewModel.applyFixedJson() }) { Text("应用") }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAiFix() }) {
                    Text(if (pending.isLoading) "取消" else "关闭")
                }
            }
        )
    }
}

/**
 * 模板查看器(可展开/收起)
 *
 * 设计:
 *  - 默认收起,只显示一个"查看模板"按钮(避免占用导入页空间)
 *  - 展开后顶部是 JSON / CSV 两个 Tab,各自显示模板 + 关键规则
 *  - 整块内容可垂直滚动,长模板不被截断
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateViewer() {
    var expanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 标题行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "查看模板(JSON / CSV)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开"
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.size(8.dp))
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("JSON") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("CSV") }
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (selectedTab == 0) JsonTemplateContent() else CsvTemplateContent()
                }
            }
        }
    }
}

@Composable
private fun JsonTemplateContent() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "JSON 模板(6 种题型完整示例):",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CodeBlock("""
{
  "bankName": "Java 基础 · 习题 1",
  "desc": "可选题库描述",
  "questions": [
    {
      "type": "SINGLE",
      "title": "JDK 提供的编译器是?",
      "options": ["java.exe", "javac.exe", "javap.exe", "javaw.exe"],
      "answer": ["1"],
      "analysis": "javac 是编译工具"
    },
    {
      "type": "MULTI",
      "title": "Java 哪些是基本类型?",
      "options": ["int", "String", "bool", "double"],
      "answer": ["0", "3"],
      "analysis": "int 和 double"
    },
    {
      "type": "JUDGE",
      "title": "Java 由 Sun 公司开发",
      "options": [],
      "answer": ["0"],
      "analysis": "正确"
    },
    {
      "type": "BLANK",
      "title": "源文件 Speak.java 的扩展名?",
      "options": [],
      "answer": [".java"]
    },
    {
      "type": "DEBUG",
      "title": "Example.java 错误行是?",
      "options": ["A", "B", "C", "D"],
      "answer": ["3"],
      "analysis": "D 选项 system 应大写"
    },
    {
      "type": "PROG",
      "title": "写出 assert 断言",
      "options": [],
      "answer": ["assert (x >= 0 && x <= 100) : \"非法数据\";"]
    }
  ]
}
        """.trimIndent())
        HorizontalDivider()
        Text("关键规则:", style = MaterialTheme.typography.labelMedium)
        BulletList(listOf(
            "type 必须大写: SINGLE / MULTI / JUDGE / DEBUG / BLANK / READ / PROG",
            "answer 是字符串数组:SINGLE/DEBUG/MULTI 存下标;JUDGE 用 \"0\"(正确)或 \"1\"(错误);BLANK/PROG 存原文",
            "JUDGE 题 options 可为空,系统自动展示\"正确/错误\"按钮",
            "PROG 题 answer 里的 \\n 会被解析为真实换行",
            "文件必须 UTF-8 编码(记事本「另存为」选 UTF-8,不要 UTF-8 BOM)"
        ))
    }
}

@Composable
private fun CsvTemplateContent() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "CSV 模板(完整示例,首行表头):",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CodeBlock("""
type,title,optA,optB,optC,optD,answer,analysis,bankName,desc
SINGLE,JDK 编译器是?,java.exe,javac.exe,javap.exe,javaw.exe,1,javac 是编译,Java 基础,入门
MULTI,Java 哪些是基本类型,int,String,bool,double,0;3,int 和 double,Java 基础,入门
JUDGE,Java 由 Sun 公司开发,T,F,,,0,正确,Java 基础,入门
BLANK,扩展名是?,,,,,java,见题面,Java 基础,入门
DEBUG,Example.java 错误行是?,A,B,C,D,3,D 选项 system 应大写,Java 基础,入门
        """.trimIndent())
        HorizontalDivider()
        Text("关键规则:", style = MaterialTheme.typography.labelMedium)
        BulletList(listOf(
            "必填列:type, title, answer",
            "选项列 optA..optZ 顺序读取,遇空列停止",
            "MULTI/多空 answer 用 ; 分隔(如 \"0;3\" = A 和 D)",
            "JUDGE 题 options 可空(系统自动展示\"正确/错误\");或写 T/F 列",
            "BLANK/PROG 题 options 全空,answer 存原文",
            "文件必须 UTF-8(不要 UTF-8 BOM)"
        ))
    }
}

/**
 * 等宽字体代码块(可复制样式),浅灰背景
 */
@Composable
private fun CodeBlock(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

/**
 * 简单项目符号列表
 */
@Composable
private fun BulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Text("• ", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
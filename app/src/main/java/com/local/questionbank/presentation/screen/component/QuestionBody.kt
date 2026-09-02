package com.local.questionbank.presentation.screen.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.questionbank.domain.model.Question
import com.local.questionbank.domain.model.QuestionType

/**
 * 通用题目渲染组件
 *
 * - 顶部:【题型】题干
 * - 中部:选项(JUDGE 固定为"正确/错误",BLANK/PROG 文本框,其余选项卡)
 * - 底部:已提交时显示对错 + 正确答案 + 解析
 *
 * 整页可滚(verticalScroll)以应对长题干 / 多选项 / 长解析
 *
 * 用于:
 *  - QuestionScreen(实际刷题)
 *  - BankAiQuizScreen(AI 批量生成题作答)
 */
@Composable
fun QuestionBody(
    question: Question,
    selected: Set<Int>,
    submitted: Boolean,
    isCorrect: Boolean?,
    correctAnswer: List<String>,
    onToggle: (Int) -> Unit,
    userAnswerText: String = "",
    onUserTextChange: (String) -> Unit = {},
    showQuestionTypeLabel: Boolean = true,
    /**
     * 是否允许组件内部垂直滚动。
     *  - true(默认):长题干 / 多选项 / 长解析时可独立滚动,适合放在 HorizontalPager / Box 中
     *  - false:禁用内滚动,适合放在 LazyColumn item 内(避免"infinity height"崩溃)
     */
    scrollable: Boolean = true
) {
    val baseModifier = Modifier.fillMaxWidth()
    val modifier = if (scrollable) {
        baseModifier.verticalScroll(rememberScrollState()).padding(16.dp)
    } else {
        baseModifier.padding(16.dp)
    }
    Column(modifier = modifier) {
        // 题干 + UNKNOWN 黄 tag
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = if (showQuestionTypeLabel) {
                    "【${question.type.name}】${question.title}"
                } else {
                    question.title
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (question.type == QuestionType.UNKNOWN) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(start = 6.dp))
                androidx.compose.material3.Surface(
                    color = Color(0xFFFFA000),   // 琥珀色
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "未知题型",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        // 题面附加代码(v5+):题干下方,选项/输入框上方
        if (!question.codeSnippet.isNullOrBlank()) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 4.dp))
            CodeBlock(code = question.codeSnippet)
        }
        // 填空题 / 编程题 / 未知题型:文本输入框(answer 存原文)
        if ((question.type == QuestionType.BLANK || question.type == QuestionType.PROG || question.type == QuestionType.UNKNOWN) && question.options.isEmpty()) {
            OutlinedTextField(
                value = userAnswerText,
                onValueChange = onUserTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (question.type == QuestionType.PROG) 160.dp else 56.dp),
                enabled = !submitted,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = if (question.type == QuestionType.PROG) FontFamily.Monospace else FontFamily.Default,
                    fontSize = if (question.type == QuestionType.PROG) 13.sp else 16.sp
                ),
                placeholder = {
                    Text(
                        if (question.type == QuestionType.PROG) "请输入代码" else "请输入答案",
                        fontFamily = if (question.type == QuestionType.PROG) FontFamily.Monospace else FontFamily.Default
                    )
                },
                singleLine = question.type == QuestionType.BLANK,
                maxLines = if (question.type == QuestionType.PROG) 12 else 1
            )
        } else {
            val displayOptions = if (question.type == QuestionType.JUDGE) {
                listOf("正确", "错误")
            } else {
                question.options
            }
            displayOptions.forEachIndexed { index, optionText ->
                OptionCard(
                    index = index,
                    text = optionText,
                    isSelected = index in selected,
                    isCorrectAnswer = submitted && correctAnswer.contains(index.toString()),
                    isWrongPick = submitted && index in selected && !correctAnswer.contains(index.toString()),
                    onClick = { onToggle(index) }
                )
            }
        }

        // 提交后展示答案 / 解析
        if (submitted) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isCorrect == true)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isCorrect == true) "回答正确" else "回答错误",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (question.type == QuestionType.PROG) {
                        Text(
                            text = "参考代码:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = correctAnswer.joinToString("\n"),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    } else {
                        Text(
                            text = "正确答案:${
                                if (question.type == QuestionType.BLANK) correctAnswer.joinToString()
                                else correctAnswer.joinToString { qIndexToLetter(it.toIntOrNull() ?: 0) }
                            }",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (!question.analysis.isNullOrBlank()) {
                        Text(
                            text = "解析:${question.analysis}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OptionCard(
    index: Int,
    text: String,
    isSelected: Boolean,
    isCorrectAnswer: Boolean,
    isWrongPick: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isCorrectAnswer -> MaterialTheme.colorScheme.primary
        isWrongPick -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val bg = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Text(
            text = "${qIndexToLetter(index)}.  $text",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

fun qIndexToLetter(index: Int): String = ('A' + index).toString()
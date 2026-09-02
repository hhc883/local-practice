package com.local.questionbank.presentation.screen.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 代码块渲染组件
 *
 * 设计:
 *  - 浅灰背景(`surfaceVariant`)区分"这里是代码"
 *  - 等宽字体 + 13sp 紧凑字号
 *  - **horizontalScroll**(超长代码不折行,用户左右滑看完整)
 *  - 不引入语法高亮库(用户选择先不要)
 *
 * 未来扩展点:换 Text 为 AnnotatedString + AnnotatedString.Builder
 * 实现关键字着色(public/class/...)和字符串着色("...")
 */
@Composable
fun CodeBlock(
    code: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false
            )
        }
    }
}

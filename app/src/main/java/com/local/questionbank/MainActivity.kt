package com.local.questionbank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.local.questionbank.presentation.navigation.AppNavGraph
import com.local.questionbank.presentation.theme.QuestionBankTheme

/**
 * 单 Activity 入口
 *
 * 职责：
 *  - 提供 Android Context
 *  - 安装主题
 *  - 装载 NavHost
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 通过 Application 拿到依赖容器
        val container = (application as QuestionBankApp).container

        setContent {
            QuestionBankTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph(container = container)
                }
            }
        }
    }
}

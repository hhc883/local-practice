package com.local.questionbank

import android.app.Application
import com.local.questionbank.di.AppContainer

/**
 * Application
 *
 * 唯一职责：持有 [AppContainer]，对外暴露依赖获取入口
 *
 * 注意：
 *  - 不要在这里做耗时操作
 *  - 不要在这里做与上下文无关的静态初始化
 */
class QuestionBankApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}

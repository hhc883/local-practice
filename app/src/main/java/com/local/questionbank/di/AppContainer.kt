package com.local.questionbank.di

import android.content.Context
import com.local.questionbank.data.database.AppDatabase
import com.local.questionbank.data.datasource.CsvFileParser
import com.local.questionbank.data.datasource.JsonFileParser
import com.local.questionbank.data.repository.AiAssistantRepositoryImpl
import com.local.questionbank.data.repository.AiSettingsRepositoryImpl
import com.local.questionbank.data.repository.AnswerRepositoryImpl
import com.local.questionbank.data.repository.FavoriteRepositoryImpl
import com.local.questionbank.data.repository.QuestionBankRepositoryImpl
import com.local.questionbank.data.repository.QuestionRepositoryImpl
import com.local.questionbank.domain.repository.AiAssistantRepository
import com.local.questionbank.domain.repository.AiSettingsRepository
import com.local.questionbank.domain.repository.AnswerRepository
import com.local.questionbank.domain.repository.FavoriteRepository
import com.local.questionbank.domain.repository.QuestionBankRepository
import com.local.questionbank.domain.repository.QuestionRepository

/**
 * 手写依赖容器
 *
 * 为什么不引入 Hilt？
 *  - 项目规模小，4~5 个类，手写容器比 KSP 注解处理器更轻
 *  - 学习价值高，便于初学 Android DI 的人看清依赖关系
 *
 * 生命周期：
 *  - 在 [com.local.questionbank.QuestionBankApp.onCreate] 中初始化一次
 *  - 单例对象通过 lazy 委托按需创建
 */
class AppContainer(private val appContext: Context) {

    // ---------- 数据库层 ----------
    private val database: AppDatabase by lazy { AppDatabase.get(appContext) }

    private val bankDao by lazy { database.questionBankDao() }
    private val questionDao by lazy { database.questionDao() }
    private val answerRecordDao by lazy { database.answerRecordDao() }
    private val favoriteDao by lazy { database.favoriteDao() }

    // ---------- 领域仓库层 ----------
    val questionBankRepository: QuestionBankRepository by lazy {
        QuestionBankRepositoryImpl(database, bankDao, questionDao)
    }
    val questionRepository: QuestionRepository by lazy {
        QuestionRepositoryImpl(questionDao, favoriteDao, bankDao)
    }
    val answerRepository: AnswerRepository by lazy {
        AnswerRepositoryImpl(answerRecordDao)
    }
    val favoriteRepository: FavoriteRepository by lazy {
        FavoriteRepositoryImpl(favoriteDao)
    }

    // ---------- 数据源 ----------
    val jsonFileParser: JsonFileParser by lazy { JsonFileParser(appContext) }
    val csvFileParser: CsvFileParser by lazy { CsvFileParser(appContext) }

    // ---------- AI 助手 ----------
    val aiSettingsRepository: AiSettingsRepository by lazy {
        AiSettingsRepositoryImpl(appContext)
    }
    val aiAssistantRepository: AiAssistantRepository by lazy {
        AiAssistantRepositoryImpl(aiSettingsRepository, jsonFileParser)
    }
}

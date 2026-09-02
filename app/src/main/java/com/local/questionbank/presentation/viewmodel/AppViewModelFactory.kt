package com.local.questionbank.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.local.questionbank.di.AppContainer

/**
 * 通用 ViewModel 工厂
 *
 * 设计说明:
 *  - 不引入 Hilt,ViewModel 构造所需依赖由 [AppContainer] 提供
 *  - 使用 Map 缓存 [Class] → 构造器,避免在 Composable 中重复反射
 *  - ViewModel 构造器应声明为 `internal`,由本类同包内可见
 *
 * 用法:
 * ```
 * val vm: BankListViewModel = viewModel(
 *     factory = AppViewModelFactory(app.container)
 * )
 * ```
 */
class AppViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    private val creators: Map<Class<out ViewModel>, (AppContainer, Context) -> ViewModel> = mapOf(
        BankListViewModel::class.java to { c, _ -> BankListViewModel(c.questionBankRepository) },
        ImportViewModel::class.java to { c, ctx ->
            ImportViewModel(ctx, c.jsonFileParser, c.csvFileParser, c.questionBankRepository, c.aiAssistantRepository)
        },
        QuestionViewModel::class.java to { c, _ ->
            QuestionViewModel(
                questionRepository = c.questionRepository,
                answerRepository = c.answerRepository,
                favoriteRepository = c.favoriteRepository,
                aiAssistantRepository = c.aiAssistantRepository,
                aiSettingsRepository = c.aiSettingsRepository,
                questionBankRepository = c.questionBankRepository
            )
        },
        FavoriteListViewModel::class.java to { c, _ ->
            FavoriteListViewModel(
                questionRepository = c.questionRepository,
                favoriteRepository = c.favoriteRepository,
                questionBankRepository = c.questionBankRepository
            )
        },
        AiSettingsViewModel::class.java to { c, _ ->
            AiSettingsViewModel(c.aiSettingsRepository, c.aiAssistantRepository)
        },
        AiAssistViewModel::class.java to { c, _ ->
            AiAssistViewModel(c.aiAssistantRepository, c.questionBankRepository)
        },
        BankAiQuizViewModel::class.java to { c, _ ->
            BankAiQuizViewModel(
                questionRepository = c.questionRepository,
                questionBankRepository = c.questionBankRepository,
                aiAssistantRepository = c.aiAssistantRepository,
                aiSettingsRepository = c.aiSettingsRepository
            )
        }
    )

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val ctx = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Context
        val creator = creators[modelClass]
            ?: creators.entries.firstOrNull { modelClass.isAssignableFrom(it.key) }?.value
            ?: error("未注册 ViewModel: ${modelClass.name}")
        return creator(container, ctx) as T
    }
}
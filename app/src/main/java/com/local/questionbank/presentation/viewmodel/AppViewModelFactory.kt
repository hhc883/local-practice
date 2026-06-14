package com.local.questionbank.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.local.questionbank.di.AppContainer

/**
 * 通用 ViewModel 工厂
 *
 * 设计说明：
 *  - 不引入 Hilt，ViewModel 构造所需依赖由 [AppContainer] 提供
 *  - 使用 Map 缓存 [Class] → 构造器，避免在 Composable 中重复反射
 *  - ViewModel 构造器应声明为 `internal`，由本类同包内可见
 *
 * 用法：
 * ```
 * val vm: BankListViewModel = viewModel(
 *     factory = AppViewModelFactory(app.container)
 * )
 * ```
 */
class AppViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    private val creators: Map<Class<out ViewModel>, (AppContainer) -> ViewModel> = mapOf(
        BankListViewModel::class.java to { c -> BankListViewModel(c.questionBankRepository) },
        ImportViewModel::class.java to { c ->
            ImportViewModel(c.jsonFileParser, c.questionBankRepository)
        },
        QuestionViewModel::class.java to { c ->
            QuestionViewModel(
                questionRepository = c.questionRepository,
                answerRepository = c.answerRepository,
                favoriteRepository = c.favoriteRepository
            )
        },
        FavoriteListViewModel::class.java to { c ->
            FavoriteListViewModel(
                questionRepository = c.questionRepository,
                favoriteRepository = c.favoriteRepository
            )
        }
    )

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val creator = creators[modelClass]
            ?: creators.entries.firstOrNull { modelClass.isAssignableFrom(it.key) }?.value
            ?: error("未注册 ViewModel: ${modelClass.name}")
        return creator(container) as T
    }
}

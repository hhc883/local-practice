package com.local.questionbank.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.local.questionbank.di.AppContainer
import com.local.questionbank.presentation.screen.BankListScreen
import com.local.questionbank.presentation.screen.FavoriteListScreen
import com.local.questionbank.presentation.screen.ImportScreen
import com.local.questionbank.presentation.screen.QuestionScreen
import com.local.questionbank.presentation.viewmodel.AppViewModelFactory
import com.local.questionbank.presentation.viewmodel.FavoriteListViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * AppNavGraph
 *
 * - 单 Activity + 单 NavHost
 * - 路由常量集中在 [Routes] 中维护
 */
@Composable
fun AppNavGraph(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.BANK_LIST
    ) {
        composable(Routes.BANK_LIST) {
            BankListScreen(
                container = container,
                onAddBank = { navController.navigate(Routes.IMPORT) },
                onStartPractice = { bankId, mode ->
                    navController.navigate(Routes.question(bankId, mode))
                },
                // 收藏夹 / 错题本：传 bankId=0，由 ViewModel 走"全局"逻辑
                onOpenFavorites = { navController.navigate(Routes.FAVORITE_LIST) },
                onOpenWrongBook = { navController.navigate(Routes.question(0L, "WRONG_ONLY")) }
            )
        }
        composable(Routes.FAVORITE_LIST) {
            val viewModel: FavoriteListViewModel = viewModel(
                factory = AppViewModelFactory(container)
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            FavoriteListScreen(
                uiState = state,
                onNavigateBack = { navController.popBackStack() },
                onDeleteFavorite = viewModel::deleteFavorite,
                onUndoDelete = viewModel::undoDelete,
                onQuestionClick = { question ->
                    navController.navigate(Routes.question(question.bankId, "FAVORITE_ONLY"))
                }
            )
        }
        composable(Routes.IMPORT) {
            ImportScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onImported = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.QUESTION_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_BANK_ID) { type = NavType.LongType },
                navArgument(Routes.ARG_MODE) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bankId = backStackEntry.arguments?.getLong(Routes.ARG_BANK_ID) ?: 0L
            val mode = backStackEntry.arguments?.getString(Routes.ARG_MODE) ?: "ORDERED"
            QuestionScreen(
                container = container,
                bankId = bankId,
                modeName = mode,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

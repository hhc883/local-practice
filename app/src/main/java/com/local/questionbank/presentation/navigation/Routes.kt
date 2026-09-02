package com.local.questionbank.presentation.navigation

/**
 * 路由常量集中管理，避免散落的 magic string
 */
object Routes {
    const val BANK_LIST = "bank_list"
    const val IMPORT = "import"
    const val QUESTION_PATTERN = "question/{bankId}/{mode}"
    const val FAVORITE_LIST = "favorite_list"
    const val AI_SETTINGS = "ai_settings"
    const val AI_HUB = "ai_hub"
    const val BANK_AI_QUIZ_PATTERN = "bank_ai_quiz/{bankId}/{bankName}"
    const val ARG_BANK_ID = "bankId"
    const val ARG_BANK_NAME = "bankName"
    const val ARG_MODE = "mode"

    fun question(bankId: Long, mode: String): String =
        "question/$bankId/$mode"

    fun bankAiQuiz(bankId: Long, bankName: String): String =
        "bank_ai_quiz/$bankId/${java.net.URLEncoder.encode(bankName, "UTF-8")}"
}

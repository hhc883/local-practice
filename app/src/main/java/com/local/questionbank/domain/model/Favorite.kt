package com.local.questionbank.domain.model

/**
 * 领域层 - 收藏
 */
data class Favorite(
    val id: Long = 0L,
    val questionId: Long,
    val tag: String = "默认",
    val createTimestamp: Long = System.currentTimeMillis()
)

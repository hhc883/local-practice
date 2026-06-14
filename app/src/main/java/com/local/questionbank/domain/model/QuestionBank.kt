package com.local.questionbank.domain.model

/**
 * 领域层 - 题库
 *
 * 与 Room Entity 解耦：
 *  - Entity 只用于持久化，关注表结构
 *  - 模型用于业务流转与 UI 展示，关注语义
 */
data class QuestionBank(
    val id: Long = 0L,
    val name: String,
    val description: String? = null,
    val createTimestamp: Long = System.currentTimeMillis(),
    val questions: List<Question> = emptyList()
)

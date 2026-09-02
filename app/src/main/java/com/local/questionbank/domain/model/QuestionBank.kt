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
    /** 用户拖拽排序索引；值越小排位越靠前。0 表示未指定（导入新题时由仓库层补值）。 */
    val sortIndex: Long = 0L,
    val questions: List<Question> = emptyList()
)

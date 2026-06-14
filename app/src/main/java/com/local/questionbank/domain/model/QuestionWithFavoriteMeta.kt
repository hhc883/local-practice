package com.local.questionbank.domain.model

/**
 * 收藏题目的元数据封装
 *
 * [Question] 本身不包含收藏时间和标签，这两个字段来自 [com.local.questionbank.data.database.entity.FavoriteEntity]。
 * 本类型用于在 Repository 层将两者绑定后传递给 UI，避免 UI 层直接依赖 Entity。
 *
 * @param question           题目领域模型
 * @param favoriteTimestamp  收藏时间（来自 FavoriteEntity.createTimestamp）
 * @param tag                收藏标签（来自 FavoriteEntity.tag）
 */
data class QuestionWithFavoriteMeta(
    val question: Question,
    val favoriteTimestamp: Long,
    val tag: String
)
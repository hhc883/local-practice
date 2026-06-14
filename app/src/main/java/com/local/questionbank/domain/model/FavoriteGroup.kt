package com.local.questionbank.domain.model

/**
 * 按题库分组的收藏视图模型
 *
 * @param bank              题库信息
 * @param favorites         该题库下收藏的题目（按收藏时间倒序）
 * @param latestFavoriteTimestamp 该题库下最近一次收藏的时间戳（用于整组排序）
 */
data class FavoriteGroup(
    val bank: QuestionBank,
    val favorites: List<Question>,
    val latestFavoriteTimestamp: Long
)
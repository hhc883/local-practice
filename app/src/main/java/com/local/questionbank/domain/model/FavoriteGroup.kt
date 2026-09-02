package com.local.questionbank.domain.model

/**
 * 按题库分组的收藏视图模型
 *
 * @param bank                     题库信息
 * @param favorites                该题库下收藏的题目（按 sortIndex 升序，最先拖到的在最上）
 * @param favoriteDbIds            与 [favorites] 一一对应的 favorite 表自增 id（用于拖拽落库）
 * @param latestFavoriteTimestamp  该题库下最近一次收藏的时间戳（备用，目前 UI 不显示）
 */
data class FavoriteGroup(
    val bank: QuestionBank,
    val favorites: List<Question>,
    val favoriteDbIds: List<Long>,
    val latestFavoriteTimestamp: Long
)
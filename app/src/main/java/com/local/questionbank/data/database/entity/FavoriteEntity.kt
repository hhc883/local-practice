package com.local.questionbank.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 收藏表
 *
 * 字段说明：
 *  - [id]          自增主键（仅用于按收藏时间排序）
 *  - [questionId]  题目 id（联合索引，保证同题不会重复收藏）
 *  - [tag]         自定义标签，可空。预置值："默认" / "重点" / "易错"
 *  - [createTimestamp] 收藏时间戳（毫秒）
 *  - [sortIndex]   收藏内题目的拖拽排序用；值越小越靠前（v4 新增）
 *
 * 设计取舍：
 *  - 用 questionId 单列建 unique 索引避免重复收藏行写入
 *  - tag 留作扩展点（按 tag 分组、按 tag 过滤），目前 UI 暂只用"默认"
 *  - sortIndex 与 questionId 不同：sortIndex 用于收藏内排序，questionId 用于全局收藏排序
 */
@Entity(
    tableName = "favorite",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["questionId"], unique = true),
        Index("createTimestamp"),
        Index("sortIndex")
    ]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val questionId: Long,
    val tag: String = "默认",
    val createTimestamp: Long,
    /**
     * 收藏内拖拽排序用。值越小排位越靠前。
     * 迁移 v3→v4 时由 SQL 用 createTimestamp 初始化（保证原有时间顺序）；
     * 新收藏的默认 sortIndex 由 Repository 取 max + 1000 追加到末尾。
     *
     * [ColumnInfo.defaultValue] 必须与迁移 SQL `DEFAULT 0` 保持一致，
     * 否则 Room schema 校验会失败。
     */
    @ColumnInfo(defaultValue = "0")
    val sortIndex: Long = 0L
)

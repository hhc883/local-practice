package com.local.questionbank.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 题库表
 *
 * 字段说明：
 *  - [id]          自增主键，跨设备无意义，仅用于 Room 内部关联
 *  - [name]        题库名称，必填，最长 200（由导入 JSON 的 bankName 提供）
 *  - [description] 题库描述，可空
 *  - [createTimestamp] 创建时间戳（毫秒，System.currentTimeMillis()）
 *  - [sortIndex]   拖拽排序用，值越小越靠前；迁移 v2→v3 时由 SQL 填入 id * 1000
 */
@Entity(
    tableName = "question_bank",
    indices = [Index(value = ["sortIndex"])]
)
data class QuestionBankEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val description: String? = null,
    val createTimestamp: Long,
    /**
     * 用户拖拽排序用的索引。值越小排位越靠前。
     * 迁移 v2→v3 时由 SQL 计算填入（id * 1000），保证间隔足够大以便后续插入。
     *
     * [ColumnInfo.defaultValue] 必须与迁移 SQL 中的 `DEFAULT 0` 保持一致，
     * 否则 Room 的 schema 校验会判定迁移失败（`Migration didn't properly handle`）。
     */
    @ColumnInfo(defaultValue = "0")
    val sortIndex: Long = 0L
)

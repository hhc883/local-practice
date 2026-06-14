package com.local.questionbank.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 题库表
 *
 * 字段说明：
 *  - [id]          自增主键，跨设备无意义，仅用于 Room 内部关联
 *  - [name]        题库名称，必填，最长 200（由导入 JSON 的 bankName 提供）
 *  - [description] 题库描述，可空
 *  - [createTimestamp] 创建时间戳（毫秒，System.currentTimeMillis()）
 */
@Entity(tableName = "question_bank")
data class QuestionBankEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val description: String? = null,
    val createTimestamp: Long
)

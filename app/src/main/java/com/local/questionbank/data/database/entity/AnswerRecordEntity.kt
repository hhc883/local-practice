package com.local.questionbank.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 答题记录表（每答一次写一行，支持「错题本」「最近刷题」等查询）
 *
 * 设计要点：
 *  - 不与 Question 强主外键，但保留 questionId 便于 Room 自动加索引
 *  - 同一题目多次答题会插入多条记录，由上层按 questionId 聚合
 */
@Entity(
    tableName = "answer_record",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("questionId"),
        Index("answerTimestamp")
    ]
)
data class AnswerRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val questionId: Long,
    val isCorrect: Boolean,
    val answerTimestamp: Long
)

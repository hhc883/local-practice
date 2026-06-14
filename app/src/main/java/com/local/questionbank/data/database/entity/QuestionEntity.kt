package com.local.questionbank.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 题目表
 *
 * 与 [QuestionBankEntity] 通过 [bankId] 关联，级联删除保证题库被删时题目同步清空。
 *
 * 字段说明：
 *  - [type]         题目类型，字符串存储，值域见 [com.local.questionbank.domain.model.QuestionType]
 *  - [title]        题干（Markdown 文本可选）
 *  - [optionsJson]  选项数组的 JSON 字符串，例如 ["A", "B", "C", "D"]
 *  - [answerJson]   正确答案下标数组的 JSON 字符串，例如 ["1", "3"]
 *  - [analysis]     答案解析，可空
 *
 * 为什么不直接存 List<String>？Room 原生不支持集合类型，序列化 JSON 是最简单且
 * 可被外部工具（如 adb shell 抽 sqlite）直接读取的方案。
 */
@Entity(
    tableName = "question",
    foreignKeys = [
        ForeignKey(
            entity = QuestionBankEntity::class,
            parentColumns = ["id"],
            childColumns = ["bankId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bankId")]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bankId: Long,
    val type: String,
    val title: String,
    val optionsJson: String,
    val answerJson: String,
    val analysis: String? = null
)

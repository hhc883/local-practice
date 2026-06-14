package com.local.questionbank.domain.model

/**
 * 领域层 - 答题记录
 *
 * 一题一记录，多次作答可产生多行；上层若要"最近一次"应取 [answerTimestamp] 最大的那条
 */
data class AnswerRecord(
    val id: Long = 0L,
    val questionId: Long,
    val isCorrect: Boolean,
    val answerTimestamp: Long = System.currentTimeMillis()
)

package com.local.questionbank.domain.repository

import com.local.questionbank.data.datasource.JsonDiff
import com.local.questionbank.domain.model.Question

/**
 * AI 助手仓库
 *
 * 聚合 SiliconFlow API 调用 + Prompt 拼接 + JSON 二次校验,
 * 把"调用 AI → 校验 → 返回领域对象"的流程封装成两个高层方法。
 */
interface AiAssistantRepository {

    /**
     * 修复 JSON(返回结构化结果,含 diff 与错误分类)
     *
     * @param rawJson 用户原本尝试导入但失败的 JSON 文本
     * @param errorMessage 解析时的错误描述
     * @return [FixResult] 包含成功/失败状态、修复后 JSON、字段 diff 列表、失败时建议
     */
    suspend fun fixJson(rawJson: String, errorMessage: String): FixResult

    /**
     * 基于当前题生成 1 道同知识点新题
     *
     * @return 生成的新题(可直接作为 Question 入库)
     * @throws AiAssistException API key 缺失 / AI 调用失败 / 返回内容无法通过校验
     */
    suspend fun generateSimilarQuestion(current: Question): Question

    /**
     * 测试连接是否通畅
     *
     * 用 max_tokens=1 调一次最小请求,返回非空 content 即视为成功。
     * 任何网络/认证/格式错误抛 [AiAssistException]。
     */
    suspend fun testConnection(): String
}

/**
 * AI 修复的结构化结果
 *
 * @property success 是否成功
 * @property fixedJson 成功时:修复后 JSON 字符串;失败时为 null
 * @property diff 修复前后的字段差异(成功时),UI 用来告诉用户"AI 改了哪几处"
 * @property errorMessage 失败时:原始错误文本
 * @property errorSuggestion 失败时:分类后的可读建议(如"请检查网络")
 */
data class FixResult(
    val success: Boolean,
    val fixedJson: String? = null,
    val diff: List<com.local.questionbank.data.datasource.DiffEntry> = emptyList(),
    val errorMessage: String? = null,
    val errorSuggestion: String? = null
)

/** AI 助手相关的业务异常 */
class AiAssistException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
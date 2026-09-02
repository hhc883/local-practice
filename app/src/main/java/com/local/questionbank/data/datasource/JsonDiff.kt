package com.local.questionbank.data.datasource

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * 简易 JSON 差异对比工具
 *
 * 用途:AI 修复 JSON 后,把修复前/后的两段 JSON 解析成 Map 树,
 *      递归对比,输出"修改了哪几个字段、字段值从 X 变 Y"。
 *
 * 设计取舍:
 *  - 不引入 json-diff 库(避免 ~500KB 依赖)
 *  - 用 Moshi 解析(项目已用)
 *  - 输出可读 path("questions[15].answer") + 类型描述("MODIFIED")
 *  - 限制 maxEntries,避免嵌套深时输出爆炸
 */
data class DiffEntry(
    /** 字段路径,如 "questions[15].answer" 或 "bankName" */
    val path: String,
    val kind: Kind,
    /** 修改前的值(字符串形式);新增时为 null */
    val before: String?,
    /** 修改后的值(字符串形式);删除时为 null */
    val after: String?,
    /** 人类可读描述,如 "字符串 → 数组" */
    val description: String
) {
    enum class Kind { MODIFIED, ADDED, REMOVED, TYPE_CHANGED }
}

object JsonDiff {

    private val moshi: Moshi = Moshi.Builder().build()
    private val mapAdapter: JsonAdapter<Map<String, Any?>> =
        moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

    /**
     * 对比两个 JSON 字符串(失败时返回空列表,不影响调用方)
     */
    fun diff(beforeJson: String, afterJson: String, maxEntries: Int = 20): List<DiffEntry> {
        val before = parseMap(beforeJson) ?: return emptyList()
        val after = parseMap(afterJson) ?: return emptyList()
        val result = mutableListOf<DiffEntry>()
        diffMap(before, after, "", result)
        return if (result.size > maxEntries) result.take(maxEntries) else result
    }

    /**
     * 把 diff 条目格式化为可读文本,前 N 条 + 省略提示
     *
     * 例:
     * ```
     * 修改了 3 处:
     * ● questions[15].answer:字符串 → 数组
     * ● questions[15].type:READ → BLANK
     * ● bankName:"" → Java 习题7
     * ```
     */
    fun formatForDisplay(entries: List<DiffEntry>): String {
        if (entries.isEmpty()) return "未发现字段差异(可能 AI 未做改动)"
        val sb = StringBuilder()
        sb.appendLine("修改了 ${entries.size} 处:")
        entries.forEach { e ->
            sb.append("● ")
            sb.append(e.path)
            sb.append(":")
            sb.append(e.description)
            sb.appendLine()
        }
        return sb.toString().trimEnd()
    }

    // ---------------- 内部 ----------------

    private fun parseMap(json: String): Map<String, Any?>? = try {
        mapAdapter.fromJson(json)
    } catch (e: Exception) {
        null
    }

    private fun diffMap(
        before: Map<String, Any?>,
        after: Map<String, Any?>,
        parentPath: String,
        out: MutableList<DiffEntry>
    ) {
        val allKeys = (before.keys + after.keys).toSet()
        for (key in allKeys.sorted()) {
            val b = before[key]
            val a = after[key]
            val path = if (parentPath.isEmpty()) key else "$parentPath.$key"
            when {
                b == null && a != null -> {
                    out += DiffEntry(
                        path = path, kind = DiffEntry.Kind.ADDED,
                        before = null, after = shortValue(a),
                        description = "新增:${shortValue(a)}"
                    )
                }
                b != null && a == null -> {
                    out += DiffEntry(
                        path = path, kind = DiffEntry.Kind.REMOVED,
                        before = shortValue(b), after = null,
                        description = "删除:${shortValue(b)}"
                    )
                }
                b != null && a != null -> diffValue(b, a, path, out)
                // b == null && a == null 不会出现(已合并)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun diffValue(
        before: Any?,
        after: Any?,
        path: String,
        out: MutableList<DiffEntry>
    ) {
        if (before == null || after == null) return  // 上面已处理 null 情况
        // 类型不同 → TYPE_CHANGED
        if (before.javaClass != after.javaClass) {
            out += DiffEntry(
                path = path, kind = DiffEntry.Kind.TYPE_CHANGED,
                before = shortValue(before), after = shortValue(after),
                description = "类型变化:${before.javaClass.simpleName} → ${after.javaClass.simpleName}"
            )
            return
        }
        when (before) {
            is Map<*, *> -> diffMap(before as Map<String, Any?>, after as Map<String, Any?>, path, out)
            is List<*> -> {
                @Suppress("UNCHECKED_CAST")
                diffList(before, after as List<*>, path, out)
            }
            else -> {
                if (before != after) {
                    out += DiffEntry(
                        path = path, kind = DiffEntry.Kind.MODIFIED,
                        before = shortValue(before), after = shortValue(after),
                        description = "${shortValue(before)} → ${shortValue(after)}"
                    )
                }
                // 相等 → 跳过
            }
        }
    }

    private fun diffList(
        before: List<*>,
        after: List<*>,
        parentPath: String,
        out: MutableList<DiffEntry>
    ) {
        val maxLen = maxOf(before.size, after.size)
        for (i in 0 until maxLen) {
            val path = "$parentPath[$i]"
            val b = before.getOrNull(i)
            val a = after.getOrNull(i)
            when {
                b == null && a != null -> out += DiffEntry(
                    path = path, kind = DiffEntry.Kind.ADDED,
                    before = null, after = shortValue(a),
                    description = "[新增] ${shortValue(a)}"
                )
                b != null && a == null -> out += DiffEntry(
                    path = path, kind = DiffEntry.Kind.REMOVED,
                    before = shortValue(b), after = null,
                    description = "[删除] ${shortValue(b)}"
                )
                b != null && a != null -> diffValue(b, a, path, out)
            }
        }
    }

    /**
     * 把 Any? 截断成 80 字符内的可读字符串
     * - String 保留 60 字符
     * - List/Map 显示长度
     * - null 显示 "null"
     */
    private fun shortValue(v: Any?): String {
        if (v == null) return "null"
        val s = when (v) {
            is String -> "\"${v.take(60)}${if (v.length > 60) "…" else ""}\""
            is List<*> -> "[${v.size} 项]"
            is Map<*, *> -> "{${v.size} 字段}"
            else -> v.toString().take(60)
        }
        return s
    }
}
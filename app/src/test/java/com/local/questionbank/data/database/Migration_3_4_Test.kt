package com.local.questionbank.data.database

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Room Migration 3 → 4 行为单测
 *
 * 与 Migration_2_3_Test 同样的 sqlite-jdbc 重放策略：
 *  - 用内存 SQLite 手写 v3 schema
 *  - 插入几行 favorite(覆盖 createTimestamp 不同)
 *  - 重放 [Migrations.MIGRATION_3_4_SQL]
 *  - 校验 sortIndex 列、索引、按 createTimestamp 顺序填充
 */
class Migration_3_4_Test {

    private lateinit var conn: Connection

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }

        // 准备 v3 schema：favorite 表无 sortIndex 列
        conn.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE `favorite` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `questionId` INTEGER NOT NULL,
                    `tag` TEXT NOT NULL DEFAULT '默认',
                    `createTimestamp` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            stmt.execute("CREATE UNIQUE INDEX `index_favorite_questionId` ON `favorite` (`questionId`)")
            stmt.execute("CREATE INDEX `index_favorite_createTimestamp` ON `favorite` (`createTimestamp`)")
        }

        // 插入三行不同收藏时间
        conn.prepareStatement(
            "INSERT INTO favorite (id, questionId, tag, createTimestamp) VALUES (?, ?, ?, ?)"
        ).use { ps ->
            ps.setLong(1, 1); ps.setLong(2, 100); ps.setString(3, "默认"); ps.setLong(4, 1000)
            ps.executeUpdate()
            ps.setLong(1, 2); ps.setLong(2, 200); ps.setString(3, "重点"); ps.setLong(4, 3000)
            ps.executeUpdate()
            ps.setLong(1, 3); ps.setLong(2, 300); ps.setString(3, "默认"); ps.setLong(4, 2000)
            ps.executeUpdate()
        }
    }

    @After
    fun tearDown() {
        conn.close()
    }

    @Test
    fun `migration adds sortIndex column filled by createTimestamp order`() {
        // 重放迁移 SQL
        conn.createStatement().use { stmt ->
            Migrations.MIGRATION_3_4_SQL.forEach { stmt.execute(it) }
        }

        // 每行 sortIndex 都应该是 1000 的倍数,且互不相同
        val pairs = conn.prepareStatement("SELECT id, sortIndex FROM favorite ORDER BY id ASC")
            .executeQuery().use { rs ->
                val out = mutableListOf<Pair<Long, Long>>()
                while (rs.next()) out.add(rs.getLong(1) to rs.getLong(2))
                out
            }
        assertEquals(3, pairs.size)
        // ROW_NUMBER 顺序（createTimestamp ASC, id ASC）:
        //   id=1 (1000ms) → rn=1 → sortIndex=1000
        //   id=3 (2000ms) → rn=2 → sortIndex=2000
        //   id=2 (3000ms) → rn=3 → sortIndex=3000
        // ORDER BY id ASC 的输出按 id 升序，所以 sortIndex 不是单调
        assertEquals(listOf(1L to 1000L, 2L to 3000L, 3L to 2000L), pairs)
    }

    @Test
    fun `migration creates index on sortIndex`() {
        conn.createStatement().use { stmt ->
            Migrations.MIGRATION_3_4_SQL.forEach { stmt.execute(it) }
        }
        val idxNames = mutableListOf<String>()
        conn.createStatement().executeQuery("PRAGMA index_list('favorite')").use { rs ->
            val nameCol = rs.findColumn("name")
            while (rs.next()) idxNames.add(rs.getString(nameCol))
        }
        assertTrue(
            "应存在 index_favorite_sortIndex, 实际: $idxNames",
            "index_favorite_sortIndex" in idxNames
        )
    }

    @Test
    fun `migrated database supports ORDER BY sortIndex ASC preserving createTimestamp order`() {
        conn.createStatement().use { stmt ->
            Migrations.MIGRATION_3_4_SQL.forEach { stmt.execute(it) }
        }
        val questionIds = conn.prepareStatement(
            "SELECT questionId FROM favorite ORDER BY sortIndex ASC"
        ).executeQuery().use { rs ->
            val out = mutableListOf<Long>()
            while (rs.next()) out.add(rs.getLong(1))
            out
        }
        // createTimestamp 升序: 100 → 300 → 200
        assertEquals(listOf(100L, 300L, 200L), questionIds)
    }
}

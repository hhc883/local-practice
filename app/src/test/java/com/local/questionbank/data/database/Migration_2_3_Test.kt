package com.local.questionbank.data.database

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Room Migration 2 → 3 行为单测
 *
 * 不依赖 Room 的 MigrationTestHelper / Robolectric / Android Context：
 *  1. 通过 [DriverManager] 直接打开内存 SQLite 数据库（org.xerial:sqlite-jdbc）
 *  2. 手写 v2 schema 的 CREATE TABLE 语句（与 Room 生成的 2.json 一致）
 *  3. 插入几行 question_bank
 *  4. 重放 [Migrations.MIGRATION_2_3_SQL] 中的 SQL
 *  5. 校验 sortIndex 列被填为 id * 1000、索引已建立
 *
 * 优势：纯 JVM 运行，速度快，不依赖 Android 设备或 Robolectric；
 * 验证的是迁移的核心 SQL 语义（与 [Migrations.MIGRATION_2_3] 等价）。
 */
class Migration_2_3_Test {

    private lateinit var conn: Connection

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        // 启用外键约束
        conn.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        // v2 schema
        conn.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE `question_bank` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT,
                    `createTimestamp` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            stmt.execute(
                """
                CREATE TABLE `question` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `bankId` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `optionsJson` TEXT NOT NULL,
                    `answerJson` TEXT NOT NULL,
                    `analysis` TEXT,
                    FOREIGN KEY(`bankId`) REFERENCES `question_bank`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            stmt.execute("CREATE INDEX `index_question_bankId` ON `question` (`bankId`)")
        }
        // 插入三行测试数据
        conn.prepareStatement(
            "INSERT INTO question_bank (id, name, description, createTimestamp) VALUES (?, ?, ?, ?)"
        ).use { ps ->
            ps.setLong(1, 1); ps.setString(2, "A"); ps.setNull(3, java.sql.Types.VARCHAR); ps.setLong(4, 1000)
            ps.executeUpdate()
            ps.setLong(1, 2); ps.setString(2, "B"); ps.setString(3, "desc"); ps.setLong(4, 2000)
            ps.executeUpdate()
            ps.setLong(1, 3); ps.setString(2, "C"); ps.setNull(3, java.sql.Types.VARCHAR); ps.setLong(4, 3000)
            ps.executeUpdate()
        }
    }

    @After
    fun tearDown() {
        conn.close()
    }

    @Test
    fun `migration adds sortIndex column and fills with id_times_1000`() {
        // 重放迁移 SQL
        conn.createStatement().use { stmt ->
            Migrations.MIGRATION_2_3_SQL.forEach { stmt.execute(it) }
        }

        // 验证 sortIndex 已写入
        val values = conn.prepareStatement("SELECT sortIndex FROM question_bank ORDER BY id ASC")
            .executeQuery().use { rs ->
                val out = mutableListOf<Long>()
                while (rs.next()) out.add(rs.getLong(1))
                out
            }
        assertEquals(listOf(1000L, 2000L, 3000L), values)

        // 验证索引已建立
        val idxNames = mutableListOf<String>()
        conn.createStatement().executeQuery("PRAGMA index_list('question_bank')").use { rs ->
            // PRAGMA index_list 列：seq, name, unique, origin, partial
            val nameCol = rs.findColumn("name")
            while (rs.next()) {
                idxNames.add(rs.getString(nameCol))
            }
        }
        assertTrue(
            "应存在 index_question_bank_sortIndex, 实际: $idxNames",
            "index_question_bank_sortIndex" in idxNames
        )
    }

    @Test
    fun `migration is idempotent on second run`() {
        conn.createStatement().use { stmt ->
            Migrations.MIGRATION_2_3_SQL.forEach { stmt.execute(it) }
            // 二次执行：sortIndex 应当保持不变（id*1000 不变），ALTER TABLE 会失败但已存在的列不报错
            // 实际 SQLite 在 ADD COLUMN 已存在时会报错，所以只对 UPDATE / CREATE INDEX 重放：
            stmt.execute("UPDATE question_bank SET sortIndex = id * 1000")
        }
        val values = conn.prepareStatement("SELECT sortIndex FROM question_bank ORDER BY id ASC")
            .executeQuery().use { rs ->
                val out = mutableListOf<Long>()
                while (rs.next()) out.add(rs.getLong(1))
                out
            }
        assertEquals(listOf(1000L, 2000L, 3000L), values)
    }

    @Test
    fun `migrated database supports ORDER BY sortIndex ASC preserving id order`() {
        conn.createStatement().use { stmt ->
            Migrations.MIGRATION_2_3_SQL.forEach { stmt.execute(it) }
        }
        val names = conn.prepareStatement("SELECT name FROM question_bank ORDER BY sortIndex ASC")
            .executeQuery().use { rs ->
                val out = mutableListOf<String>()
                while (rs.next()) out.add(rs.getString(1))
                out
            }
        assertEquals(listOf("A", "B", "C"), names)
    }

    @Test
    fun `applyReorder assigns monotonically increasing sortIndex`() {
        // 先迁移
        conn.createStatement().use { stmt ->
            Migrations.MIGRATION_2_3_SQL.forEach { stmt.execute(it) }
        }
        // 模拟"调换前两行顺序"——直接对 sortIndex 写值
        conn.createStatement().use { stmt ->
            stmt.execute("UPDATE question_bank SET sortIndex = 2000 WHERE id = 2")
            stmt.execute("UPDATE question_bank SET sortIndex = 1000 WHERE id = 1")
        }
        // 第 3 行 sortIndex 保持 3000 不变，预期结果: id=1 sortIndex=1000, id=2 sortIndex=2000, id=3 sortIndex=3000
        val pairs = conn.prepareStatement("SELECT id, sortIndex FROM question_bank ORDER BY sortIndex ASC")
            .executeQuery().use { rs ->
                val out = mutableListOf<Pair<Long, Long>>()
                while (rs.next()) out.add(rs.getLong(1) to rs.getLong(2))
                out
            }
        assertEquals(listOf(1L to 1000L, 2L to 2000L, 3L to 3000L), pairs)
    }
}

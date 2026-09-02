package com.local.questionbank.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.local.questionbank.data.database.dao.AnswerRecordDao
import com.local.questionbank.data.database.dao.FavoriteDao
import com.local.questionbank.data.database.dao.QuestionBankDao
import com.local.questionbank.data.database.dao.QuestionDao
import com.local.questionbank.data.database.entity.AnswerRecordEntity
import com.local.questionbank.data.database.entity.FavoriteEntity
import com.local.questionbank.data.database.entity.QuestionBankEntity
import com.local.questionbank.data.database.entity.QuestionEntity

/**
 * AppDatabase（Room 单例）
 *
 * - v1 → v2：新增 favorite 表
 * - v2 → v3：question_bank 新增 sortIndex 列 + 索引（首页长按拖拽排序）
 * - v3 → v4：favorite 新增 sortIndex 列 + 索引（收藏页长按拖拽排序）
 * - 升级策略改为显式 Migration；移除 fallbackToDestructiveMigration，
 *   避免新版本发布后误删用户已导入的题库
 */
@Database(
    entities = [
        QuestionBankEntity::class,
        QuestionEntity::class,
        AnswerRecordEntity::class,
        FavoriteEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun questionBankDao(): QuestionBankDao
    abstract fun questionDao(): QuestionDao
    abstract fun answerRecordDao(): AnswerRecordDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        private const val DB_NAME = "question_bank.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .addMigrations(
                    Migrations.MIGRATION_1_2,
                    Migrations.MIGRATION_2_3,
                    Migrations.MIGRATION_3_4,
                    Migrations.MIGRATION_4_5
                )
                // 移除 destructive 迁移：若以后忘记加 Migration，会直接抛 IllegalStateException
                // 提醒开发者补齐迁移脚本，而不是偷偷清库
                .build()
        }
    }
}

/**
 * AppDatabase 显式迁移集合
 *
 * 提取到顶层对象，便于 JVM 单测直接通过 [Migrations.MIGRATION_2_3] 引用做 MigrationTestHelper 验证。
 */
object Migrations {
    /**
     * v1 → v2
     * 新增 favorite 表
     *
     * SQL 设计与 [com.local.questionbank.data.database.entity.FavoriteEntity]
     * 完全一致；如后续表结构变更，请同步新增 MIGRATION_2_3 等
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `favorite` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `questionId` INTEGER NOT NULL,
                    `tag` TEXT NOT NULL DEFAULT '默认',
                    `createTimestamp` INTEGER NOT NULL,
                    FOREIGN KEY(`questionId`) REFERENCES `question`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_questionId` ON `favorite` (`questionId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_favorite_createTimestamp` ON `favorite` (`createTimestamp`)"
            )
        }
    }

    /**
     * v2 → v3
     * 新增 question_bank.sortIndex 列 + 索引；用 id * 1000 初始化，保留原 ID 顺序
     * 同时留出插入空间给用户拖拽后插入新题。
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_2_3_SQL.forEach { db.execSQL(it) }
        }
    }

    /**
     * v2 → v3 的纯 SQL 列表（按顺序执行）。
     * 暴露为 public 以便 JVM 单测可直接用 sqlite-jdbc 重放，不必走 Migration。
     */
    val MIGRATION_2_3_SQL: List<String> = listOf(
        "ALTER TABLE question_bank ADD COLUMN sortIndex INTEGER NOT NULL DEFAULT 0",
        "UPDATE question_bank SET sortIndex = id * 1000",
        "CREATE INDEX IF NOT EXISTS `index_question_bank_sortIndex` ON `question_bank` (`sortIndex`)"
    )

    /**
     * v3 → v4
     * 新增 favorite.sortIndex 列 + 索引；用 createTimestamp 初始化为递减的 sortIndex，
     * 保留原有的"最新收藏在最上"顺序（收藏时间越晚，sortIndex 越大）。
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_3_4_SQL.forEach { db.execSQL(it) }
        }
    }

    /**
     * v3 → v4 的纯 SQL 列表。
     * 暴露为 public 以便 JVM 单测可直接用 sqlite-jdbc 重放。
     */
    val MIGRATION_3_4_SQL: List<String> = listOf(
        "ALTER TABLE favorite ADD COLUMN sortIndex INTEGER NOT NULL DEFAULT 0",
        // 用 createTimestamp 排序后给每行一个唯一 sortIndex(递增 1000)
        // ROW_NUMBER 反映"按收藏时间升序"的次序,再用此序填充 sortIndex
        // 这样原有"最新收藏在最上"通过 sortIndex DESC 自然保留
        """
        UPDATE favorite SET sortIndex = (
            SELECT (rn * 1000) FROM (
                SELECT id, ROW_NUMBER() OVER (ORDER BY createTimestamp ASC, id ASC) AS rn
                FROM favorite
            ) WHERE favorite.id = id
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_favorite_sortIndex` ON `favorite` (`sortIndex`)"
    )

    /**
     * v4 → v5
     * question 表新增 codeSnippet 列(题面附加代码片段)
     * TEXT 类型,可空,默认 null
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE question ADD COLUMN codeSnippet TEXT")
        }
    }
}

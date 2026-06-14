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
 * - 升级策略改为显式 [MIGRATION_1_2]；移除 fallbackToDestructiveMigration，
 *   避免新版本发布后误删用户已导入的题库
 */
@Database(
    entities = [
        QuestionBankEntity::class,
        QuestionEntity::class,
        AnswerRecordEntity::class,
        FavoriteEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun questionBankDao(): QuestionBankDao
    abstract fun questionDao(): QuestionDao
    abstract fun answerRecordDao(): AnswerRecordDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        private const val DB_NAME = "question_bank.db"

        /**
         * v1 → v2
         * 新增 favorite 表
         *
         * SQL 设计与 [com.local.questionbank.data.database.entity.FavoriteEntity]
         * 完全一致；如后续表结构变更，请同步新增 MIGRATION_2_3 等
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
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
                .addMigrations(MIGRATION_1_2)
                // 移除 destructive 迁移：若以后忘记加 Migration，会直接抛 IllegalStateException
                // 提醒开发者补齐迁移脚本，而不是偷偷清库
                .build()
        }
    }
}

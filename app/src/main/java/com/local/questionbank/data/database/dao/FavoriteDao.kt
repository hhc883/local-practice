package com.local.questionbank.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.local.questionbank.data.database.entity.FavoriteEntity
import com.local.questionbank.data.database.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 收藏表 Dao
 *
 * 注意：
 *  - 收藏判重依赖 questionId 的 unique 索引；写入时用 IGNORE 策略
 *  - 删除用 questionId 而非 id，避免 UI 端需要先查 id
 */
@Dao
interface FavoriteDao {

    /** 全部收藏 id（按时间倒序） */
    @Query("SELECT questionId FROM favorite ORDER BY createTimestamp DESC")
    fun observeFavoriteIds(): Flow<List<Long>>

    /** 全部收藏（带实体，方便显示收藏时间） */
    @Query("SELECT * FROM favorite ORDER BY createTimestamp DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    /** 收藏数量 */
    @Query("SELECT COUNT(*) FROM favorite WHERE questionId = :questionId")
    suspend fun countByQuestion(questionId: Long): Int

    /** 是否已收藏 */
    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE questionId = :questionId)")
    fun observeIsFavorited(questionId: Long): Flow<Boolean>

    /** 加锁加收藏（重复写会被 IGNORE 掉） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteEntity): Long

    /** 按 questionId 删除收藏 */
    @Query("DELETE FROM favorite WHERE questionId = :questionId")
    suspend fun deleteByQuestion(questionId: Long): Int

    /**
     * 仅取收藏的题目实体（与 Question 表 join）
     *
     * 用 QuestionEntity 而非领域模型是 DAO 内部约定；上层在 Repository 中转
     */
    @Query(
        """
        SELECT q.* FROM question q
        INNER JOIN favorite f ON f.questionId = q.id
        WHERE q.bankId = :bankId
        ORDER BY f.createTimestamp DESC
        """
    )
    fun observeFavoriteQuestions(bankId: Long): Flow<List<QuestionEntity>>

    /**
     * 取"全部题库"的收藏题目
     *
     * 当 [bankId] = 0 时使用（不接 bankId 过滤）
     */
    @Query(
        """
        SELECT q.* FROM question q
        INNER JOIN favorite f ON f.questionId = q.id
        ORDER BY f.createTimestamp DESC
        """
    )
    fun observeAllFavoriteQuestions(): Flow<List<QuestionEntity>>
}

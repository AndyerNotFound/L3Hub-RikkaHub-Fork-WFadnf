package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.ModelFingerprintEntity

/**
 * 模型指纹检测历史。写入是一次性的，查询按时间倒序取最近若干条。
 *
 * [recentFlow] 按 created_at 倒序，同一毫秒内写入的多条再按 id 倒序兜底，保证顺序稳定。
 */
@Dao
interface ModelFingerprintDAO {
    @Insert
    suspend fun insert(entity: ModelFingerprintEntity)

    @Transaction
    suspend fun insertRecent(entity: ModelFingerprintEntity, limit: Int) {
        insert(entity)
        trimTo(limit)
    }

    /** 最近 [limit] 条记录的观察流，供历史列表订阅 */
    @Query(
        "SELECT * FROM model_fingerprint_history ORDER BY created_at DESC, id DESC LIMIT :limit"
    )
    fun recentFlow(limit: Int): Flow<List<ModelFingerprintEntity>>

    @Query("DELETE FROM model_fingerprint_history")
    suspend fun deleteAll()

    /** 只留最新的 [keep] 条，避免检测历史无限增长 */
    @Query(
        "DELETE FROM model_fingerprint_history WHERE id NOT IN (" +
            "SELECT id FROM model_fingerprint_history ORDER BY created_at DESC, id DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)
}

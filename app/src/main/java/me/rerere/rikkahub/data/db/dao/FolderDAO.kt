package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.FolderEntity

@Dao
interface FolderDAO {
    @Query("SELECT * FROM conversation_folder WHERE assistant_id = :assistantId ORDER BY sort_index ASC, create_at ASC")
    fun getFoldersOfAssistant(assistantId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM conversation_folder WHERE id = :id")
    suspend fun getFolderById(id: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("UPDATE conversation_folder SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("SELECT * FROM conversation_folder WHERE id = :id")
    fun observeFolder(id: String): Flow<FolderEntity?>

    @Query("SELECT * FROM conversation_folder")
    suspend fun getAll(): List<FolderEntity>

    /** 工作区删除后清掉文件夹上的引用，[workspaceId] 为原来的主工作区时一起换掉 */
    @Query(
        "UPDATE conversation_folder SET workspace_id = :primary, workspace_ids = :workspaceIds WHERE id = :id"
    )
    suspend fun updateWorkspaces(id: String, primary: String, workspaceIds: String)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("DELETE FROM conversation_folder WHERE id = :id")
    suspend fun deleteById(id: String)
}

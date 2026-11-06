package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 会话文件夹（助手内分组）。
 *
 * 一个文件夹隶属于某个助手（assistant_id），会话通过 [ConversationEntity.folderId] 关联到文件夹。
 * 文件夹可以绑定一组工作区，落在里面的会话都用这组工作区（见 FolderRepository.workspaceBindingOf）。
 * 这里不使用 Room 外键，删除文件夹时由 Repository 层手动把归属会话的 folder_id 清空，
 * 避免级联删除误删会话，也避免外键迁移的复杂度。
 */
@Entity(
    tableName = "conversation_folder",
    indices = [Index(value = ["assistant_id"])]
)
data class FolderEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("sort_index", defaultValue = "0")
    val sortIndex: Int = 0,
    @ColumnInfo("create_at")
    val createAt: Long,
    // 文件夹绑定的工作区 id；非空表示这个分组里的会话都用这组工作区
    @ColumnInfo("workspace_id", defaultValue = "")
    val workspaceId: String = "",
    // 其余绑定的工作区 id，逗号分隔；空串表示不绑定
    @ColumnInfo("workspace_ids", defaultValue = "")
    val workspaceIds: String = "",
)

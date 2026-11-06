package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.FolderDAO
import me.rerere.rikkahub.data.db.entity.FolderEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.data.model.WorkspaceBinding
import java.time.Instant
import kotlin.uuid.Uuid

/** 会话的分组归属，解析工作区绑定时只读这两列 */
data class ConversationFolderRef(
    val folderId: String = "",
    val parentConversationId: String = "",
)

class FolderRepository(
    private val folderDAO: FolderDAO,
    private val conversationDAO: ConversationDAO,
) {
    fun getFoldersOfAssistant(assistantId: Uuid): Flow<List<Folder>> {
        return folderDAO.getFoldersOfAssistant(assistantId.toString())
            .map { list -> list.map { it.toFolder() } }
    }

    suspend fun getFolderById(id: Uuid): Folder? {
        return folderDAO.getFolderById(id.toString())?.toFolder()
    }

    suspend fun createFolder(
        assistantId: Uuid,
        name: String,
        workspaceId: Uuid? = null,
        workspaceIds: Set<Uuid> = emptySet(),
    ): Folder {
        val folder = Folder(
            assistantId = assistantId,
            name = name,
            createAt = Instant.now(),
            workspaceId = workspaceId?.takeIf { it in workspaceIds } ?: workspaceIds.firstOrNull(),
            workspaceIds = workspaceIds,
        )
        folderDAO.insert(folder.toEntity())
        return folder
    }

    suspend fun renameFolder(id: Uuid, name: String) {
        folderDAO.rename(id.toString(), name)
    }

    /**
     * 改文件夹绑定的工作区。
     *
     * 组里的会话不各写一份，读取时按文件夹现取（[workspaceBindingOf]）：
     * 换绑要及时生效，离开文件夹也要能回到会话自己的绑定。
     */
    suspend fun updateFolderWorkspaces(id: Uuid, primary: Uuid?, workspaceIds: Set<Uuid>) {
        val normalized = primary?.takeIf { it in workspaceIds } ?: workspaceIds.firstOrNull()
        folderDAO.updateWorkspaces(
            id = id.toString(),
            primary = normalized?.toString().orEmpty(),
            workspaceIds = encodeIds(workspaceIds),
        )
    }

    /**
     * 删除文件夹，先把归属该文件夹的会话 folder_id 清空，再删除文件夹本身（不影响会话）。
     */
    suspend fun deleteFolder(id: Uuid) {
        conversationDAO.clearFolder(id.toString())
        folderDAO.deleteById(id.toString())
    }

    /** 文件夹绑定的工作区；没绑或文件夹已删除时是空绑定，会话按自己的绑定走。 */
    suspend fun workspaceBindingOf(folderId: Uuid?): WorkspaceBinding {
        if (folderId == null) return WorkspaceBinding.None
        return getFolderById(folderId)?.workspaceBinding ?: WorkspaceBinding.None
    }

    /** 会话实际使用的工作区，口径见下面那个重载 */
    suspend fun workspaceBindingOf(conversation: Conversation): WorkspaceBinding =
        workspaceBindingOf(conversation.folderId, conversation.parentConversationId)

    /**
     * 会话实际使用的工作区。
     *
     * 会话有自己的文件夹就用文件夹的绑定（文件夹没绑工作区就是不接管）；
     * 子代理会话自己没有分组，跟着父会话走，否则派出去的活会落在另一个工作区里。
     */
    suspend fun workspaceBindingOf(folderId: Uuid?, parentConversationId: Uuid?): WorkspaceBinding {
        if (folderId != null) return workspaceBindingOf(folderId)
        var currentId: String? = parentConversationId?.toString() ?: return WorkspaceBinding.None
        var hops = 0
        while (currentId != null && hops <= MAX_PARENT_HOPS) {
            val ref = conversationDAO.getFolderRef(currentId) ?: break
            val parentFolderId = ref.folderId.toUuid()
            if (parentFolderId != null) return workspaceBindingOf(parentFolderId)
            currentId = ref.parentConversationId.takeIf { it.isNotBlank() }
            hops++
        }
        return WorkspaceBinding.None
    }

    /** 界面用：跟着会话的文件夹一路更新 */
    fun observeWorkspaceBinding(folderId: Uuid?): Flow<WorkspaceBinding> {
        if (folderId == null) return flowOf(WorkspaceBinding.None)
        return folderDAO.observeFolder(folderId.toString())
            .map { entity -> entity?.toFolder()?.workspaceBinding ?: WorkspaceBinding.None }
    }

    /** 工作区被删除后，把文件夹上对它的引用摘掉；会话自己的绑定由 WorkspaceRepository 清理 */
    suspend fun detachWorkspace(workspaceId: Uuid) = withContext(Dispatchers.IO) {
        val key = workspaceId.toString()
        folderDAO.getAll().forEach { entity ->
            val bound = decodeIds(entity.workspaceIds)
            if (entity.workspaceId != key && workspaceId !in bound) return@forEach
            val remaining = bound - workspaceId
            val primary = entity.workspaceId
                .takeIf { it.isNotBlank() && it != key }
                ?: remaining.firstOrNull()?.toString().orEmpty()
            folderDAO.updateWorkspaces(
                id = entity.id,
                primary = primary,
                workspaceIds = encodeIds(remaining),
            )
        }
    }

    private companion object {
        /** 子代理最多三层，往回找父会话时留一点余量 */
        const val MAX_PARENT_HOPS = 3
    }
}

private fun String.toUuid(): Uuid? =
    takeIf { it.isNotBlank() }?.let { runCatching { Uuid.parse(it) }.getOrNull() }

private fun encodeIds(ids: Set<Uuid>): String = ids.joinToString(",") { it.toString() }

private fun decodeIds(raw: String): Set<Uuid> =
    raw.split(',')
        .mapNotNull { token -> token.trim().toUuid() }
        .toSet()

private fun FolderEntity.toFolder(): Folder = Folder(
    id = Uuid.parse(id),
    assistantId = Uuid.parse(assistantId),
    name = name,
    sortIndex = sortIndex,
    createAt = Instant.ofEpochMilli(createAt),
    workspaceId = workspaceId.toUuid(),
    workspaceIds = decodeIds(workspaceIds),
)

private fun Folder.toEntity(): FolderEntity = FolderEntity(
    id = id.toString(),
    assistantId = assistantId.toString(),
    name = name,
    sortIndex = sortIndex,
    createAt = createAt.toEpochMilli(),
    workspaceId = workspaceId?.toString().orEmpty(),
    workspaceIds = encodeIds(workspaceIds),
)

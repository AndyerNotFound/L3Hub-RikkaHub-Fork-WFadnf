package me.rerere.rikkahub.data.model

import java.time.Instant
import kotlin.uuid.Uuid

/**
 * 会话文件夹（助手内分组）。
 *
 * 文件夹除了分堆，还可以绑定一组工作区：落在这个文件夹里的会话都用这组工作区，
 * 于是「按对话分组的文件夹」就等于「按工作区把会话分了组」。
 */
data class Folder(
    val id: Uuid = Uuid.random(),
    val assistantId: Uuid,
    val name: String,
    val sortIndex: Int = 0,
    val createAt: Instant = Instant.now(),
    // 主工作区，和助手同一套口径：可空，兼容只写了 workspaceIds 的情况
    val workspaceId: Uuid? = null,
    // 文件夹绑定的工作区；非空时覆盖会话自己的绑定
    val workspaceIds: Set<Uuid> = emptySet(),
) {
    /** 主工作区在前，并上其余绑定的工作区 */
    val allWorkspaceIds: Set<Uuid>
        get() = buildSet {
            workspaceId?.let { add(it) }
            addAll(workspaceIds)
        }

    val primaryWorkspaceId: Uuid?
        get() = workspaceId ?: workspaceIds.firstOrNull()

    /** 交给会话的工作区组；没绑工作区时是空绑定（不接管） */
    val workspaceBinding: WorkspaceBinding
        get() = WorkspaceBinding(primaryId = primaryWorkspaceId, ids = allWorkspaceIds)
}

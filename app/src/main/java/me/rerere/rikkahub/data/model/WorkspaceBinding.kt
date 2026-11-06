package me.rerere.rikkahub.data.model

import kotlin.uuid.Uuid

/**
 * 一组工作区：一个主工作区加其余绑定的工作区。
 *
 * 会话文件夹用它把「这几个聊天共用哪个工作区」固定在分组上，
 * 空绑定表示不接管，会话按自己的绑定走。
 */
data class WorkspaceBinding(
    val primaryId: Uuid? = null,
    val ids: Set<Uuid> = emptySet(),
) {
    val isEmpty: Boolean get() = ids.isEmpty()

    val isNotEmpty: Boolean get() = ids.isNotEmpty()

    companion object {
        val None = WorkspaceBinding()
    }
}

/** 把 [binding] 盖到助手上；空绑定原样返回，会话仍用自己的工作区。 */
fun Assistant.withWorkspaceBinding(binding: WorkspaceBinding): Assistant {
    if (binding.isEmpty) return this
    // 主工作区必须在集合里，否则 allWorkspaceIds 会多出一个不在 ids 里的 id
    val primary = binding.primaryId?.takeIf { it in binding.ids } ?: binding.ids.first()
    return copy(
        workspaceId = primary,
        workspaceIds = binding.ids,
    )
}

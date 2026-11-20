package me.rerere.rikkahub.data.model

import me.rerere.workspace.WorkspacePaths

/**
 * 会话工作目录的存法与展示。
 *
 * 库里存相对工作区文件区的路径（空 = 工作区根），展示和提示词里再拼回绝对路径。
 * 早期版本存的是绝对路径，这里一并兼容：能剥掉工作区前缀就剥；剥不掉的（换了远端目录后
 * 留下的旧绝对路径）按未设置处理，避免拼出 newDir/old/abs 这种路径。
 *
 * 绝对与相对的换算统一交给 [WorkspacePaths]，目录边界由它判断，`/workspace-other` 不会被当成
 * `/workspace` 的子目录。
 */
object WorkspaceCwd {
    fun normalize(workspaceDir: String, stored: String?): String? {
        val raw = stored?.replace('\\', '/')?.trim().orEmpty()
        if (raw.isEmpty() || raw == ".") return null
        if (raw.startsWith("/")) {
            return WorkspacePaths.relativeTo(workspaceDir, raw)?.ifEmpty { null }
        }
        return raw.trim('/').ifEmpty { null }
    }

    fun absolute(workspaceDir: String, relative: String?): String =
        WorkspacePaths.join(workspaceDir, relative.orEmpty())
            ?: workspaceDir.trimEnd('/').ifBlank { "/" }
}

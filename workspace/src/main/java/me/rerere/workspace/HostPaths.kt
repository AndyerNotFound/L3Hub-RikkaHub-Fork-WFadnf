package me.rerere.workspace

import java.io.File

/**
 * 宿主机文件路径的唯一实现。
 *
 * 应用私有目录、技能目录和工作区文件区都可能被软链绕开, 归属判断必须落在 canonical path 上;
 * 比较时还要按目录边界切一刀, 否则 `/files-other` 会被当成 `/files` 的子目录。
 *
 * 只处理真实文件系统。Rootfs 内那条另一套寻址方式 (环境内绝对路径) 走 [WorkspacePaths],
 * 两者不要混用。
 */
object HostPaths {
    /** 解析软链后的文件; 解析失败时返回 null */
    fun canonical(file: File): File? = runCatching { file.canonicalFile }.getOrNull()

    /** [file] 是否等于 [root] 或落在 [root] 之下; 任一侧解析失败按不在其中处理 */
    fun contains(root: File, file: File): Boolean = relativeTo(root, file) != null

    /**
     * [file] 相对 [root] 的路径, 统一用 `/` 分隔; [root] 自身返回空串, 不在其中时返回 null。
     */
    fun relativeTo(root: File, file: File): String? {
        val base = canonical(root) ?: return null
        val target = canonical(file) ?: return null
        return relativeToCanonical(base, target)
    }

    /** 两侧都已是 canonical path 时省掉两次 realpath */
    fun relativeToCanonical(root: File, file: File): String? {
        val basePath = root.path.trimEnd(File.separatorChar)
        val filePath = file.path
        if (basePath.isEmpty()) return filePath.trimStart(File.separatorChar).replace(File.separatorChar, '/')
        if (filePath != basePath && !filePath.startsWith("$basePath${File.separator}")) return null
        return filePath.removePrefix(basePath).trimStart(File.separatorChar)
            .replace(File.separatorChar, '/')
    }

    /**
     * 在 [root] 之下解析相对路径: 含 NUL、解析不出或落到 root 之外 (含软链) 时返回 null。
     * 前导 `/` 按相对路径处理; 空串返回 [root] 自身。
     */
    fun resolve(root: File, relativePath: String): File? {
        if (relativePath.contains('\u0000')) return null
        val base = canonical(root) ?: return null
        val normalized = relativePath.trim().trimStart('/')
        if (normalized.isEmpty()) return base
        val target = canonical(File(base, normalized)) ?: return null
        return target.takeIf { relativeToCanonical(base, it) != null }
    }
}

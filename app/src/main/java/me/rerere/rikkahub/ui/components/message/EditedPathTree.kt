package me.rerere.rikkahub.ui.components.message

/** 「本轮改动」树里的一层目录：[files] 是直接写在该目录下的文件，[dirs] 是更深的目录 */
internal data class EditedPathNode(
    val path: String,
    val files: List<String> = emptyList(),
    val dirs: List<EditedPathNode> = emptyList(),
)

/** 顶层：极少数路径直接落在根目录（比如 `/notes.md`），单独列在最前面 */
internal data class EditedPathTree(
    val files: List<String> = emptyList(),
    val dirs: List<EditedPathNode> = emptyList(),
)

/**
 * 把本轮改过的路径整理成按目录分层的树。
 *
 * 只有一层子目录、自己又没有文件的目录会向上合并：`/a/b/c/d.kt` 只占一行 `/a/b/c` 加一行文件，
 * 不会把路径的每一段都铺成一行。合并后的每一行仍然保留完整路径，和工具结果里的路径一一对应。
 */
internal fun buildEditedPathTree(paths: List<String>): EditedPathTree {
    if (paths.isEmpty()) return EditedPathTree()

    class Node(val path: String) {
        val dirs = linkedMapOf<String, Node>()
        val files = mutableListOf<String>()
    }

    val root = Node("")
    paths.distinct().forEach { path ->
        val parent = path.substringBeforeLast('/', "")
        val absolute = parent.startsWith('/')
        var node = root
        parent.trim('/').split('/').filter { it.isNotEmpty() }.forEach { segment ->
            val childPath = when {
                node.path.isNotEmpty() -> "${node.path}/$segment"
                absolute -> "/$segment"
                else -> segment
            }
            node = node.dirs.getOrPut(segment) { Node(childPath) }
        }
        node.files += path
    }

    // 一条道走到黑的目录合并成一行，避免 `/a`、`/a/b`、`/a/b/c` 各占一行
    fun compress(node: Node): Node {
        var current = node
        while (current.files.isEmpty() && current.dirs.size == 1) {
            current = current.dirs.values.first()
        }
        return current
    }

    fun freeze(node: Node): EditedPathNode {
        val current = compress(node)
        return EditedPathNode(
            path = current.path,
            files = current.files.sorted(),
            dirs = current.dirs.values.map { child -> freeze(child) }.sortedBy { it.path },
        )
    }

    return EditedPathTree(
        files = root.files.sorted(),
        dirs = root.dirs.values.map { child -> freeze(child) }.sortedBy { it.path },
    )
}

package me.rerere.workspace

/**
 * 环境内绝对路径的唯一实现。
 *
 * 审批判定、落盘、存储区换算与展示都从这里取同一个规范形式, 避免各写一套规则:
 * 折叠 `.` 与重复的 `/`, 从根目录向上越界的 `..` 一律判为非法, 而不是悄悄夹到 `/`。
 * 路径按字面处理, 不跟随软链; 软链逃逸由访问宿主机文件的实现另行用 canonical path 拦。
 */
object WorkspacePaths {
    /**
     * 规范化环境内绝对路径; 不是绝对路径、含 NUL 或越过根目录时返回 null。
     */
    fun normalize(path: String): String? {
        val raw = path.replace('\\', '/').trim()
        if (raw.isEmpty() || !raw.startsWith("/")) return null
        if (raw.contains('\u0000')) return null
        val segments = ArrayDeque<String>()
        for (segment in raw.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeLast()
                else -> segments.addLast(segment)
            }
        }
        return if (segments.isEmpty()) "/" else segments.joinToString(separator = "/", prefix = "/")
    }

    /**
     * 以 [directory] 为基准拼出环境内绝对路径; [path] 里的前导 `/` 按相对路径处理,
     * 与存储区「相对文件区」的既有约定一致。折叠后落到 [directory] 之外时返回 null,
     * 免得调用方把 `..` 拼出一条自己没打算访问的路径。
     */
    fun join(directory: String, path: String): String? {
        val base = normalize(directory) ?: return null
        val relative = path.replace('\\', '/').trim().trim('/')
        if (relative.isEmpty()) return base
        val combined = if (base == "/") "/$relative" else "$base/$relative"
        return normalize(combined)?.takeIf { contains(base, it) }
    }

    /**
     * 两段相对路径拼接: 只处理分隔符与 `.`, 不做归属判断, 供界面展示仓库相对路径使用。
     */
    fun joinRelative(base: String, path: String): String {
        val left = base.replace('\\', '/').trim().trim('/')
        val right = path.replace('\\', '/').trim().trim('/')
        val segments = (left.split('/') + right.split('/'))
            .filter { it.isNotEmpty() && it != "." }
        return segments.joinToString("/")
    }

    /** [path] 是否等于 [root] 或落在 [root] 之下; 按目录边界判断, `/a` 不包含 `/ab` */
    fun contains(root: String, path: String): Boolean = relativeTo(root, path) != null

    /**
     * [path] 相对 [root] 的路径: 同一个目录返回空串, 不在其中或两者不合法时返回 null。
     */
    fun relativeTo(root: String, path: String): String? {
        val base = normalize(root) ?: return null
        val target = normalize(path) ?: return null
        return when {
            target == base -> ""
            base == "/" -> target.trimStart('/')
            target.startsWith("$base/") -> target.removePrefix("$base/")
            else -> null
        }
    }
}

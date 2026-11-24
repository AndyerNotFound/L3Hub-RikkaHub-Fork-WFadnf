package me.rerere.rikkahub.data.sync

import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.workspace.HostPaths
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Kelivo 导出时附件的来源判定与归档命名。
 *
 * 两条边界：
 * - `file://` 只接受 app 私有 filesDir 下白名单目录里的真实文件（见 [SOURCE_FOLDERS]，与
 *   [BackupManager.ATTACHMENT_FOLDERS] 同源，另加历史版本的 `avatars/`）。备份包里的 `file://`
 *   指向什么都会被恢复端写进消息，导出时再照着读一遍，就等于把设置、数据库或共享存储里的任意
 *   可读文件带进备份包。
 * - 归档里的文件名一律重新生成。展示名来自消息里的 `part.fileName` 或 ContentProvider 的
 *   DISPLAY_NAME，可以直接是 `../../x`、带分隔符或以 `.` 开头的串：拿它 resolve 会写到缓存目录
 *   之外，写进 zip 还会造出越界条目。展示名只留在 part 的 `name` 字段上。
 *
 * 判定全部落在 canonical path 上（[HostPaths]），软链指到目录之外时按「不在白名单里」处理。
 */
internal object KelivoAttachmentPaths {
    /** content:// 附件落盘的缓存子目录 */
    const val CACHE_DIR_NAME = "kelivo-backup-upload"

    /** 附件来源白名单里额外的目录：历史版本把头像单独存过，正常流程走 upload/ */
    private const val AVATARS = "avatars"

    private const val FALLBACK_NAME = "file"
    private const val MAX_SEGMENT_LENGTH = 96
    private const val MAX_DISPLAY_NAME_LENGTH = 160
    private const val MAX_EXTENSION_LENGTH = 8

    /** 缓存名里的 hash 前缀长度：8 字节足够避开碰撞，文件名也还看得清 */
    private const val TOKEN_BYTES = 8

    /** zip 条目名上限，防止极端展示名把条目名撑到不可用的长度 */
    private const val MAX_ENTRY_LENGTH = 256

    /** 同名附件让位时的最大尝试次数 */
    const val MAX_NAME_RETRIES = 64

    /** 允许作为附件来源的 filesDir 顶层目录 */
    val SOURCE_FOLDERS: Set<String> = setOf(
        FileFolders.UPLOAD,
        FileFolders.IMAGES,
        FileFolders.SKILLS,
        FileFolders.FONTS,
        FileFolders.MEDIA_CREATION,
        AVATARS,
    )

    /** zip 里保留的顶层目录；其余来源目录一律并入 upload/ */
    val ARCHIVE_ROOTS: Set<String> = setOf(
        FileFolders.UPLOAD,
        FileFolders.IMAGES,
        FileFolders.FONTS,
        AVATARS,
    )

    /** [file] 相对 [filesDir] 的路径；[filesDir] 自身或解析不到（软链逃逸）时返回 null */
    fun relativePath(filesDir: File, file: File): String? =
        HostPaths.relativeTo(filesDir, file)?.takeIf { it.isNotEmpty() }

    /**
     * `file://` 的绝对路径 → 可导出的源文件。
     *
     * 相对路径、含 NUL、解析后落到 filesDir 之外、不在 [SOURCE_FOLDERS] 里、不是常规文件，
     * 一律返回 null，调用方按既有的「附件缺失」跳过该 part。
     */
    fun sourceFile(filesDir: File, path: String?): File? {
        if (path.isNullOrBlank() || path.contains('\u0000')) return null
        val candidate = File(path)
        if (!candidate.isAbsolute) return null
        // canonical 之后再判归属：`upload/../datastore/x` 与指向目录外的软链都会在这里落到 null
        val canonical = HostPaths.canonical(candidate) ?: return null
        if (!canonical.isFile) return null
        val relative = relativePath(filesDir, canonical) ?: return null
        return canonical.takeIf { relative.substringBefore('/') in SOURCE_FOLDERS }
    }

    /** 单段安全文件名：去掉目录部分、控制字符与首尾的点，空白时用 [fallback] */
    fun sanitizeSegment(
        name: String?,
        fallback: String = FALLBACK_NAME,
        maxLength: Int = MAX_SEGMENT_LENGTH,
    ): String {
        val base = name.orEmpty().replace('\\', '/').substringAfterLast('/')
        val builder = StringBuilder(base.length)
        var index = 0
        while (index < base.length) {
            val ch = base[index]
            if (ch.isHighSurrogate() && index + 1 < base.length && base[index + 1].isLowSurrogate()) {
                // 成对的代理项（emoji 一类）原样保留，切一半会留下非法字符
                builder.append(ch).append(base[index + 1])
                index += 2
            } else {
                if (!ch.isSurrogate() && ch.code >= 0x20 && ch.code != 0x7f) builder.append(ch)
                index += 1
            }
        }
        val trimmed = builder.toString().trim().trim('.').take(maxLength).trimEnd('.', ' ')
        val cleaned = if (trimmed.lastOrNull()?.isHighSurrogate() == true) trimmed.dropLast(1) else trimmed
        return cleaned.ifBlank { fallback }
    }

    /** 给人看的展示名：同样去掉路径与控制字符，但不做扩展名归一 */
    fun displayName(name: String?, fallback: String = FALLBACK_NAME): String =
        sanitizeSegment(name, fallback, maxLength = MAX_DISPLAY_NAME_LENGTH)

    /** 归档里的顶层目录；来源目录不在 [ARCHIVE_ROOTS] 里时并入 upload/ */
    fun archiveFolder(relativePath: String?): String {
        val folder = relativePath?.substringBefore('/')?.takeIf { it in ARCHIVE_ROOTS }
        return folder ?: FileFolders.UPLOAD
    }

    /**
     * 归档条目名：`folder/名字[-序号].扩展名`。
     *
     * [ordinal] 大于 0 时插在扩展名之前，用来区分同目录下同名的不同来源（例如两个子目录里的
     * `a.md` 都并入 upload/）。
     */
    fun entryName(folder: String, fileName: String, ordinal: Int = 0): String {
        val safeName = sanitizeSegment(fileName)
        val name = if (ordinal <= 0) safeName else numbered(safeName, ordinal)
        return "${sanitizeSegment(folder)}/$name"
    }

    fun kelivoUri(entryName: String): String = "kelivo-file:///$entryName"

    private fun numbered(fileName: String, ordinal: Int): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot <= 0) "$fileName-$ordinal"
        else "${fileName.substring(0, dot)}-$ordinal${fileName.substring(dot)}"
    }

    /** 缓存落盘用的文件名：随机前缀 + 安全扩展名，永不含分隔符 */
    fun cacheFileName(
        displayName: String?,
        mimeType: String?,
        token: String = UUID.randomUUID().toString(),
    ): String {
        val safeToken = token
            .filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }
            .ifBlank { "kelivo" }
        return "$safeToken.${extensionOf(displayName, mimeType)}"
    }

    /**
     * 由来源 uri 派生的固定 token。
     *
     * 同一个 content uri 在一条会话里被引用多次时落到同一个缓存文件，导出侧按条目名去重，
     * 与以前按 DISPLAY_NAME 去重的结果一致；不同 uri 之间仍不会互相覆盖。
     */
    fun cacheToken(seed: String): String = MessageDigest.getInstance("SHA-256")
        .digest(seed.toByteArray(Charsets.UTF_8))
        .take(TOKEN_BYTES)
        .joinToString("") { byte -> "%02x".format(byte) }

    /**
     * 给一条附件挑一个不冲突的条目名。
     *
     * [taken] 按条目名查已占用的源文件：同一个文件再次被引用时复用原条目（一条会话里同图多次出现
     * 不会在包里堆成好几份）；不同文件撞名时按序号让开，避免后一条覆盖前一条。
     * 名字不合规或让不开时返回 null，调用方按附件缺失跳过。
     */
    fun entryNameFor(
        folder: String,
        fileName: String,
        source: File,
        maxRetries: Int = MAX_NAME_RETRIES,
        taken: (String) -> File?,
    ): String? {
        val baseName = entryName(folder, fileName)
        if (!isSafeEntryName(baseName)) return null
        val sourcePath = canonicalOrAbsolute(source)
        for (ordinal in 0..maxRetries) {
            val candidate = entryName(folder, fileName, ordinal)
            if (!isSafeEntryName(candidate)) return null
            val existing = taken(candidate) ?: return candidate
            if (canonicalOrAbsolute(existing) == sourcePath) return candidate
        }
        return null
    }

    /** 供调用方比较「是不是同一个文件」用；解析失败时退回绝对路径 */
    fun canonicalOrAbsolute(file: File): File = HostPaths.canonical(file) ?: file.absoluteFile

    /** 归档条目名是否可用：绝对路径、反斜杠、空段与 `.` / `..` 段都不接受 */
    fun isSafeEntryName(entryName: String): Boolean {
        if (entryName.isBlank() || entryName.startsWith('/') || '\\' in entryName) return false
        if (entryName.length > MAX_ENTRY_LENGTH) return false
        return entryName.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    /** content:// 附件的落盘目标；名字是生成的，这里再按 canonical path 兜一次 */
    fun cacheTarget(
        cacheDir: File,
        displayName: String?,
        mimeType: String?,
        token: String = UUID.randomUUID().toString(),
    ): File? {
        val directory = cacheDirectory(cacheDir)
        // 缓存子目录自身也可能是软链，不能把指向 cacheDir 外部的目录当作可信根。
        if (!HostPaths.contains(cacheDir, directory)) return null
        if (!directory.isDirectory && !directory.mkdirs()) return null
        val target = File(directory, cacheFileName(displayName, mimeType, token))
        return target.takeIf { HostPaths.contains(directory, it) }
    }

    fun cacheDirectory(cacheDir: File): File = File(cacheDir, CACHE_DIR_NAME)

    private fun extensionOf(displayName: String?, mimeType: String?): String {
        val fromName = displayName
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.isPlainExtension() }
        if (fromName != null) return fromName
        val fromMime = mimeType
            ?.substringAfter('/', "")
            ?.substringBefore('+')
            ?.lowercase()
            ?.takeIf { it.isPlainExtension() }
        return fromMime ?: "bin"
    }

    private fun String.isPlainExtension(): Boolean =
        length in 1..MAX_EXTENSION_LENGTH && all { it in 'a'..'z' || it in '0'..'9' }
}

package me.rerere.rikkahub.data.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.MediaCreationFiles
import me.rerere.rikkahub.data.sync.bridge.SyncBridge
import java.io.File

/**
 * 增量同步的第一包：只描述对端有什么，不搬内容。
 *
 * 会话只给 id 与 update_at，节点不进清单（一条长会话的节点体积是清单的百倍）；
 * 文件只给相对路径与字节数，作为「本地这份是不是同一个版本」的弱指纹。
 * 清单本身可能上千条，仍然经 provider 的中转文件给出，不塞进 Binder 事务。
 */
@Serializable
data class SyncManifest(
    val protocol: Int = SyncBridge.PROTOCOL_VERSION,
    val schemaVersion: Int = 0,
    val capturedAt: Long = 0L,
    val conversations: List<ConversationEntry> = emptyList(),
    val tombstones: List<TombstoneEntry> = emptyList(),
    val files: List<FileEntry> = emptyList(),
) {
    @Serializable
    data class ConversationEntry(val id: String, val updatedAt: Long)

    @Serializable
    data class TombstoneEntry(val kind: String, val targetId: String, val deletedAt: Long)

    @Serializable
    data class FileEntry(val path: String, val size: Long)
}

/** 文件差异的结果：[full] 为 true 表示逐条列太不划算，请对端把白名单目录整份打过来。 */
data class SyncFilePlan(val paths: List<String>, val full: Boolean)

private const val MAX_REQUESTED_FILES = 400

/**
 * 算出本地缺哪些文件。
 *
 * 清单里的路径是相对 filesDir 的（`upload/x.png`），遍历时只认可白名单目录下的安全相对路径；
 * 本地已有同名且字节数一致的文件视为同一版本，不再传——没有内容哈希，大小是能拿到的最强信号，
 * 内容变了但大小恰好相同的文件会漏一次，这类文件在附件里极少。
 */
fun planFilesToFetch(
    manifest: SyncManifest,
    filesDir: File,
    maxPaths: Int = MAX_REQUESTED_FILES,
): SyncFilePlan {
    val missing = mutableListOf<String>()
    for (entry in manifest.files) {
        if (!isSyncablePath(entry.path)) continue
        val local = File(filesDir, entry.path)
        if (local.isFile && local.length() == entry.size) continue
        missing += entry.path
        if (missing.size > maxPaths) return SyncFilePlan(emptyList(), full = true)
    }
    return SyncFilePlan(missing, full = false)
}

/**
 * 推送方向的反向差异：本地有、对端没有或大小不同的文件。
 * 与 [planFilesToFetch] 同一套判据，只是把「本地」和「清单」的位置对调。
 */
fun planFilesToSend(
    localFiles: List<SyncManifest.FileEntry>,
    manifest: SyncManifest,
    maxPaths: Int = MAX_REQUESTED_FILES,
): SyncFilePlan {
    val remote = manifest.files.associateBy { it.path }
    val missing = mutableListOf<String>()
    for (entry in localFiles) {
        if (!isSyncablePath(entry.path)) continue
        val other = remote[entry.path]
        if (other != null && other.size == entry.size) continue
        missing += entry.path
        if (missing.size > maxPaths) return SyncFilePlan(emptyList(), full = true)
    }
    return SyncFilePlan(missing, full = false)
}

/** 只允许白名单目录下的普通相对路径，挡住 `..`、绝对路径与反斜杠 */
fun isSyncablePath(path: String): Boolean {
    if (path.isBlank() || path.startsWith('/') || '\\' in path) return false
    val parts = path.split('/')
    if (parts.any { it.isEmpty() || it == "." || it == ".." }) return false
    return parts.first() in BackupManager.ATTACHMENT_FOLDERS
}

/** 按白名单目录列出本地文件，作为清单的文件部分；规则与备份打包一致 */
internal fun collectSyncableFiles(filesDir: File): List<SyncManifest.FileEntry> {
    val entries = mutableListOf<SyncManifest.FileEntry>()
    for (folder in BackupManager.ATTACHMENT_FOLDERS) {
        val directory = File(filesDir, folder)
        if (!directory.isDirectory) continue
        val files = if (folder in BackupManager.NESTED_ATTACHMENT_FOLDERS) {
            directory.walkTopDown()
        } else {
            directory.listFiles().orEmpty().asSequence()
        }
        for (file in files.filter { it.isFile }) {
            if (folder == FileFolders.MEDIA_CREATION &&
                file.name.endsWith(MediaCreationFiles.PARTIAL_SUFFIX)
            ) {
                continue
            }
            val relative = file.relativeTo(directory).invariantSeparatorsPath
            val path = "$folder/$relative"
            if (!isSyncablePath(path)) continue
            entries += SyncManifest.FileEntry(path = path, size = file.length())
        }
    }
    return entries
}

/** 读库生成清单；[includeFiles] 为 false 时不扫目录，用于推送方向只要会话的场合 */
internal fun buildSyncManifest(
    database: SupportSQLiteDatabase,
    filesDir: File,
    schemaVersion: Int,
    includeFiles: Boolean,
): SyncManifest {
    val conversations = mutableListOf<SyncManifest.ConversationEntry>()
    database.query("SELECT id, update_at FROM `ConversationEntity`").use { cursor ->
        while (cursor.moveToNext()) {
            conversations += SyncManifest.ConversationEntry(
                id = cursor.getString(0),
                updatedAt = cursor.getLong(1),
            )
        }
    }
    val tombstones = mutableListOf<SyncManifest.TombstoneEntry>()
    if (SyncTombstones.tableExists(database)) {
        database.query("SELECT kind, target_id, deleted_at FROM `${SyncTombstones.TABLE}`").use { cursor ->
            while (cursor.moveToNext()) {
                tombstones += SyncManifest.TombstoneEntry(
                    kind = cursor.getString(0),
                    targetId = cursor.getString(1),
                    deletedAt = cursor.getLong(2),
                )
            }
        }
    }
    return SyncManifest(
        protocol = SyncBridge.PROTOCOL_VERSION,
        schemaVersion = schemaVersion,
        capturedAt = System.currentTimeMillis(),
        conversations = conversations,
        tombstones = tombstones,
        files = if (includeFiles) collectSyncableFiles(filesDir) else emptyList(),
    )
}

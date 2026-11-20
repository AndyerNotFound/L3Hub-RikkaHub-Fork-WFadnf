package me.rerere.rikkahub.data.sync.webdav

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.sync.BackupManager
import me.rerere.rikkahub.data.sync.BundleRequest
import me.rerere.rikkahub.data.sync.RemoteBundleBackup
import me.rerere.rikkahub.data.sync.RestoreOptions
import me.rerere.rikkahub.data.sync.RestoreStageResult
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.utils.fileSizeToString
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "WebDavSync"
private const val CONTENT_TYPE_BUNDLE = "application/octet-stream"

class WebDavSync(
    private val backupManager: BackupManager,
    private val context: Context,
    private val httpClient: HttpClient,
) {
    private fun getClient(config: WebDavConfig): WebDavClient {
        return WebDavClient(config, httpClient)
    }

    suspend fun testConnection(config: WebDavConfig) = withContext(Dispatchers.IO) {
        val client = getClient(config)
        // Test by listing the root directory
        client.propfind(depth = 0).getOrThrow()
        Log.i(TAG, "testConnection: Connection successful")
    }

    /**
     * 上传一份备份。
     *
     * [incremental] 为 true 时是按链追加的 `.l3b`：第一次传整份，之后只传这一段时间变化的那一层；
     * 为 false 时是旧的全量 zip，每次重新打包整库。
     */
    suspend fun backup(config: WebDavConfig, incremental: Boolean = true) = withContext(Dispatchers.IO) {
        val client = getClient(config)
        client.ensureCollectionExists().getOrThrow()
        val includeDatabase = WebDavConfig.BackupItem.DATABASE in config.items
        val includeFiles = WebDavConfig.BackupItem.FILES in config.items
        if (!incremental) {
            val file = backupManager.createBackup(includeDatabase, includeFiles)
            try {
                client.put(path = file.name, file = file, contentType = "application/zip").getOrThrow()
                Log.i(TAG, "backup: Uploaded ${file.name} (${file.length().fileSizeToString()})")
            } finally {
                file.delete()
            }
            return@withContext
        }
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val names = client.list().getOrThrow().filter { !it.isCollection }.map { it.displayName }.toSet()
        val writer = RemoteBundleBackup(
            context = context,
            backupManager = backupManager,
            targetKey = targetKey(config),
        )
        val plan = writer.plan(names, includeDatabase, includeFiles, timestamp)
        val layer = writer.prepare(plan, includeDatabase, includeFiles)
        try {
            client.put(path = layer.name, file = layer.file, contentType = CONTENT_TYPE_BUNDLE).getOrThrow()
            writer.commit(layer)
            Log.i(TAG, "backup: Uploaded ${layer.name} (${layer.file.length().fileSizeToString()})")
        } finally {
            writer.discard(layer)
        }
    }

    suspend fun listBackupFiles(config: WebDavConfig): List<WebDavBackupItem> = withContext(Dispatchers.IO) {
        val client = getClient(config)

        // Ensure the backup directory exists
        client.ensureCollectionExists().getOrThrow()

        val resources = client.list().getOrThrow().filter { !it.isCollection && it.displayName.startsWith("backup_") }
        val items = mutableListOf<WebDavBackupItem>()
        val consumed = mutableSetOf<String>()
        for (base in resources.filter { it.displayName.endsWith(".l3b") }) {
            val layers = resources
                .filter { it.displayName.startsWith(base.displayName + ".d") }
                .sortedBy { it.displayName }
            consumed += base.displayName
            consumed += layers.map { it.displayName }
            items += WebDavBackupItem(
                href = base.href,
                displayName = base.displayName,
                size = base.contentLength + layers.sumOf { it.contentLength },
                lastModified = (listOfNotNull(base.lastModified) + layers.mapNotNull { it.lastModified })
                    .maxOrNull() ?: Instant.EPOCH,
                layers = layers.map { it.displayName },
                bundle = true,
            )
        }
        for (zip in resources.filter { it.displayName.endsWith(".zip") && it.displayName !in consumed }) {
            items += WebDavBackupItem(
                href = zip.href,
                displayName = zip.displayName,
                size = zip.contentLength,
                lastModified = zip.lastModified ?: Instant.EPOCH,
                bundle = false,
            )
        }
        items.sortedByDescending { it.lastModified }
    }

    suspend fun restore(
        config: WebDavConfig,
        item: WebDavBackupItem,
        options: RestoreOptions,
    ): RestoreStageResult = withContext(Dispatchers.IO) {
        val client = getClient(config)
        val backupFile = File.createTempFile(
            "restore-",
            if (item.bundle) ".l3b" else ".zip",
            context.cacheDir,
        )

        try {
            // Download backup file directly to file to avoid OOM
            Log.i(TAG, "restore: Downloading ${item.displayName}")
            client.downloadToFile(item.displayName, backupFile).getOrThrow()
            // 分层备份：base 之后各层按顺序拼进来，拼完就是一份完整的包
            for (layer in item.layers) {
                val part = File.createTempFile("restore-layer-", ".l3b.d", context.cacheDir)
                try {
                    client.downloadToFile(layer, part).getOrThrow()
                    part.inputStream().use { input ->
                        FileOutputStream(backupFile, true).use { output -> input.copyTo(output) }
                    }
                } finally {
                    part.delete()
                }
            }

            Log.i(TAG, "restore: Downloaded ${backupFile.length().fileSizeToString()}")

            // Restore from backup file
            restoreFromBackupFile(backupFile, options)
        } finally {
            // Clean up temp file
            if (backupFile.exists()) {
                backupFile.delete()
                Log.i(TAG, "restore: Cleaned up temporary backup file")
            }
        }
    }

    suspend fun deleteBackupFile(config: WebDavConfig, item: WebDavBackupItem) = withContext(Dispatchers.IO) {
        val client = getClient(config)
        // 分层备份是一整套对象，删一份就要把它的各层一起删掉
        for (layer in item.layers) client.delete(layer).getOrThrow()
        client.delete(item.displayName).getOrThrow()
        Log.i(TAG, "deleteBackupFile: Deleted ${item.displayName} with ${item.layers.size} layers")
    }

    suspend fun restoreFromLocalFile(file: File, options: RestoreOptions): RestoreStageResult =
        restoreFromBackupFile(file, options)

    /**
     * 生成一份可分享的备份文件。
     *
     * 单文件导出没有「下一次」，所以它永远是一个 base 层；[incremental] 为 false 时仍按旧 zip 打包。
     */
    suspend fun prepareBackupFile(config: WebDavConfig, incremental: Boolean = true): File {
        val includeDatabase = WebDavConfig.BackupItem.DATABASE in config.items
        val includeFiles = WebDavConfig.BackupItem.FILES in config.items
        if (!incremental) {
            return backupManager.createBackup(includeDatabase = includeDatabase, includeFiles = includeFiles)
        }
        val prepared = backupManager.prepareBundleLayer(
            BundleRequest(
                includeDatabase = includeDatabase,
                includeFiles = includeFiles,
                base = true,
                includeSettings = true,
            )
        )
        val file = File.createTempFile("rikkahub_backup_", ".l3b", context.cacheDir)
        try {
            backupManager.appendBundle(file, prepared)
        } finally {
            prepared.payload.delete()
        }
        return file
    }

    private suspend fun restoreFromBackupFile(
        backupFile: File,
        options: RestoreOptions,
    ): RestoreStageResult = backupManager.stageRestore(archive = backupFile, options = options)

    private fun targetKey(config: WebDavConfig): String = "webdav|${config.url}|${config.path}"
}

data class WebDavBackupItem(
    val href: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
    /** `.l3b` 链上的增量层对象名；旧格式的 zip 为空 */
    val layers: List<String> = emptyList(),
    val bundle: Boolean = false,
)

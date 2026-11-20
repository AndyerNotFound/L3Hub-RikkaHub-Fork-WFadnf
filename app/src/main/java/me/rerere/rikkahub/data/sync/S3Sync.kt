package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.sync.s3.S3Client
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.utils.fileSizeToString
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "S3Sync"
private const val PREFIX = "rikkahub_backups/"
private const val CONTENT_TYPE_BUNDLE = "application/octet-stream"

class S3Sync(
    private val backupManager: BackupManager,
    private val context: Context,
    private val httpClient: HttpClient,
) {
    private fun getS3Client(config: S3Config): S3Client {
        return S3Client(config, httpClient)
    }

    suspend fun testS3(config: S3Config) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        // Test by listing objects with max 1 result
        client.listObjects(maxKeys = 1).getOrThrow()
        Log.i(TAG, "testS3: Connection successful")
    }

    /** 增量时按链追加 `.l3b`，否则仍是每次重打的整份 zip */
    suspend fun backupToS3(config: S3Config, incremental: Boolean = true) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        val includeDatabase = S3Config.BackupItem.DATABASE in config.items
        val includeFiles = S3Config.BackupItem.FILES in config.items
        if (!incremental) {
            val file = backupManager.createBackup(includeDatabase, includeFiles)
            try {
                client.putObject(key = PREFIX + file.name, file = file, contentType = "application/zip").getOrThrow()
                Log.i(TAG, "backupToS3: Uploaded ${file.name} (${file.length().fileSizeToString()})")
            } finally {
                file.delete()
            }
            return@withContext
        }
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val names = client.listObjects(prefix = PREFIX, maxKeys = 1000).getOrThrow()
            .objects
            .map { it.key.substringAfterLast("/") }
            .toSet()
        val writer = RemoteBundleBackup(
            context = context,
            backupManager = backupManager,
            targetKey = targetKey(config),
        )
        val plan = writer.plan(names, includeDatabase, includeFiles, timestamp)
        val layer = writer.prepare(plan, includeDatabase, includeFiles)
        try {
            client.putObject(key = PREFIX + layer.name, file = layer.file, contentType = CONTENT_TYPE_BUNDLE)
                .getOrThrow()
            writer.commit(layer)
            Log.i(TAG, "backupToS3: Uploaded ${layer.name} (${layer.file.length().fileSizeToString()})")
        } finally {
            writer.discard(layer)
        }
    }

    suspend fun listBackupFiles(config: S3Config): List<S3BackupItem> = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        val objects = client.listObjects(prefix = PREFIX, maxKeys = 1000).getOrThrow()
            .objects
            .filter { it.key.startsWith(PREFIX) && it.key.removePrefix(PREFIX).startsWith("backup_") }
        val items = mutableListOf<S3BackupItem>()
        val consumed = mutableSetOf<String>()
        for (base in objects.filter { it.key.endsWith(".l3b") }) {
            val layers = objects.filter { it.key.startsWith(base.key + ".d") }.sortedBy { it.key }
            consumed += base.key
            consumed += layers.map { it.key }
            items += S3BackupItem(
                key = base.key,
                displayName = base.key.substringAfterLast("/"),
                size = base.size + layers.sumOf { it.size },
                lastModified = (listOfNotNull(base.lastModified) + layers.mapNotNull { it.lastModified })
                    .maxOrNull() ?: Instant.EPOCH,
                layers = layers.map { it.key },
                bundle = true,
            )
        }
        for (zip in objects.filter { it.key.endsWith(".zip") && it.key !in consumed }) {
            items += S3BackupItem(
                key = zip.key,
                displayName = zip.key.substringAfterLast("/"),
                size = zip.size,
                lastModified = zip.lastModified ?: Instant.EPOCH,
                bundle = false,
            )
        }
        items.sortedByDescending { it.lastModified }
    }

    suspend fun restoreFromS3(
        config: S3Config,
        item: S3BackupItem,
        options: RestoreOptions,
    ): RestoreStageResult = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        val backupFile = File.createTempFile(
            "restore-",
            if (item.bundle) ".l3b" else ".zip",
            context.cacheDir,
        )

        try {
            Log.i(TAG, "restoreFromS3: Downloading ${item.displayName}")
            client.downloadObjectToFile(item.key, backupFile).getOrThrow()
            for (layer in item.layers) {
                val part = File.createTempFile("restore-layer-", ".l3b.d", context.cacheDir)
                try {
                    client.downloadObjectToFile(layer, part).getOrThrow()
                    part.inputStream().use { input ->
                        FileOutputStream(backupFile, true).use { output -> input.copyTo(output) }
                    }
                } finally {
                    part.delete()
                }
            }
            Log.i(TAG, "restoreFromS3: Downloaded ${backupFile.length().fileSizeToString()}")
            restoreFromBackupFile(backupFile, options)
        } finally {
            if (backupFile.exists()) {
                backupFile.delete()
                Log.i(TAG, "restoreFromS3: Cleaned up temporary backup file")
            }
        }
    }

    suspend fun deleteS3BackupFile(config: S3Config, item: S3BackupItem) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        for (layer in item.layers) client.deleteObject(layer).getOrThrow()
        client.deleteObject(item.key).getOrThrow()
        Log.i(TAG, "deleteS3BackupFile: Deleted ${item.key} with ${item.layers.size} layers")
    }

    suspend fun prepareBackupFile(config: S3Config, incremental: Boolean = true): File {
        val includeDatabase = S3Config.BackupItem.DATABASE in config.items
        val includeFiles = S3Config.BackupItem.FILES in config.items
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

    private fun targetKey(config: S3Config): String = "s3|${config.endpoint}|${config.bucket}"
}

data class S3BackupItem(
    val key: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
    /** `.l3b` 链上的增量层对象键；旧格式的 zip 为空 */
    val layers: List<String> = emptyList(),
    val bundle: Boolean = false,
)

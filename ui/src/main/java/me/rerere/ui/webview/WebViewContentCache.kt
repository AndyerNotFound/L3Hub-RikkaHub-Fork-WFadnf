package me.rerere.ui.webview

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object WebViewContentCache {
    private const val DIRECTORY_NAME = "webview_content"
    private const val MESSAGE_DIRECTORY_NAME = "offline_message_preview"
    private const val MESSAGE_MAX_BYTES = 800_004
    private const val MESSAGE_CACHE_BYTES = 16L * 1024 * 1024
    private const val MESSAGE_CACHE_ENTRIES = 128
    private const val HASH_LENGTH = 64
    private val maxAgeMillis = TimeUnit.DAYS.toMillis(7)
    private val hexDigits = "0123456789abcdef".toCharArray()
    private val messageCacheLock = Any()

    fun store(cacheDir: File, content: String): String {
        val id = content.toByteArray().sha256()
        val directory = File(cacheDir, DIRECTORY_NAME)
        check(directory.isDirectory || directory.mkdirs()) {
            "Unable to create WebView content cache directory"
        }

        val file = File(directory, id)
        if (!file.isFile) {
            file.writeText(content)
        }
        file.setLastModified(System.currentTimeMillis())

        removeExpiredFiles(directory)
        return id
    }

    fun load(cacheDir: File, id: String): String? {
        if (!id.isSha256()) return null

        val file = File(File(cacheDir, DIRECTORY_NAME), id)
        if (!file.isFile) return null

        return runCatching {
            file.readText().also {
                file.setLastModified(System.currentTimeMillis())
            }
        }.getOrNull()
    }

    /** Raw message text has its own cache, separate from executable HTML previews. Call on IO. */
    fun storeOfflineMessage(cacheDir: File, source: String): String = synchronized(messageCacheLock) {
        require(source.length <= MESSAGE_MAX_BYTES)
        val bytes = source.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MESSAGE_MAX_BYTES)
        val id = bytes.sha256()
        val directory = checkNotNull(messageDirectory(cacheDir))
        check(directory.isDirectory || directory.mkdirs()) { "Unable to create message preview cache" }
        val file = File(directory, id)
        check(file.canonicalFile == file)
        if (!file.isFile) {
            val pending = File.createTempFile("entry-", ".tmp", directory)
            try {
                pending.writeBytes(bytes)
                check(pending.renameTo(file)) { "Unable to save message preview" }
            } finally {
                pending.delete()
            }
        }
        file.setLastModified(System.currentTimeMillis())
        removeExpiredFiles(directory)
        var remainingBytes = MESSAGE_CACHE_BYTES - file.length()
        var remainingEntries = MESSAGE_CACHE_ENTRIES - 1
        directory.listFiles().orEmpty().filter { it != file && it.name.isSha256() && it.isFile }
            .sortedByDescending { it.lastModified() }.forEach { entry ->
                val size = entry.length()
                if (remainingEntries > 0 && size <= remainingBytes) {
                    remainingEntries--
                    remainingBytes -= size
                } else {
                    entry.delete()
                }
            }
        id
    }

    fun loadOfflineMessage(cacheDir: File, id: String): String? = synchronized(messageCacheLock) {
        if (!id.isSha256()) return@synchronized null
        runCatching {
            val directory = messageDirectory(cacheDir) ?: return@runCatching null
            val file = File(directory, id)
            val size = file.length()
            if (file.canonicalFile != file || !file.isFile || size > MESSAGE_MAX_BYTES ||
                file.lastModified() < System.currentTimeMillis() - maxAgeMillis) return@runCatching null
            val bytes = file.inputStream().use { input ->
                val result = ByteArrayOutputStream(size.toInt())
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (result.size() > MESSAGE_MAX_BYTES - count) return@runCatching null
                    result.write(buffer, 0, count)
                }
                result.toByteArray()
            }
            file.setLastModified(System.currentTimeMillis())
            bytes.toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun messageDirectory(cacheDir: File): File? =
        File(cacheDir.canonicalFile, MESSAGE_DIRECTORY_NAME).takeIf { it.canonicalFile == it }

    private fun removeExpiredFiles(directory: File) {
        val expirationTime = System.currentTimeMillis() - maxAgeMillis
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < expirationTime) {
                file.delete()
            }
        }
    }

    private fun ByteArray.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(this)
        return CharArray(bytes.size * 2).also { result ->
            bytes.forEachIndexed { index, byte ->
                val value = byte.toInt() and 0xff
                result[index * 2] = hexDigits[value ushr 4]
                result[index * 2 + 1] = hexDigits[value and 0x0f]
            }
        }.concatToString()
    }

    private fun String.isSha256(): Boolean =
        length == HASH_LENGTH && all { it in '0'..'9' || it in 'a'..'f' }
}

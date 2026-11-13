package me.rerere.rikkahub.data.ai.local

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * 本机的 LiteRT 模型库。
 *
 * 目录里的每个文件就是一个已导入的模型：模型列表直接读目录，删除模型就是删文件。
 * 设置里因此不会留下指向空文件的条目，重复导入也不会多存一份。
 */
class LiteRTModelStorage(private val directory: File) {

    data class Model(
        val file: File,
        val sizeBytes: Long,
        val lastModified: Long,
    ) {
        val displayName: String get() = file.nameWithoutExtension
        val path: String get() = file.absolutePath
    }

    sealed interface ImportResult {
        /** 新模型已写入模型库 */
        data class Imported(val model: Model) : ImportResult

        /** 模型库里已有逐字节相同的文件，直接复用 */
        data class Duplicate(val model: Model) : ImportResult
    }

    /** 已导入的模型，最近导入的排在前面 */
    fun list(): List<Model> = modelFiles()
        .sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name.lowercase() })
        .map { Model(it, it.length(), it.lastModified()) }

    fun totalSizeBytes(): Long = modelFiles().sumOf { it.length() }

    /**
     * 把 [source] 的内容存进模型库，调用方负责关闭这条流。
     *
     * 内容先落到临时文件，再与库里等长的文件逐字节比较；完全一致就丢掉临时文件并返回
     * [ImportResult.Duplicate]，同一个模型反复导入因此只占一份空间。
     */
    fun importModel(
        source: InputStream,
        displayName: String? = null,
        contentSize: Long? = null,
        onProgress: (Float) -> Unit = {},
    ): ImportResult {
        directory.mkdirs()
        // 上次进程被杀时留下的临时文件不属于模型库，顺手清掉
        deleteStaleTemps()
        val temp = File(directory, "$TEMP_PREFIX${System.nanoTime()}$TEMP_SUFFIX")
        try {
            var copied = 0L
            temp.outputStream().use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    copied += read
                    if (contentSize != null && contentSize > 0) {
                        onProgress((copied.toDouble() / contentSize).toFloat().coerceIn(0f, 1f))
                    }
                }
            }
            require(copied > 0L) { "所选文件为空" }
            onProgress(1f)

            modelFiles()
                .firstOrNull { it.length() == copied && it.hasSameContentAs(temp) }
                ?.let { existing ->
                    return ImportResult.Duplicate(
                        Model(existing, existing.length(), existing.lastModified())
                    )
                }

            val target = availableFile(sanitizeName(displayName))
            if (!temp.renameTo(target)) {
                try {
                    temp.copyTo(target)
                } catch (error: Throwable) {
                    target.delete()
                    throw error
                }
            }
            return ImportResult.Imported(Model(target, target.length(), target.lastModified()))
        } finally {
            temp.delete()
        }
    }

    /** 删除模型文件，返回是否真的删掉了 */
    fun delete(model: Model): Boolean = model.file.delete()

    private fun modelFiles(): List<File> = directory.listFiles()
        ?.filter { it.isFile && !it.name.startsWith(TEMP_PREFIX) }
        .orEmpty()

    private fun deleteStaleTemps() {
        val now = System.currentTimeMillis()
        directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(TEMP_PREFIX) }
            ?.filter { now - it.lastModified() > STALE_TEMP_MILLIS }
            ?.forEach { it.delete() }
    }

    // 同名但内容不同的模型另存为「名字 (2)」，不再用时间戳命名
    private fun availableFile(fileName: String): File {
        val direct = File(directory, fileName)
        if (!direct.exists()) return direct
        val extension = direct.name.substringAfterLast('.', "")
        val stem = if (extension.isEmpty()) direct.name else direct.name.removeSuffix(".$extension")
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        var index = 2
        while (true) {
            val candidate = File(directory, "$stem ($index)$suffix")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun sanitizeName(displayName: String?): String {
        val name = displayName.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(INVALID_NAME_CHARS, "_")
            .trim()
        return name.ifBlank { DEFAULT_FILE_NAME }
    }

    private fun File.hasSameContentAs(other: File): Boolean {
        if (length() != other.length()) return false
        return runCatching {
            inputStream().buffered(COPY_BUFFER_SIZE).use { leftStream ->
                other.inputStream().buffered(COPY_BUFFER_SIZE).use { rightStream ->
                    val left = ByteArray(COPY_BUFFER_SIZE)
                    val right = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        val leftCount = leftStream.readChunk(left)
                        val rightCount = rightStream.readChunk(right)
                        if (leftCount != rightCount) return@runCatching false
                        if (leftCount <= 0) break
                        for (index in 0 until leftCount) {
                            if (left[index] != right[index]) return@runCatching false
                        }
                    }
                    true
                }
            }
        }.getOrDefault(false)
    }

    private fun InputStream.readChunk(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    companion object {
        const val DIRECTORY_NAME = "litert-models"

        private const val TEMP_PREFIX = ".importing-"
        private const val TEMP_SUFFIX = ".tmp"
        private const val DEFAULT_FILE_NAME = "model.litertlm"
        private const val COPY_BUFFER_SIZE = 1 shl 20
        private const val STALE_TEMP_MILLIS = 60 * 60 * 1000L
        private val INVALID_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

        fun of(context: Context): LiteRTModelStorage =
            LiteRTModelStorage(File(context.filesDir, DIRECTORY_NAME))
    }
}

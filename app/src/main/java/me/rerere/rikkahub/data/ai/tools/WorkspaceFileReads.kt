package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import org.koin.java.KoinJavaComponent.getKoin
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * 按环境内绝对路径读文件。路径映射交给工作区后端, 本地工作区由它统一处理
 * /workspace、bind mount 与 Rootfs 内部路径。
 *
 * 与 WorkspaceTools 同包拆开, 只为让工具文件守住行数闸门。
 */

private const val MAX_READ_FILE_BYTES = 8L * 1024 * 1024
private const val READ_BUFFER_INITIAL_BYTES = 64 * 1024

internal suspend fun WorkspaceRepository.readTextInRootfs(
    workspaceId: String,
    path: String,
): String = readRootfsBuffer(workspaceId, path).toString(Charsets.UTF_8.name())

internal suspend fun WorkspaceRepository.readRootfsBuffer(
    workspaceId: String,
    path: String,
    knownSize: Long? = null,
): ByteArrayOutputStream {
    // 目录列表已经给过大小就按它先挡一次; 没给也不再单独 stat, 读的时候按上限截断。
    // SSH 工作区上每次 stat 都是一趟独立往返, 而拷贝本来就会把字节数带回来。
    if (knownSize != null) requireReadableSize(path, knownSize)
    val buffer = SizeLimitedOutputStream(path)
    exportAbsoluteFile(workspaceId, path, buffer)
    return buffer
}

internal suspend fun WorkspaceRepository.readImageInRootfs(
    workspaceId: String,
    path: String,
): List<UIMessagePart> {
    val bytes = readRootfsBuffer(workspaceId, path).toByteArray()

    val filesManager = getKoin().get<FilesManager>()
    val uris = filesManager.createChatFilesByByteArrays(listOf(bytes))
    return listOf(
        UIMessagePart.Image(url = uris.first().toString()),
        UIMessagePart.Text(
            buildJsonObject {
                put("path", path)
                put("description", "Image file read successfully")
            }.toString()
        ),
    )
}

private fun requireReadableSize(path: String, size: Long) {
    require(size <= MAX_READ_FILE_BYTES) {
        "File is too large to read: $path (${size / 1024 / 1024}MB, max ${MAX_READ_FILE_BYTES / 1024 / 1024}MB). Use shell commands like head, tail, or grep to read parts of it."
    }
}

/** 读到上限就中止: 大文件不必整份拉进内存, 也不必为了判断大小先 stat 一次 */
private class SizeLimitedOutputStream(
    private val path: String,
) : ByteArrayOutputStream(READ_BUFFER_INITIAL_BYTES) {
    private var total = 0

    override fun write(b: Int) {
        track(1)
        super.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        track(len)
        super.write(b, off, len)
    }

    private fun track(len: Int) {
        total += len
        if (total > MAX_READ_FILE_BYTES) {
            throw IOException(
                "File is too large to read: $path (max ${MAX_READ_FILE_BYTES / 1024 / 1024}MB). " +
                    "Use shell commands like head, tail, or grep to read parts of it."
            )
        }
    }
}

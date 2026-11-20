package me.rerere.workspace

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class WorkspaceFileTooLargeException(path: String, maxBytes: Int) :
    IOException("File is too large to read: $path (max $maxBytes bytes)")

/**
 * Find the limit error inside a wrapper chain. JSch reports a failing OutputStream write as
 * `SftpException` with our exception as the cause, so callers must not match on the top level only.
 */
internal fun Throwable.fileTooLargeCause(): WorkspaceFileTooLargeException? =
    generateSequence<Throwable>(this) { it.cause }
        .filterIsInstance<WorkspaceFileTooLargeException>()
        .firstOrNull()

internal fun readBoundedWorkspaceFile(
    path: String,
    fileSize: Long,
    maxBytes: Int,
    read: (OutputStream) -> Unit,
): ByteArray {
    require(maxBytes >= 0) { "maxBytes must not be negative" }
    if (fileSize > maxBytes) throw WorkspaceFileTooLargeException(path, maxBytes)
    val initialSize = fileSize.coerceIn(0, 16L * 1024).toInt()
    val output = BoundedFileBuffer(path, maxBytes, initialSize)
    read(output)
    return output.toByteArray()
}

private class BoundedFileBuffer(
    private val path: String,
    private val maxBytes: Int,
    initialSize: Int,
) : ByteArrayOutputStream(initialSize) {
    override fun write(value: Int) {
        checkSize(1)
        super.write(value)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        checkSize(length)
        super.write(bytes, offset, length)
    }

    private fun checkSize(length: Int) {
        if (count.toLong() + length > maxBytes) throw WorkspaceFileTooLargeException(path, maxBytes)
    }
}

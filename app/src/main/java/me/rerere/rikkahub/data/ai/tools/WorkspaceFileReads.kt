package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceFileTooLargeException
import org.koin.java.KoinJavaComponent.getKoin
import java.io.IOException

private const val MAX_READ_FILE_BYTES = 8 * 1024 * 1024
private const val DEFAULT_READ_LINES = 2000
private const val MAX_READ_LINES = 5000

private val IMAGE_EXTENSIONS = setOf(
    "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif", "avif", "ico",
)

private fun String.isImagePath(): Boolean =
    substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

internal fun createReadFileTool(
    targets: WorkspaceToolTargets,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_read_file",
    description = """
        Read a file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use ${targets.layout.workspaceDir} for the workspace files area.
        Supports UTF-8 text files and image files (png, jpg, jpeg, gif, webp, bmp, svg, heic, heif, avif, ico).
        Returns at most `limit` lines starting at `offset` (1-based); check totalLines to page through large files.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                putPathProperty(targets.layout, required = true)
                put("offset", buildJsonObject {
                    put("type", "integer")
                    put("description", "1-based line number to start reading from. Defaults to 1.")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum number of lines to read. Defaults to $DEFAULT_READ_LINES, max $MAX_READ_LINES.")
                })
            },
            required = targets.required("path"),
        )
    },
    needsApproval = { targets.needsApproval(it) },
    isReadOnly = true,
    execute = {
        val params = it.jsonObject
        val target = targets.resolveForExecution(params)
        val path = params.absolutePath("path")
        if (path.isImagePath()) {
            workspaceRepository.readImageInRootfs(target.id, path)
        } else {
            val text = workspaceRepository.readTextInRootfs(target.id, path)
            val page = pageText(
                text = text,
                offset = params.int("offset") ?: 1,
                limit = params.int("limit") ?: DEFAULT_READ_LINES,
            )
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("path", path)
                        put("text", page.text)
                        put("startLine", page.startLine)
                        put("endLine", page.endLine)
                        put("totalLines", page.totalLines)
                        if (page.truncated) put("truncated", true)
                        put("workspace", target.name)
                    }.toString()
                )
            )
        }
    },
)

internal suspend fun WorkspaceRepository.readTextInRootfs(workspaceId: String, path: String): String =
    readFileForTool(workspaceId, path).toString(Charsets.UTF_8)

private suspend fun WorkspaceRepository.readFileForTool(workspaceId: String, path: String): ByteArray =
    try {
        readAbsoluteFile(workspaceId, path, MAX_READ_FILE_BYTES)
    } catch (e: WorkspaceFileTooLargeException) {
        throw IOException(
            "File is too large for workspace_read_file: $path (max ${MAX_READ_FILE_BYTES / 1024 / 1024}MB).",
            e,
        )
    }

private suspend fun WorkspaceRepository.readImageInRootfs(
    workspaceId: String,
    path: String,
): List<UIMessagePart> {
    val bytes = readFileForTool(workspaceId, path)

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

internal data class PagedText(
    val text: String,
    val startLine: Int,
    val endLine: Int,
    val totalLines: Int,
    val truncated: Boolean,
)

/** 按 1-based 行号切片；offset 超出总行数时返回空文本，不报错 */
internal fun pageText(text: String, offset: Int, limit: Int): PagedText {
    val lines = if (text.isEmpty()) {
        emptyList()
    } else {
        text.split('\n').let { parts -> if (parts.last().isEmpty()) parts.dropLast(1) else parts }
    }
    if (lines.isEmpty()) return PagedText(text = "", startLine = 1, endLine = 0, totalLines = 0, truncated = false)

    val start = offset.coerceAtLeast(1)
    val count = limit.coerceIn(1, MAX_READ_LINES)
    if (start > lines.size) {
        return PagedText(text = "", startLine = start, endLine = start - 1, totalLines = lines.size, truncated = false)
    }
    val end = (start + count - 1).coerceAtMost(lines.size)
    return PagedText(
        text = lines.subList(start - 1, end).joinToString("\n"),
        startLine = start,
        endLine = end,
        totalLines = lines.size,
        truncated = end < lines.size,
    )
}

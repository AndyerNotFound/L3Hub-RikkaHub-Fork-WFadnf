package me.rerere.rikkahub.data.ai.tools

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.DiffMetadata
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.toMetadata
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.utils.generateUnifiedDiff
import me.rerere.workspace.WorkspaceCapabilities
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceLayout
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceShellStatus
import org.koin.java.KoinJavaComponent.getKoin
import java.io.ByteArrayOutputStream

private const val SHELL_TIMEOUT_MAX_SECONDS = 600L
private const val MAX_READ_FILE_BYTES = 8L * 1024 * 1024

val WorkspaceToolDefaultApprovals: Map<String, Boolean> = mapOf(
    "workspace_read_file" to false,
    "workspace_write_file" to false,
    "workspace_edit_file" to false,
    "workspace_shell" to true,
)

/** 直接写文件的工具, 只在支持 [WorkspaceCapabilities.fileWriteTools] 的工作区提供 */
val WorkspaceFileWriteToolNames: Set<String> = setOf("workspace_write_file", "workspace_edit_file")

fun resolveWorkspaceToolApproval(name: String, overrides: Map<String, Boolean>): Boolean =
    overrides[name] ?: WorkspaceToolDefaultApprovals[name] ?: false

private const val TAG = "WorkspaceTools"

private data class WorkspaceTarget(
    val id: String,
    val name: String,
    val layout: WorkspaceLayout,
    val approvalOverrides: Map<String, Boolean>,
    val supportsFileWrite: Boolean,
)

suspend fun createWorkspaceTools(
    workspaceIds: Collection<String>,
    workspaceRepository: WorkspaceRepository,
    cwd: String? = null,
): List<Tool> {
    val targets = workspaceIds
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .mapNotNull { id ->
            val workspace = workspaceRepository.getById(id) ?: return@mapNotNull null
            if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
                Log.d(TAG, "skip workspace tools: workspace=$id, status=${workspace.shellStatus}")
                return@mapNotNull null
            }
            val layout = workspaceRepository.layout(id) ?: return@mapNotNull null
            WorkspaceTarget(
                id = id,
                name = workspace.name,
                layout = layout,
                approvalOverrides = workspace.toolApprovalOverrides(),
                supportsFileWrite = workspace.capabilities().fileWriteTools,
            )
        }
        .toList()
    if (targets.isEmpty()) return emptyList()

    val shellCwd = cwd?.let(targets.first().layout::relativeToWorkspace)
    return buildList {
        add(createReadFileTool(targets, workspaceRepository))
        if (targets.any { it.supportsFileWrite }) {
            add(createWriteFileTool(targets, workspaceRepository))
            add(createEditFileTool(targets, workspaceRepository))
        }
        add(createShellTool(targets, workspaceRepository, shellCwd))
    }
}

private fun List<WorkspaceTarget>.workspaceNote(): String = if (size <= 1) {
    ""
} else {
    " Multiple workspaces are bound: ${joinToString { "${it.name} (${it.id})" }}." +
        " Set `workspace` to choose one; defaults to ${first().name}."
}

private fun JsonObjectBuilder.putWorkspaceProperty(targets: List<WorkspaceTarget>) {
    if (targets.size <= 1) return
    put("workspace", buildJsonObject {
        put("type", "string")
        put(
            "description",
            "Workspace to use. One of: ${targets.joinToString { "${it.name} (${it.id})" }}." +
                " Defaults to ${targets.first().name}."
        )
    })
}

private fun JsonObject.resolveWorkspace(targets: List<WorkspaceTarget>): WorkspaceTarget {
    if (targets.size == 1) return targets.first()
    val requested = string("workspace")?.trim().orEmpty()
    if (requested.isBlank()) return targets.first()
    return targets.firstOrNull { target ->
        target.id.equals(requested, ignoreCase = true) || target.name.equals(requested, ignoreCase = true)
    } ?: error(
        "Unknown workspace '$requested'. Available: ${targets.joinToString { "${it.name} (${it.id})" }}"
    )
}

private fun WorkspaceTarget.needsApproval(name: String): Boolean =
    resolveWorkspaceToolApproval(name, approvalOverrides)

private val IMAGE_EXTENSIONS = setOf(
    "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif", "avif", "ico",
)

private fun String.isImagePath(): Boolean =
    substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

private fun createReadFileTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_read_file",
    description = """
        Read a file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use ${targets.first().layout.workspaceDir} for the workspace files area.
        Supports UTF-8 text files and image files (png, jpg, jpeg, gif, webp, bmp, svg, heic, heif, avif, ico).
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                putPathProperty(targets.first().layout, required = true)
            },
            required = listOf("path"),
        )
    },
    needsApproval = { params ->
        params.jsonObject.resolveWorkspace(targets).needsApproval("workspace_read_file")
    },
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
        val path = params.absolutePath("path")
        if (path.isImagePath()) {
            workspaceRepository.readImageInRootfs(target.id, path)
        } else {
            val text = workspaceRepository.readTextInRootfs(target.id, path)
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("path", path)
                        put("text", text)
                        put("workspace", target.name)
                    }.toString()
                )
            )
        }
    },
)

private fun createWriteFileTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_write_file",
    description = """
        Write a UTF-8 text file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use ${targets.first().layout.workspaceDir} for the workspace files area.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                putPathProperty(targets.first().layout, required = true)
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "UTF-8 text content to write")
                })
                put("overwrite", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to overwrite an existing file. Defaults to true.")
                })
            },
            required = listOf("path", "text"),
        )
    },
    needsApproval = { params ->
        val target = params.jsonObject.resolveWorkspace(targets)
        target.needsApproval("workspace_write_file") ||
            params.jsonObject.pathOutsideWritableRoots(target.layout, "path")
    },
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
        require(target.supportsFileWrite) {
            "Workspace '${target.name}' does not support file write tools"
        }
        val path = params.absolutePath("path")
        val text = params.string("text") ?: error("text is required")
        val overwrite = params["overwrite"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
        val entry = workspaceRepository.writeAbsoluteText(target.id, path, text, overwrite)
        listOf(UIMessagePart.Text(entry.toJson().toString()))
    },
)

private fun createEditFileTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_edit_file",
    description = """
        Edit a UTF-8 text file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use ${targets.first().layout.workspaceDir} for the workspace files area.
        Provide old_text and new_text. By default old_text must occur exactly once; set replace_all=true to replace every occurrence.
        If no exact match is found, whitespace-tolerant line matching is attempted automatically.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                putPathProperty(targets.first().layout, required = true)
                put("old_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Exact text to replace")
                })
                put("new_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Replacement text")
                })
                put("replace_all", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to replace every occurrence. Defaults to false.")
                })
            },
            required = listOf("path", "old_text", "new_text"),
        )
    },
    needsApproval = { params ->
        val target = params.jsonObject.resolveWorkspace(targets)
        target.needsApproval("workspace_edit_file") ||
            params.jsonObject.pathOutsideWritableRoots(target.layout, "path")
    },
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
        require(target.supportsFileWrite) {
            "Workspace '${target.name}' does not support file write tools"
        }
        val path = params.absolutePath("path")
        val oldText = params.string("old_text") ?: error("old_text is required")
        val newText = params.string("new_text") ?: error("new_text is required")
        val replaceAll = params["replace_all"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        require(oldText.isNotEmpty()) { "old_text must not be empty" }

        val original = workspaceRepository.readTextInRootfs(target.id, path)
        // 逐级尝试 exact -> line_trimmed -> block_anchor 替换器, 见 TextReplacers.kt
        val result = try {
            replaceText(original, oldText, newText, replaceAll)
        } catch (e: IllegalArgumentException) {
            error("${e.message} (path: $path)")
        }
        val entry = workspaceRepository.writeAbsoluteText(target.id, path, result.updated, overwrite = true)
        val diff = generateUnifiedDiff(original, result.updated, entry.path)
        listOf(
            UIMessagePart.Text(
                text = buildJsonObject {
                    put("path", entry.path)
                    put("replacements", result.replacements)
                    if (result.strategy != ExactReplacer.name) put("matchStrategy", result.strategy)
                    put("sizeBytes", entry.sizeBytes)
                    put("updatedAt", entry.updatedAt)
                    put("workspace", target.name)
                }.toString(),
                // diff 存入 metadata 供 UI 渲染 diff view, 不会随工具结果发送给 API
                metadata = diff?.let { d -> DiffMetadata(diff = d).toMetadata() },
            )
        )
    },
)

private fun createShellTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
    defaultCwd: String? = null,
) = Tool(
    name = "workspace_shell",
    description = buildString {
        append(
            "Run a shell command in the assistant's bound workspace Rootfs. The workspace files area is mounted at ${targets.first().layout.workspaceDir}. "
        )
        append("Use cwd for a path relative to the workspace files root. ")
        if (!defaultCwd.isNullOrBlank()) {
            append("Defaults to '$defaultCwd'. ")
        }
        append("Requires Rootfs to be installed and ready.")
        append(targets.workspaceNote())
    },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                put("command", buildJsonObject {
                    put("type", "string")
                    put("description", "Shell command to run")
                })
                put("cwd", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        if (!defaultCwd.isNullOrBlank()) {
                            "Working directory relative to the workspace files root. Defaults to '$defaultCwd'."
                        } else {
                            "Working directory relative to the workspace files root. Defaults to root."
                        }
                    )
                })
                put("timeout", buildJsonObject {
                    put("type", "integer")
                    put(
                        "description",
                        "Command timeout in seconds. Defaults to 30, max $SHELL_TIMEOUT_MAX_SECONDS."
                    )
                })
            },
            required = listOf("command"),
        )
    },
    needsApproval = { params ->
        params.jsonObject.resolveWorkspace(targets).needsApproval("workspace_shell")
    },
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
        val command = params.string("command") ?: error("command is required")
        val cwd = target.layout.relativeToWorkspace(params.string("cwd") ?: defaultCwd.orEmpty())
        val timeoutMillis = params.string("timeout")?.toLongOrNull()
            ?.coerceIn(1L, SHELL_TIMEOUT_MAX_SECONDS)
            ?.times(1_000L)
            ?: WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS
        val result = workspaceRepository.executeCommand(target.id, command, cwd, timeoutMillis)
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("exitCode", result.exitCode)
                    put("stdout", result.stdout)
                    put("stderr", result.stderr)
                    put("timedOut", result.timedOut)
                    if (result.truncated) put("truncated", true)
                    put("workspace", target.name)
                }.toString()
            )
        )
    },
)

private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private suspend fun WorkspaceRepository.readTextInRootfs(
    workspaceId: String,
    path: String,
): String = readRootfsBuffer(workspaceId, path).toString(Charsets.UTF_8.name())

/**
 * 按环境内绝对路径读入内存。路径映射交给工作区后端, 本地工作区由它统一处理
 * /workspace、bind mount 与 Rootfs 内部路径。
 */
private suspend fun WorkspaceRepository.readRootfsBuffer(
    workspaceId: String,
    path: String,
): ByteArrayOutputStream {
    val size = absoluteFileSize(workspaceId, path)
    require(size <= MAX_READ_FILE_BYTES) {
        "File is too large to read: $path (${size / 1024 / 1024}MB, max ${MAX_READ_FILE_BYTES / 1024 / 1024}MB). Use shell commands like head, tail, or grep to read parts of it."
    }
    return ByteArrayOutputStream(size.toInt()).also { exportAbsoluteFile(workspaceId, path, it) }
}

private suspend fun WorkspaceRepository.readImageInRootfs(
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

private fun kotlinx.serialization.json.JsonObject.absolutePath(name: String): String {
    val path = string(name)?.replace('\\', '/')?.trim() ?: error("$name is required")
    require(path.isNotBlank()) { "$name is required" }
    require(path.startsWith("/")) { "$name must be an absolute path inside Rootfs" }
    require(!path.contains('\u0000')) { "$name contains invalid character" }
    return path
}

// 免强制审批的可写安全区由工作区后端给出, 本地为工作区文件目录、临时目录和技能目录
private fun kotlinx.serialization.json.JsonElement.pathOutsideWritableRoots(
    layout: WorkspaceLayout,
    name: String,
): Boolean =
    runCatching {
        !layout.isWritable(jsonObject.absolutePath(name))
    }.getOrDefault(true)

private fun JsonObjectBuilder.putPathProperty(layout: WorkspaceLayout, required: Boolean) {
    put("path", buildJsonObject {
        put("type", "string")
        put(
            "description",
            if (required) {
                "Absolute path inside Rootfs. Use ${layout.workspaceDir} for the workspace files area."
            } else {
                "Optional absolute path inside Rootfs. Use ${layout.workspaceDir} for the workspace files area."
            }
        )
    })
}

private fun WorkspaceFileEntry.toJson() = buildJsonObject {
    put("path", path)
    put("name", name)
    put("isDirectory", isDirectory)
    put("sizeBytes", sizeBytes)
    put("updatedAt", updatedAt)
}

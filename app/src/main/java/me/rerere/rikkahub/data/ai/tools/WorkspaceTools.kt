package me.rerere.rikkahub.data.ai.tools

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.ExecutionPermission
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.workspace.WorkspaceSnapshotStore
import me.rerere.rikkahub.service.BackgroundShellManager
import me.rerere.workspace.WorkspaceCapabilities
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceLayout
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspacePaths
import me.rerere.workspace.WorkspaceShellStatus
import org.koin.java.KoinJavaComponent.getKoin
import kotlin.uuid.Uuid

internal const val DEFAULT_READ_LINES = 2000
internal const val MAX_READ_LINES = 5000

val WorkspaceToolDefaultApprovals: Map<String, Boolean> = mapOf(
    "workspace_read_file" to false,
    "workspace_list" to false,
    "workspace_glob" to false,
    "workspace_grep" to false,
    "workspace_write_file" to false,
    "workspace_edit_file" to false,
    "workspace_apply_patch" to false,
    "workspace_shell" to true,
    "workspace_git" to true,
    "workspace_test" to true,
    "workspace_repo_map" to false,
)

/** 直接写文件的工具, 只在支持 [WorkspaceCapabilities.fileWriteTools] 的工作区提供 */
val WorkspaceFileWriteToolNames: Set<String> = setOf(
    "workspace_write_file",
    "workspace_edit_file",
    "workspace_apply_patch",
)

fun resolveWorkspaceToolApproval(name: String, overrides: Map<String, Boolean>): Boolean =
    overrides[name] ?: WorkspaceToolDefaultApprovals[name] ?: false

private const val TAG = "WorkspaceTools"

internal data class WorkspaceTarget(
    val id: String,
    val name: String,
    val layout: WorkspaceLayout,
    val approvalOverrides: Map<String, Boolean>,
    val supportsFileWrite: Boolean,
    val supportsBackgroundShell: Boolean = false,
    val executionPermission: ExecutionPermission = ExecutionPermission.APPROVAL,
)

suspend fun createWorkspaceTools(
    workspaceIds: Collection<String>,
    workspaceRepository: WorkspaceRepository,
    cwds: Map<String, String> = emptyMap(),
    conversationId: Uuid? = null,
    planMode: Boolean = false,
    executionPermission: ExecutionPermission = ExecutionPermission.APPROVAL,
    backgroundShellManager: BackgroundShellManager? = null,
): List<Tool> {
    // 只读档位沿用计划模式的过滤规则: 写文件 / 改仓库 / 跑测试都不注册, shell 只放只读命令
    val readOnly = planMode || executionPermission == ExecutionPermission.READ_ONLY
    val targets = workspaceIds
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
                supportsBackgroundShell = workspace.capabilities().backgroundShell,
                executionPermission = executionPermission,
            )
        }
        .toList()
    if (targets.isEmpty()) return emptyList()

    return buildList {
        add(createReadFileTool(targets, workspaceRepository))
        add(createListTool(targets, workspaceRepository, cwds))
        add(createGlobTool(targets, workspaceRepository, cwds))
        add(createGrepTool(targets, workspaceRepository, cwds))
        add(createRepoMapTool(targets, workspaceRepository, cwds))
        val snapshots = conversationId?.let {
            runCatching { getKoin().get<WorkspaceSnapshotStore>() }.getOrNull()
        }
        // 计划模式和「只读」档位只放只读工具：写文件、跑测试这类有副作用的工具直接不注册
        if (targets.any { it.supportsFileWrite } && !readOnly) {
            add(createWriteFileTool(targets, workspaceRepository, snapshots, conversationId))
            add(createEditFileTool(targets, workspaceRepository, snapshots, conversationId))
            add(createApplyPatchTool(targets, workspaceRepository, snapshots, conversationId))
        }
        add(createGitTool(targets, workspaceRepository, readOnly))
        if (!readOnly) add(createTestTool(targets, workspaceRepository))
        add(
            createShellTool(
                targets = targets,
                workspaceRepository = workspaceRepository,
                workspaceCwds = cwds,
                readOnly = readOnly,
                backgroundShellManager = backgroundShellManager,
                conversationId = conversationId,
            )
        )
        // 后台命令按会话归属；只有支持后台执行的工作区才提供输出/终止工具
        if (backgroundShellManager != null && conversationId != null &&
            targets.any { it.supportsBackgroundShell }
        ) {
            val background = BackgroundShell(backgroundShellManager, conversationId)
            add(createShellOutputTool(background))
            add(createShellKillTool(background))
        }
    }
}

/**
 * 「工具覆写」页的固定目录：还没有可用工作区时也要能列出全部工作区工具。
 *
 * 只用默认的 Rootfs 目录约定生成定义，execute 不会被调用；有可用工作区时目录本身仍按真实绑定生成。
 */
fun createWorkspaceCatalogTools(
    workspaceRepository: WorkspaceRepository,
    backgroundShellManager: BackgroundShellManager?,
): List<Tool> {
    val layout = WorkspaceLayout(
        workspaceDir = WorkspaceManager.ROOTFS_WORKSPACE_DIR,
        writableRoots = listOf(WorkspaceManager.ROOTFS_WORKSPACE_DIR, "/tmp"),
    )
    val targets = listOf(
        WorkspaceTarget(
            id = "catalog",
            name = "workspace",
            layout = layout,
            approvalOverrides = emptyMap(),
            supportsFileWrite = true,
            supportsBackgroundShell = true,
        )
    )
    val conversationId = Uuid.random()
    return buildList {
        add(createReadFileTool(targets, workspaceRepository))
        add(createListTool(targets, workspaceRepository))
        add(createGlobTool(targets, workspaceRepository))
        add(createGrepTool(targets, workspaceRepository))
        add(createRepoMapTool(targets, workspaceRepository))
        add(createWriteFileTool(targets, workspaceRepository, snapshots = null, conversationId = conversationId))
        add(createEditFileTool(targets, workspaceRepository, snapshots = null, conversationId = conversationId))
        add(createApplyPatchTool(targets, workspaceRepository, snapshots = null, conversationId = conversationId))
        add(createGitTool(targets, workspaceRepository, readOnly = false))
        add(createTestTool(targets, workspaceRepository))
        add(
            createShellTool(
                targets = targets,
                workspaceRepository = workspaceRepository,
                backgroundShellManager = backgroundShellManager,
                conversationId = conversationId,
            )
        )
        if (backgroundShellManager != null) {
            val background = BackgroundShell(backgroundShellManager, conversationId)
            add(createShellOutputTool(background))
            add(createShellKillTool(background))
        }
    }
}

internal fun List<WorkspaceTarget>.workspaceNote(): String = if (size <= 1) {
    ""
} else {
    " Multiple workspaces are bound: ${joinToString { "${it.id} (${it.name})" }}." +
        " Set `workspace` to one of these IDs; defaults to ${first().id}."
}

internal fun JsonObjectBuilder.putWorkspaceProperty(targets: List<WorkspaceTarget>) {
    if (targets.size <= 1) return
    put("workspace", buildJsonObject {
        put("type", "string")
        put(
            "description",
            "Workspace ID to use. One of: ${targets.joinToString { "${it.id} (${it.name})" }}." +
                " Defaults to ${targets.first().id}. Prefer the ID; names may contain quotes."
        )
    })
}

internal fun JsonObject.resolveWorkspace(targets: List<WorkspaceTarget>): WorkspaceTarget {
    if (targets.size == 1) return targets.first()
    val requested = string("workspace")?.trim().orEmpty()
    if (requested.isBlank()) return targets.first()

    targets.firstOrNull { target ->
        target.id.equals(requested, ignoreCase = true) || target.name.equals(requested, ignoreCase = true)
    }?.let { return it }

    val normalized = requested.normalizeWorkspaceSelector()
    if (normalized.isNotEmpty()) {
        val matches = targets.filter { target ->
            target.id.normalizeWorkspaceSelector() == normalized ||
                target.name.normalizeWorkspaceSelector() == normalized
        }
        if (matches.size == 1) return matches.single()
        if (matches.size > 1) {
            error("Ambiguous workspace '$requested'. Use the workspace ID. Available: ${targets.availableWorkspaces()}")
        }
    }

    val idPrefixMatches = targets.filter { it.id.startsWith(requested, ignoreCase = true) }
    if (idPrefixMatches.size == 1) return idPrefixMatches.single()

    error("Unknown workspace '$requested'. Use the workspace ID. Available: ${targets.availableWorkspaces()}")
}

/** 审批回调不能抛异常，否则会中断整轮生成；解析失败时交给调用方按需要审批处理。 */
internal fun JsonObject.resolveWorkspaceOrNull(targets: List<WorkspaceTarget>): WorkspaceTarget? =
    runCatching { resolveWorkspace(targets) }.getOrNull()

private fun List<WorkspaceTarget>.availableWorkspaces(): String =
    joinToString { "${it.id} (${it.name})" }

/** 名称可能带引号，模型有时只保留其中一部分；比较时去掉各类引号并归一小写与空白。 */
private fun String.normalizeWorkspaceSelector(): String =
    filterNot { it in WORKSPACE_QUOTES }
        .replace(WHITESPACE, " ")
        .trim()
        .lowercase()

private val WORKSPACE_QUOTES = setOf('"', '\'', '“', '”', '‘', '’', '＂')
private val WHITESPACE = Regex("\\s+")

/**
 * 单条工具在当前执行权限下是否需要用户确认。
 *
 * [ExecutionPermission.READ_ONLY] 下写类工具根本不会注册, 这里保守地要求确认;
 * [ExecutionPermission.READ_WRITE] 只放行写文件, 命令类工具仍然确认;
 * [ExecutionPermission.FULL] 一律放行。
 */
internal fun WorkspaceTarget.needsApproval(name: String): Boolean = when (executionPermission) {
    ExecutionPermission.FULL -> false
    ExecutionPermission.READ_WRITE ->
        if (name in WorkspaceFileWriteToolNames) false else WorkspaceToolDefaultApprovals[name] ?: true
    ExecutionPermission.READ_ONLY -> true
    ExecutionPermission.APPROVAL -> resolveWorkspaceToolApproval(name, approvalOverrides)
}

/**
 * 写文件类工具的审批: [ExecutionPermission.FULL] 全部放行; 其余档位下写到可写区之外一律确认,
 * 免得「免审批写入」被用来改系统目录。
 */
internal fun WorkspaceTarget.requiresWriteApproval(name: String, outsideWritableRoots: Boolean): Boolean =
    when (executionPermission) {
        ExecutionPermission.FULL -> false
        ExecutionPermission.READ_WRITE -> outsideWritableRoots
        ExecutionPermission.READ_ONLY -> true
        ExecutionPermission.APPROVAL -> needsApproval(name) || outsideWritableRoots
    }

internal fun JsonObjectBuilder.putEditedFiles(files: List<JsonObject>) {
    if (files.isEmpty()) return
    put("editedFiles", buildJsonArray { files.forEach { add(it) } })
}

/** 单条「已修改文件」记录, [created] 为 null 表示拿不到创建时间, 不确定是不是本轮新建的 */
internal fun editedFileJson(path: String, created: Boolean? = null): JsonObject = buildJsonObject {
    put("path", path)
    if (created != null) put("created", created)
}

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
        Returns at most `limit` lines starting at `offset` (1-based); check totalLines to page through large files.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                putPathProperty(targets.first().layout, required = true)
                put("offset", buildJsonObject {
                    put("type", "integer")
                    put("description", "1-based line number to start reading from. Defaults to 1.")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum number of lines to read. Defaults to $DEFAULT_READ_LINES, max $MAX_READ_LINES.")
                })
            },
            required = listOf("path"),
        )
    },
    needsApproval = { params ->
        params.jsonObject.resolveWorkspaceOrNull(targets)?.needsApproval("workspace_read_file") ?: true
    },
    isReadOnly = true,
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
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

internal fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

internal fun kotlinx.serialization.json.JsonObject.absolutePathOrNull(name: String): String? {
    val raw = string(name) ?: return null
    val path = raw.replace('\\', '/').trim()
    require(path.isNotBlank()) { "$name is required" }
    return WorkspacePaths.normalize(path)
        ?: error("$name must be an absolute path inside Rootfs: $raw")
}

internal fun kotlinx.serialization.json.JsonObject.absolutePath(name: String): String =
    absolutePathOrNull(name) ?: error("$name is required")

internal fun JsonObjectBuilder.putPathProperty(
    layout: WorkspaceLayout,
    required: Boolean,
    defaultHint: String? = null,
) {
    put("path", buildJsonObject {
        put("type", "string")
        put(
            "description",
            buildString {
                append(
                    if (required) {
                        "Absolute path inside Rootfs. Use ${layout.workspaceDir} for the workspace files area."
                    } else {
                        "Optional absolute path inside Rootfs. Use ${layout.workspaceDir} for the workspace files area."
                    }
                )
                if (!required && defaultHint != null) append(" Defaults to $defaultHint.")
            }
        )
    })
}

internal fun WorkspaceFileEntry.toJson() = buildJsonObject {
    put("path", path)
    put("name", name)
    put("isDirectory", isDirectory)
    put("sizeBytes", sizeBytes)
    put("updatedAt", updatedAt)
}

internal fun kotlinx.serialization.json.JsonObject.int(name: String): Int? =
    this[name]?.jsonPrimitive?.contentOrNull?.trim()?.toIntOrNull()

internal fun kotlinx.serialization.json.JsonObject.boolean(name: String): Boolean? =
    this[name]?.jsonPrimitive?.contentOrNull?.trim()?.toBooleanStrictOrNull()
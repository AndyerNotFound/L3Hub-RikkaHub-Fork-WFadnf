package me.rerere.rikkahub.data.ai.tools

import android.util.Log
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.ExecutionPermission
import me.rerere.rikkahub.data.model.WorkspaceToolMode
import me.rerere.rikkahub.data.model.toolMode
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.BackgroundShellManager
import me.rerere.workspace.WorkspaceCapabilities
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceLayout
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspacePaths
import me.rerere.workspace.WorkspaceShellStatus
import kotlin.uuid.Uuid

/** 直接写文件的工具, 只在支持 [WorkspaceCapabilities.fileWriteTools] 的工作区提供 */
val WorkspaceFileWriteToolNames: Set<String> = setOf(
    "workspace_write_file",
    "workspace_edit_file",
    "workspace_apply_patch",
)

private const val TAG = "WorkspaceTools"

internal data class WorkspaceTarget(
    val id: String,
    val name: String,
    val readyLayout: WorkspaceLayout?,
    val toolModes: Map<String, WorkspaceToolMode>,
    val supportsFileWrite: Boolean,
    val supportsBackgroundShell: Boolean = false,
    val isLocalRootfs: Boolean = false,
    val executionPermission: ExecutionPermission = ExecutionPermission.APPROVAL,
) {
    val layout: WorkspaceLayout get() = checkNotNull(readyLayout) { "Workspace '$name' is not ready." }

    fun allowsTool(name: String): Boolean =
        readyLayout != null && toolModes.toolMode(name) != WorkspaceToolMode.DISABLED && when {
            name in WorkspaceFileWriteToolNames -> {
                supportsFileWrite && executionPermission != ExecutionPermission.READ_ONLY
            }

            name == WORKSPACE_TEST_TOOL_NAME -> executionPermission != ExecutionPermission.READ_ONLY
            else -> true
        }
}

internal data class WorkspaceToolSet(
    val tools: List<Tool>,
    val namesByWorkspace: Map<String, Set<String>>,
    val outputReaderWorkspaceIds: List<String>,
)

internal suspend fun createWorkspaceTools(
    workspaceIds: Collection<String>,
    workspaceRepository: WorkspaceRepository,
    cwds: Map<String, String> = emptyMap(),
    conversationId: Uuid? = null,
    executionPermission: ExecutionPermission = ExecutionPermission.APPROVAL,
    backgroundShellManager: BackgroundShellManager? = null,
): WorkspaceToolSet {
    val ids = workspaceIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val defaultId = ids.firstOrNull()
        ?: return WorkspaceToolSet(emptyList(), emptyMap(), emptyList())
    // Resolve against all known bindings, not just the ones that can currently execute.
    val targets = ids.mapNotNull { id ->
        val workspace = workspaceRepository.getById(id) ?: return@mapNotNull null
        val capabilities = workspace.capabilities()
        val layout = if (workspace.shellStatus == WorkspaceShellStatus.READY.name && capabilities.terminal) {
            workspaceRepository.layout(workspace)
        } else {
            Log.d(TAG, "skip workspace tools: workspace=$id, status=${workspace.shellStatus}")
            null
        }
        WorkspaceTarget(
            id = id,
            name = workspace.name,
            readyLayout = layout,
            toolModes = workspace.toolModes(),
            supportsFileWrite = capabilities.fileWriteTools,
            supportsBackgroundShell = capabilities.backgroundShell,
            isLocalRootfs = capabilities.rootfs,
            executionPermission = executionPermission,
        )
    }
    return buildWorkspaceTools(
        targets = targets,
        defaultId = defaultId,
        workspaceRepository = workspaceRepository,
        cwds = cwds,
        readOnly = executionPermission == ExecutionPermission.READ_ONLY,
        backgroundShellManager = backgroundShellManager,
        conversationId = conversationId,
    )
}

/**
 * 「工具覆写」只编辑定义。即使所有真实工作区都禁用某个工具，也要能编辑其定义。
 */
internal fun createWorkspaceCatalogTools(
    workspaceRepository: WorkspaceRepository,
    backgroundShellManager: BackgroundShellManager?,
): List<Tool> {
    val layout = WorkspaceLayout(
        workspaceDir = WorkspaceManager.ROOTFS_WORKSPACE_DIR,
        writableRoots = listOf(WorkspaceManager.ROOTFS_WORKSPACE_DIR, "/tmp"),
    )
    val target = WorkspaceTarget(
        id = "catalog",
        name = "workspace",
        readyLayout = layout,
        toolModes = emptyMap(),
        supportsFileWrite = true,
        supportsBackgroundShell = true,
    )
    return buildWorkspaceTools(
        targets = listOf(target),
        defaultId = target.id,
        workspaceRepository = workspaceRepository,
        cwds = emptyMap(),
        readOnly = false,
        backgroundShellManager = backgroundShellManager,
        conversationId = Uuid.random(),
    ).tools
}

private fun buildWorkspaceTools(
    targets: List<WorkspaceTarget>,
    defaultId: String,
    workspaceRepository: WorkspaceRepository,
    cwds: Map<String, String>,
    readOnly: Boolean,
    backgroundShellManager: BackgroundShellManager?,
    conversationId: Uuid?,
): WorkspaceToolSet {
    val tools = mutableListOf<Tool>()
    val namesByWorkspace = mutableMapOf<String, MutableSet<String>>()
    fun add(tool: Tool, enabled: List<WorkspaceTarget>) {
        tools.add(tool)
        enabled.forEach { namesByWorkspace.getOrPut(it.id) { linkedSetOf() }.add(tool.name) }
    }
    fun register(name: String, create: (WorkspaceToolTargets) -> Tool) {
        val scope = WorkspaceToolTargets(name, targets, defaultId, workspaceRepository::getById)
        if (scope.enabled.isNotEmpty()) add(create(scope), scope.enabled)
    }

    register("workspace_read_file") { createReadFileTool(it, workspaceRepository) }
    register("workspace_list") { createListTool(it, workspaceRepository, cwds) }
    register("workspace_glob") { createGlobTool(it, workspaceRepository, cwds) }
    register("workspace_grep") { createGrepTool(it, workspaceRepository, cwds) }
    register("workspace_repo_map") { createRepoMapTool(it, workspaceRepository, cwds) }
    register("workspace_write_file") { createWriteFileTool(it, workspaceRepository) }
    register("workspace_edit_file") { createEditFileTool(it, workspaceRepository) }
    register("workspace_apply_patch") { createApplyPatchTool(it, workspaceRepository) }
    register(WORKSPACE_GIT_TOOL_NAME) { createGitTool(it, workspaceRepository, readOnly) }
    register(WORKSPACE_TEST_TOOL_NAME) { createTestTool(it, workspaceRepository) }

    val shellTargets = WorkspaceToolTargets(WORKSPACE_SHELL_TOOL_NAME, targets, defaultId, workspaceRepository::getById)
    val background = if (backgroundShellManager != null && conversationId != null) {
        val enabled = shellTargets.enabled.filter { it.supportsBackgroundShell }
        if (enabled.isEmpty()) null else BackgroundShell(backgroundShellManager, conversationId, enabled, workspaceRepository)
    } else {
        null
    }
    if (shellTargets.enabled.isNotEmpty()) {
        add(
            createShellTool(
                targets = shellTargets,
                workspaceRepository = workspaceRepository,
                workspaceCwds = cwds,
                readOnly = readOnly,
                background = background,
            ),
            shellTargets.enabled,
        )
    }
    if (background != null) {
        add(createShellOutputTool(background), background.targets)
        if (!readOnly) add(createShellKillTool(background), background.targets)
    }
    return WorkspaceToolSet(
        tools = tools,
        namesByWorkspace = namesByWorkspace,
        outputReaderWorkspaceIds = shellTargets.enabled.filter { it.isLocalRootfs }.map { it.id },
    )
}

internal class WorkspaceToolTargets(
    private val toolName: String,
    private val bound: List<WorkspaceTarget>,
    private val defaultId: String,
    private val loadWorkspace: suspend (String) -> WorkspaceEntity?,
) {
    val enabled = bound.filter { it.allowsTool(toolName) }
    val layout get() = enabled.first().layout

    private val defaultEnabled = enabled.any { it.id == defaultId }
    private val showSelector get() = bound.size > 1 || !defaultEnabled
    private val defaultHint get() = if (defaultEnabled) {
        " Defaults to $defaultId."
    } else {
        " Specify a workspace ID; the default workspace ($defaultId) is unavailable for this tool."
    }

    fun workspaceNote(): String = if (showSelector) {
        " Workspaces for this tool: ${enabled.availableWorkspaces()}.$defaultHint"
    } else {
        ""
    }

    fun putWorkspaceProperty(builder: JsonObjectBuilder) {
        if (!showSelector) return
        builder.put("workspace", buildJsonObject {
            put("type", "string")
            put("description", "Workspace ID. One of: ${enabled.availableWorkspaces()}.$defaultHint")
        })
    }

    fun required(vararg names: String): List<String>? =
        (names.toList() + if (defaultEnabled) emptyList() else listOf("workspace")).ifEmpty { null }

    fun resolve(params: JsonObject): WorkspaceTarget {
        val requested = params.requestedWorkspace()
        // Approval resumption rebuilds the tool list. Never derive the implicit target from that list.
        val target = if (requested == null) {
            bound.firstOrNull { it.id == defaultId }
                ?: error("Default workspace '$defaultId' is unavailable. ${workspaceNote().trim()}")
        } else {
            bound.resolveWorkspaceSelector(requested)
        }
        check(target in enabled) {
            "Tool $toolName is disabled or unavailable in workspace '${target.name}' (${target.id})." +
                " Available: ${enabled.availableWorkspaces()}"
        }
        return target
    }

    fun needsApproval(
        args: JsonElement,
        requiresApproval: (WorkspaceTarget, JsonObject) -> Boolean = { target, _ -> target.needsApproval(toolName) },
    ): Boolean {
        val params = args as? JsonObject ?: return false
        // Invalid or disabled targets are rejected by execute without asking for a useless approval.
        val target = runCatching { resolve(params) }.getOrNull() ?: return false
        return requiresApproval(target, params)
    }

    suspend fun resolveForExecution(params: JsonObject): WorkspaceTarget = resolve(params).also { target ->
        val workspace = loadWorkspace(target.id) ?: error("Workspace '${target.id}' no longer exists.")
        workspace.requireToolEnabled(toolName)
    }
}

internal fun JsonObjectBuilder.putWorkspaceProperty(targets: WorkspaceToolTargets) = targets.putWorkspaceProperty(this)

internal fun WorkspaceEntity.requireToolEnabled(name: String) {
    check(isToolEnabled(name)) { "Tool $name is disabled or unavailable in workspace '${this.name}'." }
}

internal fun WorkspaceEntity.isToolEnabled(name: String): Boolean =
    shellStatus == WorkspaceShellStatus.READY.name &&
        capabilities().terminal &&
        toolModes().toolMode(name) != WorkspaceToolMode.DISABLED &&
        (name !in WorkspaceFileWriteToolNames || capabilities().fileWriteTools)

private fun JsonObject.requestedWorkspace(): String? {
    val value = this["workspace"] ?: return null
    if (value == JsonNull) return null
    check(value is JsonPrimitive && value.isString) { "workspace must be a workspace ID or name." }
    return value.content.trim().takeIf { it.isNotEmpty() }
}

private fun List<WorkspaceTarget>.resolveWorkspaceSelector(requested: String): WorkspaceTarget {
    val normalized = requested.normalizeWorkspaceSelector()
    if (WORKSPACE_ID.matches(normalized)) {
        return firstOrNull { it.id.equals(normalized, ignoreCase = true) }
            ?: error("Unknown workspace '$requested'. Use the workspace ID. Available: ${availableWorkspaces()}")
    }
    firstOrNull { it.id.equals(requested, ignoreCase = true) }?.let { return it }

    if (normalized.isNotEmpty()) {
        val matches = filter { target ->
            target.id.startsWith(normalized, ignoreCase = true) || target.name.normalizeWorkspaceSelector() == normalized
        }
        if (matches.size == 1) return matches.single()
        if (matches.size > 1) {
            error("Ambiguous workspace '$requested'. Use the workspace ID. Available: ${availableWorkspaces()}")
        }
    }

    error("Unknown workspace '$requested'. Use the workspace ID. Available: ${availableWorkspaces()}")
}

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
private val WORKSPACE_ID = Regex("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")

/** Read-only mode limits what can run; the tool's approval mode applies in APPROVAL only. */
internal fun WorkspaceTarget.needsApproval(name: String): Boolean =
    executionPermission == ExecutionPermission.APPROVAL && toolModes.toolMode(name) == WorkspaceToolMode.APPROVAL

/**
 * 写文件类工具的审批: [ExecutionPermission.FULL] 全部放行; 其余档位下写到可写区之外一律确认,
 * 免得「免审批写入」被用来改系统目录。
 */
internal fun WorkspaceTarget.requiresWriteApproval(name: String, outsideWritableRoots: Boolean): Boolean =
    when (executionPermission) {
        ExecutionPermission.FULL -> false
        ExecutionPermission.READ_ONLY -> true
        ExecutionPermission.APPROVAL -> needsApproval(name) || outsideWritableRoots
    }

internal fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

internal fun kotlinx.serialization.json.JsonObject.absolutePathOrNull(name: String): String? {
    val raw = string(name) ?: return null
    require(raw.isNotBlank()) { "$name is required" }
    return WorkspacePaths.normalize(raw)
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

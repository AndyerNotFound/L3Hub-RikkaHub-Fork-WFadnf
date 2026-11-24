package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.ExecutionPermission
import me.rerere.rikkahub.data.model.WorkspaceCwd
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.BackgroundShellManager
import me.rerere.rikkahub.service.BackgroundShellTask
import me.rerere.workspace.WorkspaceManager
import kotlin.uuid.Uuid

internal const val SHELL_TIMEOUT_MAX_SECONDS = 600L
internal const val WORKSPACE_SHELL_TOOL_NAME = "workspace_shell"

/** Dangerous commands still need approval in APPROVAL mode even if Shell is set to AUTO. */
internal fun WorkspaceTarget.requiresShellApproval(command: String): Boolean =
    executionPermission == ExecutionPermission.APPROVAL &&
        (needsApproval(WORKSPACE_SHELL_TOOL_NAME) || WorkspaceCommandPolicy.classify(command) == WorkspaceCommandPolicy.Risk.DANGEROUS)

internal class BackgroundShell(
    val manager: BackgroundShellManager,
    val conversationId: Uuid,
    val targets: List<WorkspaceTarget>,
    private val workspaceRepository: WorkspaceRepository,
) {
    private fun task(taskId: String): BackgroundShellTask? =
        manager.tasks.value[taskId]?.takeIf { it.conversationId == conversationId }

    fun needsApproval(args: JsonElement): Boolean {
        val taskId = (args as? JsonObject)?.string("task_id") ?: return false
        val task = task(taskId) ?: return false
        return targets.firstOrNull { it.id == task.workspaceId }?.needsApproval(WORKSPACE_SHELL_TOOL_NAME) == true
    }

    suspend fun requireTaskAccess(taskId: String) {
        val task = task(taskId) ?: error("Unknown background command: $taskId.")
        check(targets.any { it.id == task.workspaceId }) { "Shell is unavailable in this background command's workspace." }
        val workspace = workspaceRepository.getById(task.workspaceId)
            ?: error("The background command's workspace no longer exists.")
        workspace.requireToolEnabled(WORKSPACE_SHELL_TOOL_NAME)
        check(workspace.capabilities().backgroundShell) {
            "Workspace '${workspace.name}' does not support background commands."
        }
    }
}

internal fun createShellTool(
    targets: WorkspaceToolTargets,
    workspaceRepository: WorkspaceRepository,
    workspaceCwds: Map<String, String> = emptyMap(),
    readOnly: Boolean = false,
    background: BackgroundShell? = null,
) = Tool(
    name = WORKSPACE_SHELL_TOOL_NAME,
    description = buildString {
        append(
            "Run a shell command in the selected workspace. The workspace files area is at ${targets.layout.workspaceDir}. "
        )
        append("Use cwd for a path relative to the workspace files root. ")
        if (workspaceCwds.isNotEmpty()) {
            append("Defaults to the directory picked for each workspace. ")
        }
        append("Set background=true for long-running commands (dev servers, watchers): it returns a pid and a log file immediately.")
        if (background != null) {
            append(
                " On supported workspaces prefer run_in_background: it returns a task ID and sends a completion notice. " +
                    "Use workspace_shell_output to read its output."
            )
            if (!readOnly) append(" Use workspace_shell_kill to stop it.")
        }
        append("Requires Rootfs to be installed and ready.")
        if (readOnly) {
            append(
                " Only read-only commands are allowed in this mode: anything that writes files, installs packages" +
                    " or changes state is rejected."
            )
        }
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
                        "Working directory relative to the workspace files root. " +
                            "Defaults to the directory picked for that workspace, or root."
                    )
                })
                put("timeout", buildJsonObject {
                    put("type", "integer")
                    put(
                        "description",
                        buildString {
                            append("Command timeout in seconds. Defaults to 30, max $SHELL_TIMEOUT_MAX_SECONDS.")
                            append(" Ignored when running in the background.")
                        }
                    )
                })
                put("background", buildJsonObject {
                    put("type", "boolean")
                    put(
                        "description",
                        "Run the command detached and return at once with a pid and a log path. " +
                            "Read the log with `tail -n 100 <log>`. " +
                            (if (readOnly) "" else "Stop it with `kill -- -<pid>`. ") +
                            "On local Rootfs workspaces the job may end when the container exits; SSH workspaces keep it running."
                    )
                })
                if (background != null) {
                    put("run_in_background", buildJsonObject {
                        put("type", "boolean")
                        put(
                            "description",
                            "Start the command in the background and return a task id immediately instead of waiting for it. " +
                                "You will be notified with its exit code and the last lines of its output when it finishes, so do not poll it. " +
                                "Use workspace_shell_output to check on it earlier. Defaults to false."
                        )
                    })
                }
            },
            required = targets.required("command"),
        )
    },
    needsApproval = { args ->
        targets.needsApproval(args) { target, params -> target.requiresShellApproval(params.string("command").orEmpty()) }
    },
    execute = {
        val params = it.jsonObject
        val target = targets.resolveForExecution(params)
        val command = params.string("command") ?: error("command is required")
        // 会话里存的是相对路径（旧数据可能是绝对路径），归一化后再交给 shell
        val fallbackCwd = WorkspaceCwd.normalize(target.layout.workspaceDir, workspaceCwds[target.id]).orEmpty()
        val cwd = params.string("cwd")?.let { target.layout.relativeToWorkspace(it) } ?: fallbackCwd
        val timeoutMillis = params.string("timeout")?.toLongOrNull()
            ?.coerceIn(1L, SHELL_TIMEOUT_MAX_SECONDS)
            ?.times(1_000L)
            ?: WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS
        val runInBackground = params.string("run_in_background")?.toBooleanStrictOrNull() ?: false
        val detached = params.string("background")?.toBooleanStrictOrNull() ?: false
        if (readOnly && !WorkspaceCommandPolicy.isReadOnly(command)) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put(
                            "error",
                            "This assistant has read-only workspace access. Change the workspace permission " +
                                "in the assistant settings to run non-read-only commands."
                        )
                        put("command", command)
                    }.toString()
                )
            )
        }
        // 本地 Rootfs 的后台任务交给管理器：有任务 id、结束通知和输出/终止工具
        if (runInBackground && background != null && target.supportsBackgroundShell) {
            val task = background.manager.start(background.conversationId, target.id, command, cwd)
            return@Tool listOf(UIMessagePart.Text(task.toJson().toString()))
        }
        if (detached || runInBackground) {
            // 日志目录沿用 $TMPDIR / $HOME/tmp 展开规则：Termux 上 /tmp 属于 shell 用户且被
            // SELinux 挡住，应用硬写会让整条命令在重定向那一步就失败，而且失败是静默的。
            val log = "\${TMPDIR:-\$HOME/tmp}/l3hub-job-${System.currentTimeMillis()}.log"
            val quoted = command.replace("'", "'\\''")
            // 有 setsid 就脱离会话，没有就退回 nohup；整段放子 shell 里后台执行，$! 就是实际进程号
            val wrapped = "mkdir -p \"\$(dirname \"$log\")\" 2>/dev/null || true; " +
                // 先探一下日志路径可不可写: 起没起来要能判出来, 不能等子进程去开重定向
                "if ! : > \"$log\" 2>/dev/null; then echo no-log; exit 1; fi; " +
                "if command -v setsid >/dev/null 2>&1; then " +
                "exec setsid nohup sh -c '$quoted' > \"$log\" 2>&1; " +
                "else exec nohup sh -c '$quoted' > \"$log\" 2>&1; fi & " +
                // 第一行是 pid，第二行是 shell 展开后的真实日志路径（模板里的 $TMPDIR 只有
                // shell 才知道），模型拿到的是可以直接 tail 的绝对路径
                "echo \$!; printf '%s\\n' \"$log\""
            val started = workspaceRepository.executeCommand(target.id, wrapped, cwd, 15_000)
            val lines = started.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            val pid = lines.firstOrNull().orEmpty()
            val reportedLog = lines.getOrNull(1).orEmpty()
            val logReady = reportedLog.startsWith("/")
            val pidReady = pid.isNotEmpty() && pid.all { it.isDigit() }
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("background", true)
                        put("log", if (logReady) reportedLog else log)
                        put("workspace", target.name)
                        if (pidReady && logReady) {
                            put("pid", pid)
                            put(
                                "hint", "Read output with workspace_shell + `tail -n 100 $reportedLog`." +
                                    if (readOnly) "" else " Stop with `kill -- -$pid`."
                            )
                        } else {
                            // 起不来时不能只回一个看起来正常的 pid，把 shell 的说法原样带回去
                            put("error", "Failed to start the background job: the log file was not created")
                            if (started.exitCode != 0) put("exitCode", started.exitCode)
                            if (started.stderr.isNotBlank()) put("stderr", started.stderr)
                        }
                    }.toString()
                )
            )
        } else {
            val result = workspaceRepository.executeCommand(
                target.id,
                command,
                cwd,
                timeoutMillis,
            )
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
        }
    },
)

internal fun createShellOutputTool(background: BackgroundShell) = Tool(
    name = "workspace_shell_output",
    description = """
        Read the output of a background command started by workspace_shell with run_in_background.
        Returns the combined stdout and stderr produced since the previous read, the command status,
        and the exit code once it has finished. You are notified automatically when a background command
        finishes, so only use this when you need its output before then, such as checking that a server started.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("task_id", buildJsonObject {
                    put("type", "string")
                    put("description", "Task id returned by workspace_shell")
                })
                put("wait", buildJsonObject {
                    put("type", "integer")
                    put(
                        "description",
                        "Seconds to wait for the command to finish before returning. Defaults to 0 (return immediately), max $SHELL_TIMEOUT_MAX_SECONDS."
                    )
                })
            },
            required = listOf("task_id"),
        )
    },
    needsApproval = background::needsApproval,
    execute = {
        val params = it.jsonObject
        val taskId = params.string("task_id") ?: error("task_id is required")
        val waitMillis = (params.string("wait")?.toLongOrNull() ?: 0L)
            .coerceIn(0L, SHELL_TIMEOUT_MAX_SECONDS) * 1_000L
        background.requireTaskAccess(taskId)
        val result = background.manager.readOutput(background.conversationId, taskId, waitMillis)
        listOf(
            UIMessagePart.Text(
                result.task.toJson {
                    put("output", result.output)
                    if (result.skippedBytes > 0) {
                        put("skippedBytes", result.skippedBytes)
                        put("note", "Earlier output was omitted. The full log is at ${result.task.logPath}.")
                    }
                }.toString()
            )
        )
    },
)
internal fun createShellKillTool(background: BackgroundShell) = Tool(
    name = "workspace_shell_kill",
    description = "Stop a background command started by workspace_shell with run_in_background, together with every process it started.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("task_id", buildJsonObject {
                    put("type", "string")
                    put("description", "Task id returned by workspace_shell")
                })
            },
            required = listOf("task_id"),
        )
    },
    needsApproval = background::needsApproval,
    execute = {
        val taskId = it.jsonObject.string("task_id") ?: error("task_id is required")
        background.requireTaskAccess(taskId)
        val task = background.manager.kill(background.conversationId, taskId)
        listOf(UIMessagePart.Text(task.toJson().toString()))
    },
)

internal fun BackgroundShellTask.toJson(extra: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
    put("taskId", id)
    put("status", status.name.lowercase())
    exitCode?.let { put("exitCode", it) }
    put("logPath", logPath)
    extra()
}

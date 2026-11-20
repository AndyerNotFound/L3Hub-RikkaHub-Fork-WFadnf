package me.rerere.rikkahub.data.ai.tools

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


/**
 * `workspace_shell` 的审批。
 *
 * 审批档位沿用旧规则 (破坏性命令必确认、只读命令免确认);
 * 只读档位不打扰用户, 非只读命令在执行时直接拒绝并告诉模型原因;
 * [ExecutionPermission.FULL] 连破坏性命令也不再拦, 这是用户在设置页显式选择的取舍。
 */
internal fun WorkspaceTarget.requiresShellApproval(command: String): Boolean {
    val risk = WorkspaceCommandPolicy.classify(command)
    return when (executionPermission) {
        ExecutionPermission.FULL -> false
        ExecutionPermission.READ_ONLY -> false
        // 写文件免审批不等于给命令开口子: 只读命令照旧免确认, 其余仍然要问
        ExecutionPermission.READ_WRITE -> risk != WorkspaceCommandPolicy.Risk.READ_ONLY
        ExecutionPermission.APPROVAL -> when {
            // 破坏性命令始终要确认，用户关掉审批也不放行
            risk == WorkspaceCommandPolicy.Risk.DANGEROUS -> true
            !needsApproval("workspace_shell") -> false
            // 只读命令免审批：编码时大量 ls / grep / git diff，逐条确认没法用
            risk == WorkspaceCommandPolicy.Risk.READ_ONLY -> false
            else -> true
        }
    }
}

/** 「已修改文件」清单: 只带路径和是否新建, 没有内容快照 */


internal class BackgroundShell(val manager: BackgroundShellManager, val conversationId: Uuid)

internal fun createShellTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
    workspaceCwds: Map<String, String> = emptyMap(),
    readOnly: Boolean = false,
    backgroundShellManager: BackgroundShellManager? = null,
    conversationId: Uuid? = null,
) = Tool(
    name = "workspace_shell",
    description = buildString {
        append(
            "Run a shell command in the assistant's bound workspace Rootfs. The workspace files area is mounted at ${targets.first().layout.workspaceDir}. "
        )
        append("Use cwd for a path relative to the workspace files root. ")
        if (workspaceCwds.isNotEmpty()) {
            append("Defaults to the directory picked for each workspace. ")
        }
        append("Set background=true for long-running commands (dev servers, watchers): it returns a pid and a log file immediately.")
        if (backgroundShellManager != null) {
            append(
                " On local Rootfs workspaces prefer run_in_background: it returns a task id, you are notified when the command finishes, and workspace_shell_output / workspace_shell_kill read or stop it."
            )
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
                            if (backgroundShellManager != null) append(" Ignored when running in the background.")
                        }
                    )
                })
                put("background", buildJsonObject {
                    put("type", "boolean")
                    put(
                        "description",
                        "Run the command detached and return at once with a pid and a log path. " +
                            "Read the log with `tail -n 100 <log>` and stop it with `kill -- -<pid>`. " +
                            "On local Rootfs workspaces the job may end when the container exits; SSH workspaces keep it running."
                    )
                })
                if (backgroundShellManager != null) {
                    put("run_in_background", buildJsonObject {
                        put("type", "boolean")
                        put(
                            "description",
                            "Start the command in the background and return a task id immediately instead of waiting for it. " +
                                "You will be notified with its exit code and the last lines of its output when it finishes, so do not poll it. " +
                                "Use workspace_shell_output to check on it earlier and workspace_shell_kill to stop it. Defaults to false."
                        )
                    })
                }
            },
            required = listOf("command"),
        )
    },
    needsApproval = { params ->
        val target = params.jsonObject.resolveWorkspaceOrNull(targets)
        val command = params.jsonObject.string("command").orEmpty()
        target == null || target.requiresShellApproval(command)
    },
    execute = {
        val params = it.jsonObject
        val target = params.resolveWorkspace(targets)
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
                            "Only read-only commands are allowed right now. Turn plan mode off or raise the " +
                                "execution permission in the assistant settings to run this."
                        )
                        put("command", command)
                    }.toString()
                )
            )
        }
        // 本地 Rootfs 的后台任务交给管理器：有任务 id、结束通知和输出/终止工具
        if (runInBackground && backgroundShellManager != null && conversationId != null &&
            target.supportsBackgroundShell
        ) {
            val task = backgroundShellManager.start(conversationId, target.id, command, cwd)
            return@Tool listOf(UIMessagePart.Text(task.toJson().toString()))
        }
        if (detached || runInBackground) {
            // 日志目录与 ShellChangeScan 用同一套展开规则：Termux 上 /tmp 属于 shell 用户且被
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
                            put("hint", "Read output with workspace_shell + `tail -n 100 $reportedLog`; stop with `kill -- -$pid`.")
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
            // 只能用 shell 改文件的工作区（SSH）没有文件快照，跑完扫一份本轮改过的路径给聊天页
            // TODO(l3hub): 只对 supportsFileWrite=false 的工作区扫；本地工作区用 shell 改文件（git commit、格式化）
            //  目前不会出「已修改文件」卡片，等快照能覆盖这类改动再打开
            val scan = if (target.supportsFileWrite) null else ShellChangeScan.create(target.layout.workspaceDir)
            val result = workspaceRepository.executeCommand(
                target.id,
                scan?.wrap(command) ?: command,
                cwd,
                timeoutMillis,
            )
            val changed = scan?.collect { collectCommand ->
                runCatching {
                    workspaceRepository.executeCommand(target.id, collectCommand, "", SHELL_SCAN_TIMEOUT_MILLIS)
                }.getOrNull()?.stdout
            }.orEmpty()
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("exitCode", result.exitCode)
                        put("stdout", result.stdout)
                        put("stderr", result.stderr)
                        put("timedOut", result.timedOut)
                        if (result.truncated) put("truncated", true)
                        put("workspace", target.name)
                        putEditedFiles(changed.map { editedFileJson(it.path, it.created) })
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
    execute = {
        val params = it.jsonObject
        val taskId = params.string("task_id") ?: error("task_id is required")
        val waitMillis = (params.string("wait")?.toLongOrNull() ?: 0L)
            .coerceIn(0L, SHELL_TIMEOUT_MAX_SECONDS) * 1_000L
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
    execute = {
        val taskId = it.jsonObject.string("task_id") ?: error("task_id is required")
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

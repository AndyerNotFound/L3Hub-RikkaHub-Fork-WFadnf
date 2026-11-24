package me.rerere.rikkahub.data.ai.transformers

import android.util.Log
import kotlinx.coroutines.CancellationException
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.WORKSPACE_SHELL_TOOL_NAME
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.WorkspaceCwd
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceLayout
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspacePaths
import me.rerere.workspace.WorkspaceShellStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Workspace 系统提示注入转换器
 *
 * 当助手绑定了一个 shell 已就绪的 workspace 时, 在系统提示词中追加一段引导,
 * 让模型了解 workspace 环境与 workspace_* 工具的使用方式。
 * 提示中包含实际使用的 shell 与发行版, 避免模型默认按 bash / apt 来写命令。
 * 本地与远程工作区都会把 ~/.agents、工作区根和会话当前目录中的 AGENTS.md 作为工作区指令。
 * shell 与发行版读到一次后缓存 (见 [RemoteEnvironmentCache]),
 * 连不上时只注入一段说明, 不阻塞对话。
 */
class WorkspaceReminderTransformer(
    private val workspaceRepository: WorkspaceRepository,
) : InputMessageTransformer {
    private val remoteEnvironments = RemoteEnvironmentCache()

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val workspaceIds = ctx.assistant.allWorkspaceIds.map { it.toString() }
        if (workspaceIds.isEmpty()) return messages

        val prompts = buildList {
            workspaceIds.forEach { workspaceId ->
                val workspace = workspaceRepository.getById(workspaceId) ?: return@forEach
                // 没就绪的工作区不注册工具（见 ChatToolFactory）。什么都不说会让模型
                // 只看到「没有这个工具」而不知道原因，这里明确告诉它工作区存在但不可用。
                if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
                    add(workspace to buildNotReadyPrompt(workspace))
                    return@forEach
                }
                val layout = workspaceRepository.layout(workspace) ?: return@forEach
                val tools = ctx.workspaceToolNames[workspaceId].orEmpty()
                if (tools.isEmpty()) {
                    add(workspace to buildNoToolsPrompt(workspace))
                    return@forEach
                }
                // Resolve the stored directory once for the prompt and instruction lookup.
                val cwd = WorkspaceCwd.normalize(layout.workspaceDir, ctx.workspaceCwds[workspaceId])
                // 没设置时提示词里不提工作目录；设置了才拼成执行环境里的绝对路径
                val cwdPath = cwd?.let { WorkspaceCwd.absolute(layout.workspaceDir, it) }

                val prompt: String
                val home: String?
                if (workspace.isSsh()) {
                    val environmentResult = remoteEnvironments.get(workspaceId, workspace.sshConfig) {
                        // shell 探测需要连接, 失败时不再读发行版, 以免再等一次超时
                        RemoteEnvironment(
                            shell = workspaceRepository.shell(workspace),
                            distro = readDistroName(workspace),
                            home = runCatching {
                                workspaceRepository.executeCommand(
                                    id = workspaceId,
                                    command = "printf '%s' \"\$HOME\"",
                                    timeoutMillis = 15_000,
                                ).stdout.trim()
                            }.getOrNull()?.takeIf { it.startsWith("/") },
                        )
                    }
                    if (environmentResult.isFailure) {
                        val error = environmentResult.exceptionOrNull()
                            ?: IllegalStateException("Workspace unreachable: $workspaceId")
                        Log.w("WorkspaceReminder", "Workspace unreachable: $workspaceId", error)
                        add(workspace to buildUnreachablePrompt(workspace, layout, error))
                        return@forEach
                    }
                    val environment = environmentResult.getOrThrow()
                    home = environment.home
                    prompt = buildSshWorkspacePrompt(
                        workspace = workspace,
                        layout = layout,
                        shell = environment.shell,
                        distro = environment.distro,
                        cwd = cwdPath,
                        tools = tools,
                    )
                } else {
                    home = WorkspaceManager.ROOTFS_HOME_DIR
                    prompt = buildWorkspacePrompt(
                        workspace = workspace,
                        shell = if (WORKSPACE_SHELL_TOOL_NAME in tools) workspaceRepository.shell(workspace) else null,
                        distro = readDistroName(workspace),
                        cwd = cwdPath,
                        tools = tools,
                    )
                }
                val instructions = buildAgentsPrompt(workspace, workspaceAgentsPaths(layout.workspaceDir, cwdPath, home))
                add(workspace to (prompt + instructions))
            }
        }
        if (prompts.isEmpty()) return messages

        val prompt = buildString {
            if (workspaceIds.size > 1) {
                appendLine("<workspaces>")
                appendLine(
                    "The assistant is bound to multiple workspaces. Check each tool's description for the workspace IDs it accepts."
                )
                prompts.forEach { (workspace, _) ->
                    appendLine("- ${workspace.id} — ${workspace.name}")
                }
                appendLine(
                    "For tools with a `workspace` parameter, the default is \"${workspaceIds.first()}\". " +
                        "If the tool is unavailable there, explicitly specify an allowed workspace."
                )
                appendLine("</workspaces>")
                appendLine()
            }
            prompts.forEachIndexed { index, (_, block) ->
                if (index > 0) appendLine()
                append(block)
            }
        }
        return messages.withPrompt(prompt)
    }

    // 追加到第一条 system 消息; 若不存在则插入一条
    private fun List<UIMessage>.withPrompt(prompt: String): List<UIMessage> {
        val systemIndex = indexOfFirst { it.role == MessageRole.SYSTEM }
        return if (systemIndex >= 0) {
            toMutableList().apply {
                this[systemIndex] = this[systemIndex]
                    .appendText("\n\n$prompt")
                    .copy(isSynthetic = true)
            }
        } else {
            listOf(UIMessage.system(prompt).copy(isSynthetic = true)) + this
        }
    }

    private suspend fun buildAgentsPrompt(workspace: WorkspaceEntity, paths: List<String>): String {
        val instructions = paths.mapNotNull { path ->
            readRootfsText(workspace, path, MAX_AGENTS_BYTES)?.let { path to it }
        }
        if (instructions.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine()
            appendLine("<workspace_instructions>")
            appendLine("Follow the AGENTS.md instructions below.")
            instructions.forEach { (path, content) ->
                appendLine()
                appendLine("AGENTS.md source: $path")
                appendLine(content)
            }
            append("</workspace_instructions>")
        }
    }

    private suspend fun readDistroName(workspace: WorkspaceEntity): String? =
        OS_RELEASE_PATHS.firstNotNullOfOrNull { path ->
            readRootfsText(workspace, path, MAX_OS_RELEASE_BYTES)?.let(::parseOsReleaseName)
        }

    // Missing, oversized, or unreadable files should not prevent prompt generation.
    private suspend fun readRootfsText(workspace: WorkspaceEntity, path: String, maxBytes: Int): String? =
        try {
            workspaceRepository.readAbsoluteFile(workspace, path, maxBytes)
                .toString(Charsets.UTF_8)
                .takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("WorkspaceReminder", "Skipping workspace file: $path", e)
            null
        }

    private companion object {
        const val MAX_AGENTS_BYTES = 64 * 1024
        const val MAX_OS_RELEASE_BYTES = 16 * 1024
        val OS_RELEASE_PATHS = listOf("/etc/os-release", "/usr/lib/os-release")
    }
}

/** List instruction files from user scope to the current directory. */
internal fun workspaceAgentsPaths(workspaceDir: String, absoluteCwd: String?, home: String?): List<String> =
    listOfNotNull(
        home?.let { WorkspacePaths.join(it, ".agents/AGENTS.md") },
        WorkspacePaths.join(workspaceDir, "AGENTS.md"),
        absoluteCwd?.let { WorkspacePaths.join(it, "AGENTS.md") },
    ).distinct()

/** 远程工作区提示词里描述的执行环境 */
internal data class RemoteEnvironment(val shell: String, val distro: String?, val home: String? = null)

/**
 * 按工作区缓存远程执行环境。
 *
 * 读一次要好几次网络往返, 而每一步请求都要构建提示词; 远程环境很少变化, 读到后就一直沿用, 直到连接配置改变。
 * 连不上的结果只保留 [failureTtlMillis]: 期间的后续步骤不必每步都等连接超时, 过期后再重新尝试。
 */
internal class RemoteEnvironmentCache(
    private val failureTtlMillis: Long = DEFAULT_FAILURE_TTL_MS,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private class Entry(val config: String?, val result: Result<RemoteEnvironment>, val loadedAt: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    suspend fun get(
        workspaceId: String,
        config: String?,
        load: suspend () -> RemoteEnvironment,
    ): Result<RemoteEnvironment> {
        entries[workspaceId]
            ?.takeIf { it.config == config && (it.result.isSuccess || now() - it.loadedAt < failureTtlMillis) }
            ?.let { return it.result }
        val result = try {
            Result.success(load())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        entries[workspaceId] = Entry(config, result, now())
        return result
    }

    private companion object {
        const val DEFAULT_FAILURE_TTL_MS = 60_000L
    }
}

private const val MAX_DISTRO_NAME_LENGTH = 80

/** 从 os-release 内容中取发行版名称, 优先 PRETTY_NAME, 其次 NAME + VERSION_ID */
internal fun parseOsReleaseName(content: String): String? {
    val values = content.lineSequence()
        .map { it.trim() }
        .filter { !it.startsWith("#") && it.indexOf('=') > 0 }
        .associate { line ->
            line.substringBefore('=') to line.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
        }
    val name = values["PRETTY_NAME"]?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(values["NAME"], values["VERSION_ID"]).joinToString(" ")
    // 内容来自 Rootfs 内的文件, 拼进系统提示前去掉控制字符并限制长度
    return name.filterNot { it.isISOControl() }.trim().take(MAX_DISTRO_NAME_LENGTH).ifBlank { null }
}

private fun buildWorkspacePrompt(
    workspace: WorkspaceEntity,
    shell: String?,
    distro: String?,
    cwd: String?,
    tools: Set<String>,
): String = buildString {
    appendLine("<workspace>")
    appendLine(
        "You have access to a persistent Linux workspace named \"${workspace.name}\", running in a sandboxed proot rootfs environment."
    )
    appendLine(
        "- The workspace files area is mounted at `/workspace`. Use it as your working directory; files written there persist across turns of this conversation."
    )
    appendLine(
        "- All paths passed to workspace tools must be absolute and inside the Rootfs (for example `/workspace/notes.md`)."
    )
    if (distro != null) {
        appendLine("- The Rootfs distribution is $distro.")
    }
    if (shell != null) appendShellLine(shell)
    appendAvailableWorkspaceTools(tools)
    if ("workspace_shell_output" in tools) {
        appendLine(
            "- For long-running commands, use `run_in_background` rather than appending `&`. Wait for the completion notice instead of polling."
        )
    }
    appendLine(
        "- The skills directory is mounted at `/skills`. Each skill is a subdirectory `/skills/<skill-name>/` containing a `SKILL.md` (with `name` and `description` frontmatter) plus any supporting files. Read a skill's `SKILL.md` before using it, and follow its instructions."
    )
    appendLine(
        "- Built-in skills shipped with the app are mounted at `/builtin_skills/<skill-name>/` with the same layout. Treat `/builtin_skills` as READ-ONLY: you may read files and run scripts there, but never modify, overwrite, or delete anything. A skill in `/skills` with the same name overrides the built-in one."
    )
    appendLine(
        "- Files the user uploaded are mounted at `/upload`. Treat `/upload` as READ-ONLY: read uploaded files from `/upload/<file-name>`, but never modify, overwrite, or delete anything there. If you need to change an uploaded file, copy it into `/workspace` first and edit the copy."
    )
    val storageMounts = workspace.storageMountList()
    if (storageMounts.isNotEmpty()) {
        appendLine(
            "- The user mounted these directories from the phone's shared storage at the same paths, so files there are the user's real files (also visible to other apps). Be careful when modifying or deleting anything there:"
        )
        storageMounts.forEach { mount ->
            appendLine("  - `${mount.path}`")
        }
        // 实测: 共享存储 (FUSE) 拒绝建软链接; PRoot 的 link2symlink 模拟硬链接时会先把原文件改名成 .l2s.*,
        // 建软链接失败后不回滚, 原文件名就消失了
        appendLine(
            "  These directories are on Android shared storage, which supports neither symbolic nor hard links. Never run `ln` there: a failed hard link renames the original file to a hidden `.l2s.*` file. Tools that create links (such as git, which hard-links object files, or package managers) can break there, so run them in `/workspace` and copy the results back."
        )
    }
    if (!cwd.isNullOrBlank()) {
        appendLine(
            "- Current working directory: `$cwd`. Use this as the default context for file operations and shell commands."
        )
    }
    append("</workspace>")
}

private fun buildSshWorkspacePrompt(
    workspace: WorkspaceEntity,
    layout: WorkspaceLayout,
    shell: String,
    distro: String?,
    cwd: String?,
    tools: Set<String>,
): String = buildString {
    val address = workspace.sshSettings()?.let { "${it.username}@${it.host}" } ?: "the remote host"
    appendLine("<workspace>")
    appendLine(
        "You have access to a persistent remote workspace named \"${workspace.name}\" on a real machine, reached over SSH as `$address`."
    )
    appendLine(
        "- The workspace directory is `${layout.workspaceDir}`. Use it as your working directory; files written there persist across turns of this conversation."
    )
    appendLine(
        "- All paths passed to workspace tools must be absolute paths on the remote machine (for example `${layout.workspaceDir}/notes.md`)."
    )
    appendLine(
        "- This is not a sandbox: commands run with the SSH user's real permissions and can affect other data and services on the machine. Avoid destructive or system-wide changes unless the user asks for them."
    )
    if (distro != null) {
        appendLine("- The remote operating system is $distro.")
    }
    if (WORKSPACE_SHELL_TOOL_NAME in tools) appendShellLine(shell)
    appendAvailableWorkspaceTools(tools)
    if (!cwd.isNullOrBlank()) {
        appendLine(
            "- Current working directory: `$cwd`. Use this as the default context for file operations and shell commands."
        )
    }
    append("</workspace>")
}

private fun StringBuilder.appendAvailableWorkspaceTools(tools: Set<String>) {
    appendLine("- Available tools for this workspace: ${tools.joinToString { "`$it`" }}.")
    if ("workspace_read_file" in tools) {
        appendLine("- Use `offset` and `limit` to read files in pages; check `totalLines` to see what's left.")
    }
    if ("workspace_edit_file" in tools) {
        appendLine("- Prefer `workspace_edit_file` for targeted edits over rewriting whole files.")
    }
}

private fun buildNoToolsPrompt(workspace: WorkspaceEntity): String = buildString {
    appendLine("<workspace>")
    appendLine("No workspace tools are available for \"${workspace.name}\" (${workspace.id}) in this request.")
    appendLine("Do not claim to have inspected or changed anything there, or silently use a different workspace instead.")
    append("</workspace>")
}

private fun buildNotReadyPrompt(workspace: WorkspaceEntity): String =
    buildString {
        appendLine("<workspace>")
        appendLine(
            "You are bound to a workspace named \"${workspace.name}\" (${workspace.workspaceDir()}), " +
                "but it is not ready yet: status=${workspace.shellStatus}."
        )
        appendLine(
            "- None of the workspace_* tools are registered for it. Do not claim to have read, searched or changed anything there."
        )
        appendLine(
            "- If the user expects files in this workspace, tell them the Rootfs installation (or the connection) still has to finish."
        )
        append("</workspace>")
    }

private fun buildUnreachablePrompt(workspace: WorkspaceEntity, layout: WorkspaceLayout, error: Throwable): String =
    buildString {
        val address = workspace.sshSettings()?.let { "${it.username}@${it.host}" }
        appendLine("<workspace>")
        appendLine(
            "You are bound to a workspace named \"${workspace.name}\"" +
                (address?.let { " on `$it`" } ?: "") +
                " with directory `${layout.workspaceDir}`, but it is currently unreachable: ${error.message}"
        )
        appendLine(
            "- Workspace tools will fail until the connection is restored. If the user asks you to work in the workspace, tell them it is unreachable."
        )
        append("</workspace>")
    }

private fun StringBuilder.appendShellLine(shell: String) {
    if (shell.substringAfterLast('/') == "bash") {
        appendLine("- `workspace_shell` runs commands with `$shell`.")
    } else {
        appendLine(
            "- `workspace_shell` runs commands with `$shell`, a POSIX shell. Bash is not installed, so avoid bash-only syntax such as arrays, here-strings (`<<<`) and brace expansion."
        )
    }
}

private fun UIMessage.appendText(extra: String): UIMessage {
    val updatedParts = parts.toMutableList()
    val firstTextIndex = updatedParts.indexOfFirst { it is UIMessagePart.Text }
    if (firstTextIndex >= 0) {
        val text = updatedParts[firstTextIndex] as UIMessagePart.Text
        updatedParts[firstTextIndex] = text.copy(text = text.text + extra)
    } else {
        updatedParts.add(UIMessagePart.Text(extra))
    }
    return copy(parts = updatedParts)
}

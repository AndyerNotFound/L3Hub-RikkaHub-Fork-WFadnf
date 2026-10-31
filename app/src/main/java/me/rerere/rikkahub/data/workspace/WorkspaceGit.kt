package me.rerere.rikkahub.data.workspace

import me.rerere.rikkahub.data.repository.WorkspaceRepository

data class GitCommandOutcome(
    val ok: Boolean,
    val exitCode: Int,
    val output: String,
)

data class GitFileChange(
    val path: String,
    val indexStatus: Char,
    val workTreeStatus: Char,
) {
    val untracked: Boolean get() = indexStatus == '?' && workTreeStatus == '?'
    val statusLabel: String get() = when {
        untracked -> "??"
        workTreeStatus != ' ' -> "$workTreeStatus".trim().ifEmpty { "M" }
        else -> "$indexStatus".trim().ifEmpty { "M" }
    }
}

data class GitStatus(
    val branch: String,
    val ahead: Int,
    val behind: Int,
    val files: List<GitFileChange>,
) {
    val clean: Boolean get() = files.isEmpty()
}

data class GitFileStat(
    val path: String,
    val added: Int,
    val removed: Int,
    val untracked: Boolean = false,
)

/**
 * 聊天页「本轮改动」卡片与 workspace_git 工具共用的 git 封装。
 * 全部走工作区 shell, 本地 Rootfs 和 SSH 工作区行为一致。
 */
class WorkspaceGit(
    private val repository: WorkspaceRepository,
) {
    suspend fun run(
        workspaceId: String,
        cwd: String,
        command: String,
        timeoutMillis: Long = 60_000,
    ): GitCommandOutcome {
        val result = repository.executeCommand(workspaceId, command, cwd, timeoutMillis)
        val output = buildString {
            append(result.stdout.trimEnd())
            if (result.stderr.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(result.stderr.trimEnd())
            }
        }.trim()
        return GitCommandOutcome(
            ok = result.exitCode == 0 && !result.timedOut,
            exitCode = result.exitCode,
            output = output,
        )
    }

    suspend fun status(workspaceId: String, cwd: String = ""): GitStatus {
        // -z：路径不做引号包裹和八进制转义，中文/空格文件名才解析得对
        val out = run(workspaceId, cwd, "git status --porcelain=v1 -b -z").output
        var branch = ""
        var ahead = 0
        var behind = 0
        val files = mutableListOf<GitFileChange>()
        val tokens = out.split('\u0000')
        var index = 0
        val header = tokens.firstOrNull()
        if (header != null && header.startsWith("## ")) {
            val text = header.removePrefix("## ")
            branch = text.substringBefore("...").substringBefore(' ').trim()
            Regex("ahead (\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { ahead = it }
            Regex("behind (\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { behind = it }
            index = 1
        }
        while (index < tokens.size) {
            val entry = tokens[index]
            index++
            if (entry.length < 3) continue
            val indexStatus = entry[0]
            val workTree = entry[1]
            // rename / copy 的下一条记录是原路径，跳过
            if (indexStatus == 'R' || indexStatus == 'C') index++
            var path = entry.substring(3).trim()
            if (path.contains(" -> ")) path = path.substringAfter(" -> ")
            if (path.isNotEmpty()) files += GitFileChange(path, indexStatus, workTree)
        }
        return GitStatus(branch = branch, ahead = ahead, behind = behind, files = files)
    }

    suspend fun diff(
        workspaceId: String,
        cwd: String = "",
        paths: List<String> = emptyList(),
        maxChars: Int = 200_000,
    ): String {
        val suffix = paths.takeIf { it.isNotEmpty() }
            ?.joinToString(" ") { it.shQuote() }
            ?.let { " -- $it" }
            .orEmpty()
        val out = run(workspaceId, cwd, "git --no-pager diff HEAD --no-color -U3$suffix", 120_000).output
        return if (out.length > maxChars) out.take(maxChars) + "\n… (truncated)" else out
    }

    suspend fun numstat(
        workspaceId: String,
        cwd: String = "",
        paths: List<String> = emptyList(),
        status: GitStatus? = null,
    ): List<GitFileStat> {
        val rows = mutableListOf<GitFileStat>()
        val filter = paths.toSet()
        val current = status ?: status(workspaceId, cwd)
        current.files.filter { filter.isEmpty() || it.path in filter }.forEach { change ->
            if (change.untracked) {
                rows += GitFileStat(change.path, 0, 0, untracked = true)
            }
        }
        val suffix = paths.takeIf { it.isNotEmpty() }
            ?.joinToString(" ") { it.shQuote() }
            ?.let { " -- $it" }
            .orEmpty()
        val tokens = run(workspaceId, cwd, "git --no-pager diff --numstat -z HEAD --no-color$suffix")
            .output
            .split('\u0000')
        var index = 0
        while (index < tokens.size) {
            val record = tokens[index]
            index++
            if (record.isBlank()) continue
            val parts = record.split('\t')
            if (parts.size < 3) continue
            val added = parts[0].toIntOrNull() ?: 0
            val removed = parts[1].toIntOrNull() ?: 0
            var path = parts[2].trim()
            if (path.isEmpty()) {
                // rename：后面两段分别是旧路径和新路径
                val oldPath = tokens.getOrNull(index).orEmpty()
                index++
                val newPath = tokens.getOrNull(index).orEmpty()
                index++
                path = newPath.ifEmpty { oldPath }
            }
            if (path.isEmpty()) continue
            rows.removeAll { it.path == path }
            rows += GitFileStat(path, added, removed)
        }
        return rows
    }

    /** 丢弃改动：已跟踪的 checkout 回 HEAD，未跟踪的直接删掉 */
    suspend fun discard(
        workspaceId: String,
        cwd: String = "",
        paths: List<String>,
    ): GitCommandOutcome {
        if (paths.isEmpty()) return GitCommandOutcome(true, 0, "")
        val current = status(workspaceId, cwd)
        val tracked = paths.filterNot { path -> current.files.any { it.path == path && it.untracked } }
        val untracked = paths - tracked.toSet()
        val commands = buildList {
            if (tracked.isNotEmpty()) {
                add("git checkout -- ${tracked.joinToString(" ") { it.shQuote() }}")
            }
            if (untracked.isNotEmpty()) {
                add("git clean -f -- ${untracked.joinToString(" ") { it.shQuote() }}")
            }
        }
        return run(workspaceId, cwd, commands.joinToString(" && "), 120_000)
    }

    /** 只回退一个 hunk：把 diff 里的这一个 hunk 原样反向应用 */
    suspend fun discardHunk(
        workspaceId: String,
        cwd: String = "",
        path: String,
        hunkIndex: Int,
    ): GitCommandOutcome {
        val diffText = diff(workspaceId, cwd, listOf(path), maxChars = 2_000_000)
        val file = UnifiedDiff.parse(diffText).firstOrNull { (it.newPath ?: it.oldPath) == path }
            ?: return GitCommandOutcome(false, 1, "No diff found for $path")
        val hunk = file.hunks.getOrNull(hunkIndex)
            ?: return GitCommandOutcome(false, 1, "Hunk $hunkIndex not found (0..${file.hunks.lastIndex})")
        val patch = buildString {
            appendLine("--- a/$path")
            appendLine("+++ b/$path")
            appendLine("@@ -${hunk.oldStart},${hunk.oldCount} +${hunk.newStart},${hunk.newCount} @@")
            hunk.lines.forEach { appendLine(it) }
        }
        val result = repository.executeCommand(
            workspaceId,
            "git apply -R --unidiff-zero --whitespace=nowarn -",
            cwd,
            60_000,
            patch.toByteArray(),
        )
        val output = buildString {
            append(result.stdout.trimEnd())
            if (result.stderr.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(result.stderr.trimEnd())
            }
        }.trim()
        return GitCommandOutcome(ok = result.exitCode == 0 && !result.timedOut, exitCode = result.exitCode, output = output)
    }

    suspend fun commit(
        workspaceId: String,
        cwd: String = "",
        message: String,
        addAll: Boolean = true,
    ): GitCommandOutcome {
        val prefix = if (addAll) "git add -A && " else ""
        val commit = run(
            workspaceId,
            cwd,
            "${prefix}git commit -m ${message.shQuote()}",
            120_000,
        )
        if (!commit.ok) return commit
        val hash = run(workspaceId, cwd, "git rev-parse --short HEAD").output.trim()
        return commit.copy(output = if (hash.isEmpty()) commit.output else "$hash ${commit.output}")
    }

    suspend fun branches(workspaceId: String, cwd: String = ""): List<String> =
        run(workspaceId, cwd, "git branch --format='%(refname:short)'").output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

    suspend fun checkout(
        workspaceId: String,
        cwd: String = "",
        branch: String,
        create: Boolean = false,
    ): GitCommandOutcome = run(
        workspaceId,
        cwd,
        if (create) "git checkout -b ${branch.shQuote()}" else "git checkout ${branch.shQuote()}",
        120_000,
    )

    suspend fun worktrees(workspaceId: String, cwd: String = ""): List<String> =
        run(workspaceId, cwd, "git worktree list").output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

    suspend fun addWorktree(
        workspaceId: String,
        cwd: String = "",
        path: String,
        branch: String,
    ): GitCommandOutcome = run(
        workspaceId,
        cwd,
        "git worktree add ${path.shQuote()} -b ${branch.shQuote()}",
        180_000,
    )

    suspend fun removeWorktree(
        workspaceId: String,
        cwd: String = "",
        path: String,
    ): GitCommandOutcome = run(workspaceId, cwd, "git worktree remove ${path.shQuote()}", 120_000)
}

/** 命令里拼路径用，避免空格和引号把参数拆开 */
internal fun String.shQuote(): String = "'" + replace("'", "'\\''") + "'"

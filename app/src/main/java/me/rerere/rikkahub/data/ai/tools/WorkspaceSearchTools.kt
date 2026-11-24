package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.WorkspaceCwd
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.workspace.GitIgnoreMatcher
import me.rerere.rikkahub.data.workspace.GitIgnoreRule
import me.rerere.rikkahub.data.workspace.RepoMap
import me.rerere.rikkahub.data.workspace.RepoMapFile
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceLayout
import me.rerere.workspace.WorkspacePaths
import me.rerere.workspace.WorkspaceStorageArea
import java.nio.file.FileSystems
import java.nio.file.Paths

private const val MAX_LIST_ENTRIES = 500
private const val MAX_GLOB_RESULTS = 500
private const val MAX_GREP_RESULTS = 200
private const val MAX_SEARCH_ENTRIES = 4000
private const val MAX_SEARCH_DEPTH = 12
private const val MAX_GREP_FILE_BYTES = 512 * 1024
private const val MAX_GREP_SCANNED_FILES = 800
private const val MAX_GREP_LINE_CHARS = 400

// 依赖目录和构建产物没有检索价值，遍历时整棵跳过
private val SEARCH_SKIPPED_DIRS = setOf(
    ".git", ".gradle", ".idea", "node_modules", "build", "dist", "out", "target",
    "__pycache__", ".venv", "venv", ".dart_tool", "Pods", ".next", "coverage",
)

private fun WorkspaceTarget.cwdRoot(cwds: Map<String, String>): String =
    WorkspaceCwd.absolute(
        workspaceDir = layout.workspaceDir,
        relative = WorkspaceCwd.normalize(layout.workspaceDir, cwds[id]),
    )


private const val DEFAULT_PATH_HINT = "the session's current working directory for that workspace"



// ---------------------------------------------------------------------------
// 检索类工具：列目录 / glob / grep
//
// 都走 backend 的 listFiles + exportAbsoluteFile，本地与 SSH 工作区共用同一套实现；
// 遍历有层数、条目数、单文件大小上限，避免在大仓库上把手机拖死。
// ---------------------------------------------------------------------------

private data class SearchMatch(val path: String, val line: Int, val text: String)

/** 环境内绝对路径落在文件区还是 Rootfs 区；返回值可直接交给 backend.listFiles */
internal fun resolveStorageArea(layout: WorkspaceLayout, path: String): Pair<WorkspaceStorageArea, String> {
    val normalized = WorkspacePaths.normalize(path)
        ?: return WorkspaceStorageArea.LINUX to path.trim().trimStart('/')
    val relative = WorkspacePaths.relativeTo(layout.workspaceDir, normalized)
        ?: return WorkspaceStorageArea.LINUX to normalized.trimStart('/')
    return WorkspaceStorageArea.FILES to relative
}

private fun joinAbsolute(directory: String, name: String): String =
    WorkspacePaths.join(directory, name) ?: error("invalid path: $directory/$name")

/** 预编译 glob，避免逐个文件重新解析；pattern 非法时 isValid 为 false */
internal class GlobMatcher(pattern: String) {
    private val matcher = runCatching {
        FileSystems.getDefault().getPathMatcher("glob:${pattern.trim()}")
    }.getOrNull()
    private val matchFileNameOnly = !pattern.contains('/')

    val isValid: Boolean = matcher != null

    fun matches(relativePath: String, fileName: String): Boolean {
        val compiled = matcher ?: return false
        val path = runCatching { Paths.get(relativePath.trimStart('/')) }.getOrNull() ?: return false
        return compiled.matches(path) || (matchFileNameOnly && compiled.matches(path.fileName))
    }
}

/**
 * 按目录逐层展开，跳过依赖和构建目录；条目数和层数都有上限。
 * 每进一个目录读一次它自己的 .gitignore，规则向下继承，命中忽略的条目直接跳过。
 */
private suspend fun walkWorkspace(
    repository: WorkspaceRepository,
    target: WorkspaceTarget,
    root: String,
    maxEntries: Int,
    onEntry: suspend (absolutePath: String, entry: WorkspaceFileEntry) -> Boolean,
) {
    val start = root.trim().trimEnd('/').ifBlank { "/" }
    val queue = ArrayDeque<Triple<String, Int, List<GitIgnoreRule>>>()
    queue.add(Triple(start, 0, emptyList()))
    var visited = 0
    while (queue.isNotEmpty() && visited < maxEntries) {
        val (directory, depth, inheritedRules) = queue.removeFirst()
        val (area, relative) = resolveStorageArea(target.layout, directory)
        val entries = runCatching { repository.listFiles(target.id, area, relative) }
            .getOrElse { emptyList() }
            .sortedWith(compareByDescending<WorkspaceFileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })

        val gitignore = entries.firstOrNull { !it.isDirectory && it.name == ".gitignore" }
        val rules = if (gitignore != null) {
            val text = repository.readTextForSearch(
                workspaceId = target.id,
                path = joinAbsolute(directory, gitignore.name),
                sizeBytes = gitignore.sizeBytes,
            )
            if (text != null) {
                inheritedRules + GitIgnoreMatcher.parse(directory, text)
            } else {
                inheritedRules
            }
        } else {
            inheritedRules
        }

        for (entry in entries) {
            visited++
            val absolute = joinAbsolute(directory, entry.name)
            // 规则的基准目录就是它所在的目录，直接用绝对路径判断即可
            if (GitIgnoreMatcher.isIgnored(rules, absolute, entry.isDirectory)) {
                if (visited >= maxEntries) return
                continue
            }
            if (!onEntry(absolute, entry)) return
            if (entry.isDirectory && depth < MAX_SEARCH_DEPTH && entry.name !in SEARCH_SKIPPED_DIRS) {
                queue.add(Triple(absolute, depth + 1, rules))
            }
            if (visited >= maxEntries) return
        }
    }
}

/** 只读小文本文件；超过大小上限、读失败或像二进制时返回 null */
private suspend fun WorkspaceRepository.readTextForSearch(
    workspaceId: String,
    path: String,
    sizeBytes: Long,
): String? {
    if (sizeBytes > MAX_GREP_FILE_BYTES) return null
    val bytes = try {
        readAbsoluteFile(workspaceId, path, MAX_GREP_FILE_BYTES)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        return null
    }
    if ((0 until minOf(bytes.size, 4096)).any { bytes[it] == 0.toByte() }) return null
    return bytes.toString(Charsets.UTF_8)
}

/** 仓库地图：文件树 + 顶层符号，省掉逐个 read 的往返 */
internal fun createRepoMapTool(
    targets: WorkspaceToolTargets,
    workspaceRepository: WorkspaceRepository,
    cwds: Map<String, String> = emptyMap(),
) = Tool(
    name = "workspace_repo_map",
    description = """
        Build a compact map of the workspace: file tree grouped by directory with top-level symbols per file
        (Kotlin/Java/Python/TS/JS/Go/Rust/Swift/Ruby/C#/C++). Respects .gitignore.
        Use it before reading files in a large repository; pass path to map a subdirectory and max_files to bound the scan.
        Defaults to the session's current working directory.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                putPathProperty(targets.layout, required = false, defaultHint = DEFAULT_PATH_HINT)
                put("max_files", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum files to scan. Defaults to 300, max 800.")
                })
            },
            required = targets.required(),
        )
    },
    needsApproval = { targets.needsApproval(it) },
    isReadOnly = true,
    execute = {
        val params = it.jsonObject
        val target = targets.resolveForExecution(params)
        val root = params.absolutePathOrNull("path") ?: target.cwdRoot(cwds)
        val maxFiles = params.string("max_files")?.toIntOrNull()?.coerceIn(1, MAX_REPO_MAP_FILES) ?: DEFAULT_REPO_MAP_FILES

        val files = mutableListOf<RepoMapFile>()
        walkWorkspace(
            repository = workspaceRepository,
            target = target,
            root = root,
            maxEntries = MAX_SEARCH_ENTRIES,
        ) { absolute, entry ->
            if (!entry.isDirectory && files.size < maxFiles && absolute.isRepoMapSource()) {
                val text = workspaceRepository.readTextForSearch(target.id, absolute, entry.sizeBytes)
                if (text != null) {
                    val relative = WorkspacePaths.relativeTo(target.layout.workspaceDir, absolute) ?: absolute
                    files += RepoMapFile(
                        path = relative,
                        lines = text.count { it == '\n' } + 1,
                        symbols = RepoMap.extractSymbols(relative, text),
                    )
                }
            }
            true
        }

        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("path", root)
                    put("fileCount", files.size)
                    put("truncated", files.size >= maxFiles)
                    put("map", RepoMap.render(files))
                }.toString()
            )
        )
    },
)

private const val DEFAULT_REPO_MAP_FILES = 300
private const val MAX_REPO_MAP_FILES = 800

private fun String.isRepoMapSource(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf(
        "kt", "kts", "java", "py", "ts", "tsx", "js", "jsx", "go", "rs", "swift", "rb", "cs", "cpp", "h",
    )

internal fun createListTool(
    targets: WorkspaceToolTargets,
    workspaceRepository: WorkspaceRepository,
    cwds: Map<String, String> = emptyMap(),
) = Tool(
    name = "workspace_list",
    description = """
        List a directory in the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Returns name, absolute path, type, size and mtime, directories first. Defaults to the session's current working directory.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                putPathProperty(targets.layout, required = false, defaultHint = DEFAULT_PATH_HINT)
            },
            required = targets.required(),
        )
    },
    needsApproval = { targets.needsApproval(it) },
    isReadOnly = true,
    execute = {
        val params = it.jsonObject
        val target = targets.resolveForExecution(params)
        val path = params.absolutePathOrNull("path") ?: target.cwdRoot(cwds)
        val (area, relative) = resolveStorageArea(target.layout, path)
        val all = workspaceRepository.listFiles(target.id, area, relative)
            .sortedWith(compareByDescending<WorkspaceFileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        val entries = all.take(MAX_LIST_ENTRIES)
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("path", path)
                    put("entries", buildJsonArray {
                        entries.forEach { entry ->
                            add(buildJsonObject {
                                put("name", entry.name)
                                put("path", joinAbsolute(path, entry.name))
                                put("type", if (entry.isDirectory) "directory" else "file")
                                put("sizeBytes", entry.sizeBytes)
                                put("updatedAt", entry.updatedAt)
                            })
                        }
                    })
                    if (all.size > entries.size) put("truncated", true)
                    put("workspace", target.name)
                }.toString()
            )
        )
    },
)

internal fun createGlobTool(
    targets: WorkspaceToolTargets,
    workspaceRepository: WorkspaceRepository,
    cwds: Map<String, String> = emptyMap(),
) = Tool(
    name = "workspace_glob",
    description = """
        Find files by glob pattern inside the assistant's bound workspace Rootfs, for example `**/*.kt` or `src/**/*.md`.
        Skips .git, node_modules, build and other dependency or output directories.
        Returns absolute paths. Defaults to the session's current working directory; paths must be absolute inside Rootfs.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                put("pattern", buildJsonObject {
                    put("type", "string")
                    put("description", "Glob pattern matched against paths relative to `path`, e.g. **/*.kt")
                })
                putPathProperty(targets.layout, required = false, defaultHint = DEFAULT_PATH_HINT)
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum number of files to return. Defaults to $MAX_GLOB_RESULTS, max 2000.")
                })
            },
            required = targets.required("pattern"),
        )
    },
    needsApproval = { targets.needsApproval(it) },
    isReadOnly = true,
    execute = {
        val params = it.jsonObject
        val target = targets.resolveForExecution(params)
        val pattern = params.string("pattern")?.trim().orEmpty()
        require(pattern.isNotEmpty()) { "pattern is required" }
        val root = params.absolutePathOrNull("path") ?: target.cwdRoot(cwds)
        val limit = (params.int("limit") ?: MAX_GLOB_RESULTS).coerceIn(1, 2000)
        val matcher = GlobMatcher(pattern)
        require(matcher.isValid) { "Invalid glob pattern: $pattern" }

        val matches = mutableListOf<WorkspaceFileEntry>()
        walkWorkspace(workspaceRepository, target, root, MAX_SEARCH_ENTRIES) { absolute, entry ->
            if (!entry.isDirectory) {
                val relative = WorkspacePaths.relativeTo(root, absolute) ?: absolute
                if (matcher.matches(relative, entry.name)) {
                    matches += entry.copy(path = absolute)
                }
            }
            matches.size < limit
        }
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("pattern", pattern)
                    put("path", root)
                    put("files", buildJsonArray {
                        matches.forEach { entry ->
                            add(buildJsonObject {
                                put("path", entry.path)
                                put("sizeBytes", entry.sizeBytes)
                            })
                        }
                    })
                    if (matches.size >= limit) put("truncated", true)
                    put("workspace", target.name)
                }.toString()
            )
        )
    },
)

internal fun createGrepTool(
    targets: WorkspaceToolTargets,
    workspaceRepository: WorkspaceRepository,
    cwds: Map<String, String> = emptyMap(),
) = Tool(
    name = "workspace_grep",
    description = """
        Search file contents inside the assistant's bound workspace Rootfs. Returns absolute file path, 1-based line number and the matched line.
        Skips binary files, files larger than 512KB, and .git, node_modules, build and other dependency or output directories.
        Use `include` to restrict file names, e.g. `*.kt`. Defaults to the session's current working directory; paths must be absolute inside Rootfs.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Text to search for, or a regular expression when regex=true")
                })
                putPathProperty(targets.layout, required = false, defaultHint = DEFAULT_PATH_HINT)
                put("include", buildJsonObject {
                    put("type", "string")
                    put("description", "Only search files whose name matches this glob, e.g. *.kt")
                })
                put("regex", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Treat query as a regular expression. Defaults to false (literal text).")
                })
                put("ignore_case", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Case-insensitive match. Defaults to true.")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum number of matching lines. Defaults to $MAX_GREP_RESULTS, max 1000.")
                })
            },
            required = targets.required("query"),
        )
    },
    needsApproval = { targets.needsApproval(it) },
    isReadOnly = true,
    execute = {
        val params = it.jsonObject
        val target = targets.resolveForExecution(params)
        val query = params.string("query").orEmpty()
        require(query.isNotEmpty()) { "query is required" }
        val root = params.absolutePathOrNull("path") ?: target.cwdRoot(cwds)
        val limit = (params.int("limit") ?: MAX_GREP_RESULTS).coerceIn(1, 1000)
        val include = params.string("include")?.trim()?.takeIf { it.isNotEmpty() }
        val includeMatcher = include?.let(::GlobMatcher)
        require(includeMatcher?.isValid != false) { "Invalid include glob: $include" }
        val options = if (params.boolean("ignore_case") ?: true) setOf(RegexOption.IGNORE_CASE) else emptySet()
        val matcher = runCatching {
            if (params.boolean("regex") == true) Regex(query, options) else Regex(Regex.escape(query), options)
        }.getOrElse { error("Invalid regular expression: ${it.message}") }

        val matches = mutableListOf<SearchMatch>()
        var scanned = 0
        var scanLimitReached = false
        walkWorkspace(workspaceRepository, target, root, MAX_SEARCH_ENTRIES) { absolute, entry ->
            val included = includeMatcher == null || includeMatcher.matches(entry.name, entry.name)
            if (!entry.isDirectory && entry.sizeBytes <= MAX_GREP_FILE_BYTES && included) {
                if (scanned >= MAX_GREP_SCANNED_FILES) {
                    scanLimitReached = true
                    return@walkWorkspace false
                }
                scanned++
                val text = workspaceRepository.readTextForSearch(target.id, absolute, entry.sizeBytes)
                if (text != null) {
                    text.lineSequence().forEachIndexed { index, line ->
                        if (matches.size < limit && matcher.containsMatchIn(line)) {
                            matches += SearchMatch(absolute, index + 1, line.take(MAX_GREP_LINE_CHARS))
                        }
                    }
                }
            }
            matches.size < limit
        }
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("query", query)
                    put("path", root)
                    put("matches", buildJsonArray {
                        matches.forEach { match ->
                            add(buildJsonObject {
                                put("path", match.path)
                                put("line", match.line)
                                put("text", match.text)
                            })
                        }
                    })
                    put("scannedFiles", scanned)
                    if (matches.size >= limit || scanLimitReached) put("truncated", true)
                    put("workspace", target.name)
                }.toString()
            )
        )
    },
)

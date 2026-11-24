package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
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
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.workspace.ApplyOutcome
import me.rerere.rikkahub.data.workspace.UnifiedDiff
import me.rerere.rikkahub.utils.generateUnifiedDiff
import me.rerere.workspace.WorkspaceLayout
import me.rerere.workspace.WorkspacePaths

internal fun createWriteFileTool(
    targets: WorkspaceToolTargets,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_write_file",
    description = """
        Write a UTF-8 text file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use ${targets.layout.workspaceDir} for the workspace files area.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                putPathProperty(targets.layout, required = true)
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "UTF-8 text content to write")
                })
                put("overwrite", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to overwrite an existing file. Defaults to true.")
                })
            },
            required = targets.required("path", "text"),
        )
    },
    needsApproval = { args ->
        targets.needsApproval(args) { target, params ->
            target.requiresWriteApproval("workspace_write_file", params.pathOutsideWritableRoots(target.layout, "path"))
        }
    },
    execute = {
        val params = it.jsonObject
        val target = targets.resolveForExecution(params)
        val path = params.writePath("path", target.layout)
        val text = params.string("text") ?: error("text is required")
        val overwrite = params["overwrite"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
        val entry = workspaceRepository.writeAbsoluteText(target.id, path, text, overwrite)
        val payload = buildJsonObject {
            entry.toJson().forEach { (key, value) -> put(key, value) }
        }.toString()
        listOf(UIMessagePart.Text(payload))
    },
)

internal fun createEditFileTool(
    targets: WorkspaceToolTargets,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_edit_file",
    description = """
        Edit a UTF-8 text file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use ${targets.layout.workspaceDir} for the workspace files area.
        Provide old_text and new_text. By default old_text must occur exactly once; set replace_all=true to replace every occurrence.
        If no exact match is found, whitespace-tolerant line matching is attempted automatically.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                putPathProperty(targets.layout, required = true)
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
            required = targets.required("path", "old_text", "new_text"),
        )
    },
    needsApproval = { args ->
        targets.needsApproval(args) { target, params ->
            target.requiresWriteApproval("workspace_edit_file", params.pathOutsideWritableRoots(target.layout, "path"))
        }
    },
    execute = {
        val params = it.jsonObject
        val target = targets.resolveForExecution(params)
        val path = params.writePath("path", target.layout)
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

internal fun createApplyPatchTool(
    targets: WorkspaceToolTargets,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_apply_patch",
    description = """
        Apply a unified diff to the assistant's bound workspace Rootfs. Paths in the patch are relative to the selected workspace files root (${targets.layout.workspaceDir});
        absolute paths inside Rootfs are accepted too. Supports multiple files and multiple hunks per file, new files (/dev/null old path) and deletions.
        Hunks are matched by context; a missing or ambiguous context rejects the whole patch and nothing is written, so always read the file before patching.
        Returns per-file added/removed counts and any conflicts with hunk index and line. Use dry_run=true to validate without writing.
        ${targets.workspaceNote()}
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putWorkspaceProperty(targets)
                put("patch", buildJsonObject {
                    put("type", "string")
                    put("description", "Unified diff text, e.g. --- a/src/Foo.kt / +++ b/src/Foo.kt / @@ -1,3 +1,4 @@ headers")
                })
                put("dry_run", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Only validate and report, do not write files. Defaults to false.")
                })
            },
            required = targets.required("patch"),
        )
    },
    needsApproval = { args ->
        targets.needsApproval(args) { target, params ->
            target.requiresWriteApproval("workspace_apply_patch", params.patchOutsideWritableRoots(target.layout))
        }
    },
    execute = {
        val params = it.jsonObject
        val target = targets.resolveForExecution(params)
        val patchText = params.string("patch") ?: error("patch is required")
        val dryRun = params["dry_run"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val result = applyUnifiedPatch(
            target = target,
            workspaceRepository = workspaceRepository,
            patchText = patchText,
            dryRun = dryRun,
        )
        listOf(UIMessagePart.Text(result.toString()))
    },
)

private data class PlannedPatchWrite(
    val displayPath: String,
    val absolutePath: String,
    val original: String?,
    val content: String?,
    val added: Int,
    val removed: Int,
)

/** 补丁先全部校验再落盘：任何一个文件冲突都整体放弃，不留半截修改 */
private suspend fun applyUnifiedPatch(
    target: WorkspaceTarget,
    workspaceRepository: WorkspaceRepository,
    patchText: String,
    dryRun: Boolean,
): JsonObject {
    val diffs = UnifiedDiff.parse(patchText)
    if (diffs.isEmpty()) {
        return buildJsonObject {
            put("ok", false)
            put("error", "No unified diff hunks found. Expected ---/+++ file headers and @@ hunks.")
        }
    }

    val writes = mutableListOf<PlannedPatchWrite>()
    val conflicts = mutableListOf<JsonObject>()
    val skipped = mutableListOf<JsonObject>()

    diffs.forEach { diff ->
        if (diff.isBinary) {
            skipped += buildJsonObject {
                put("path", diff.newPath ?: diff.oldPath.orEmpty())
                put("reason", "binary patch is not supported")
            }
            return@forEach
        }
        val rawPath = (diff.newPath ?: diff.oldPath).orEmpty()
        val absolutePath = runCatching { target.layout.resolvePatchPath(rawPath) }.getOrNull()
        if (absolutePath == null) {
            conflicts += buildJsonObject {
                put("path", rawPath)
                put("hunk", 0)
                put("line", 0)
                put("reason", "invalid path")
            }
            return@forEach
        }
        if (!target.layout.isWritable(absolutePath)) {
            conflicts += buildJsonObject {
                put("path", rawPath)
                put("hunk", 0)
                put("line", 0)
                put("reason", "path is outside the writable area")
            }
            return@forEach
        }
        val existing = runCatching {
            workspaceRepository.readTextInRootfs(target.id, absolutePath)
        }.getOrNull()
        val original = if (diff.isNewFile) {
            // /dev/null 开头但目标已存在：直接拒绝，否则会覆盖原文件，撤销时还会把它删掉
            if (existing != null) {
                conflicts += buildJsonObject {
                    put("path", rawPath)
                    put("hunk", 0)
                    put("line", 0)
                    put("reason", "file already exists; read it and send a normal diff instead of /dev/null")
                }
                return@forEach
            }
            null
        } else {
            if (existing == null) {
                conflicts += buildJsonObject {
                    put("path", rawPath)
                    put("hunk", 0)
                    put("line", 0)
                    put("reason", "file not found")
                }
                return@forEach
            }
            existing
        }
        when (val outcome = UnifiedDiff.apply(original.orEmpty(), diff)) {
            is ApplyOutcome.Success -> writes += PlannedPatchWrite(
                displayPath = rawPath,
                absolutePath = absolutePath,
                original = original,
                content = if (diff.isDeletedFile) null else outcome.content,
                added = outcome.added,
                removed = outcome.removed,
            )

            is ApplyOutcome.Conflict -> conflicts += buildJsonObject {
                put("path", outcome.path ?: rawPath)
                put("hunk", outcome.hunkIndex)
                put("line", outcome.line)
                put("reason", outcome.reason)
            }
        }
    }

    if (conflicts.isNotEmpty()) {
        return buildJsonObject {
            put("ok", false)
            put("conflicts", buildJsonArray { conflicts.forEach { add(it) } })
            put("skipped", buildJsonArray { skipped.forEach { add(it) } })
            put("hint", "Nothing was written. Read the current file content and regenerate the patch with matching context.")
        }
    }

    if (!dryRun) {
        writes.forEach { write ->
            val content = write.content
            if (content == null) {
                val (area, relative) = resolveStorageArea(target.layout, write.absolutePath)
                workspaceRepository.deleteFile(target.id, area, relative, recursive = false)
            } else {
                workspaceRepository.writeAbsoluteText(target.id, write.absolutePath, content, overwrite = true)
            }
        }
    }

    return buildJsonObject {
        put("ok", true)
        put("dryRun", dryRun)
        put("applied", writes.size)
        put("files", buildJsonArray {
            writes.forEach { write ->
                add(buildJsonObject {
                    put("path", write.displayPath)
                    put("added", write.added)
                    put("removed", write.removed)
                    if (write.content == null) put("deleted", true)
                    if (write.original == null) put("created", true)
                })
            }
        })
        if (skipped.isNotEmpty()) {
            put("skipped", buildJsonArray { skipped.forEach { add(it) } })
        }
    }
}

/** 补丁里的路径是仓库相对路径, 统一映射到 Rootfs 内的工作区目录 */
private fun WorkspaceLayout.resolvePatchPath(raw: String): String {
    val cleaned = raw.replace('\\', '/').trim()
    require(cleaned.isNotEmpty()) { "empty path in patch" }
    require(!cleaned.contains('\u0000')) { "invalid character in patch path" }
    require(cleaned.split('/').none { it == ".." }) { "patch path must not escape the workspace: $raw" }
    return if (cleaned.startsWith("/")) {
        WorkspacePaths.normalize(cleaned) ?: error("invalid path in patch: $raw")
    } else {
        WorkspacePaths.join(workspaceDir, cleaned) ?: error("invalid path in patch: $raw")
    }
}

private fun kotlinx.serialization.json.JsonObject.patchOutsideWritableRoots(layout: WorkspaceLayout): Boolean =
    runCatching {
        val text = string("patch").orEmpty()
        UnifiedDiff.parse(text).any { diff ->
            val raw = diff.newPath ?: diff.oldPath ?: return@any false
            !layout.isWritable(layout.resolvePatchPath(raw))
        }
    }.getOrDefault(true)


/**
 * 写类工具用的路径: 与审批判定共用 [WorkspacePaths] 的规范形式。
 *
 * 审批判定和落盘必须用同一个规范形式, 否则 `/workspace/../etc/x` 会在审批时被判成
 * 「在可写区内」, 到真正写入时才被折叠成 `/etc/x`。
 */
internal fun WorkspaceLayout.normalizeWritePath(raw: String): String {
    val cleaned = raw.replace('\\', '/').trim()
    require(cleaned.isNotEmpty()) { "path is required" }
    return WorkspacePaths.normalize(cleaned)
        ?: error("path must be an absolute path inside Rootfs: $raw")
}

private fun kotlinx.serialization.json.JsonObject.writePath(
    name: String,
    layout: WorkspaceLayout,
): String = layout.normalizeWritePath(absolutePath(name))

// 免强制审批的可写安全区由工作区后端给出, 本地为工作区文件目录、临时目录和技能目录
private fun kotlinx.serialization.json.JsonElement.pathOutsideWritableRoots(
    layout: WorkspaceLayout,
    name: String,
): Boolean =
    runCatching {
        !layout.isWritable(layout.normalizeWritePath(jsonObject.absolutePath(name)))
    }.getOrDefault(true)

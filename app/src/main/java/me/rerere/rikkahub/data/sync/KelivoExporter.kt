package me.rerere.rikkahub.data.sync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.MessageNodeDAO
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.workspace.HostPaths
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把 L3Hub 的会话数据打包成 Kelivo 能直接恢复的备份 zip。
 *
 * 包内结构对齐 Kelivo 的 kelivo-backup v3 格式：
 * - manifest.json：format=kelivo-backup / formatVersion=3 / payloadKind=sqlite
 * - settings.json：业务偏好；provider/assistant 配置以 provider_configs_v1 / assistants_v1 形式携带，
 * - database/kelivo.db：按 Kelivo schema v5 从零建库（见 [KelivoSchemaV5]），
 *   只填充 conversation_rows / message_rows / message_part_rows，其余表留空
 * - upload/、images/ 等：消息附件，按 kelivo-file:/// 约定写进 part 的 uri
 *
 * 映射规则：
 * - 会话头（标题/创建/更新时间/置顶）原样搬进 conversation_rows
 * - 消息按 message_node 的 node_index + selectIndex 取当前分支；只导出
 *   user/assistant 角色（Kelivo 时间线投影只认这两种角色），system 消息跳过
 * - tool 角色的消息并入前一条 assistant 消息，转成 tool_call part
 *   （payload {id,name,arguments,content,metadata}，与 Kelivo 渲染器解析一致）
 * - 附件：file:// 只认 app 私有目录里白名单内的真实文件；content:// 读流落盘到缓存；找不到或
 *   不在白名单里则跳过该 part（见 [KelivoAttachmentPaths]）
 * - 时间戳统一为 epoch 微秒（Kelivo 的 MicrosecondDateTimeConverter）
 */
class KelivoExporter(
    private val context: Context,
    private val conversationDao: ConversationDAO,
    private val messageNodeDao: MessageNodeDAO,
    private val filesManager: FilesManager,
    private val settingsStore: SettingsStore,
) {
    private val filesDir: File get() = context.filesDir

    suspend fun export(includeFiles: Boolean): File = withContext(Dispatchers.IO) {
        val archive = File.createTempFile("kelivo_backup_", ".zip", context.cacheDir)
        val staging = Files.createTempDirectory(context.cacheDir.toPath(), "kelivo-backup-").toFile()
        try {
            val dbFile = File(staging, "database").apply { mkdirs() }.resolve("kelivo.db")
            val stats = buildDatabase(dbFile, includeFiles)
            val settingsFile = File(staging, "settings.json").apply { writeText(buildSettingsJson()) }
            val manifestFile = File(staging, "manifest.json").apply {
                writeText(buildManifestJson(stats = stats, includeFiles = includeFiles, settingsFile = settingsFile, dbFile = dbFile))
            }
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                addFileToZip(zip, manifestFile, "manifest.json")
                addFileToZip(zip, settingsFile, "settings.json")
                addFileToZip(zip, dbFile, "database/kelivo.db")
                for (attachment in stats.attachments) {
                    currentCoroutineContext().ensureActive()
                    addFileToZip(zip, attachment.file, attachment.entryName)
                }
            }
            archive
        } catch (e: Throwable) {
            archive.delete()
            throw e
        } finally {
            staging.deleteRecursively()
            // content:// 附件临时落盘的目录
            KelivoAttachmentPaths.cacheDirectory(context.cacheDir).deleteRecursively()
        }
    }

    private data class ExportStats(
        val conversationCount: Int,
        val messageCount: Int,
        val attachments: List<AttachmentEntry>,
    )

    private data class AttachmentEntry(val entryName: String, val file: File)

    private data class ResolvedAttachment(
        val folder: String,
        val fileName: String,
        val file: File,
        val displayName: String,
    )

    private suspend fun buildDatabase(dbFile: File, includeFiles: Boolean): ExportStats {
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            KelivoSchemaV5.statements.forEach(db::execSQL)
            db.execSQL("PRAGMA user_version = 5")
            db.beginTransaction()
            try {
                val stats = insertData(db, includeFiles)
                db.setTransactionSuccessful()
                return stats
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
        // 关闭后清掉可能的 WAL 侧文件，备份包只收单个 sqlite 文件
        File(dbFile.parentFile, dbFile.name + "-wal").delete()
        File(dbFile.parentFile, dbFile.name + "-shm").delete()
    }

    private suspend fun insertData(db: SQLiteDatabase, includeFiles: Boolean): ExportStats {
        val attachments = mutableListOf<AttachmentEntry>()
        val attachmentIndex = mutableMapOf<String, AttachmentEntry>()
        var conversationCount = 0
        var messageCount = 0
        for (conversation in conversationDao.getAll().first()) {
            val conversationId = conversation.id
            val fallbackMicros = conversation.createAt * 1000L
            db.insert("conversation_rows", null, conversationValues(conversation, fallbackMicros))
            conversationCount++
            var order = 0
            var lastAssistantId: String? = null
            var lastAssistantOrdinal: Int? = null
            for (node in messageNodeDao.getNodesOfConversation(conversationId)) {
                val messages = runCatching {
                    JsonInstant.decodeFromString<List<UIMessage>>(node.messages)
                }.getOrNull() ?: continue
                if (messages.isEmpty()) continue
                val index = if (node.selectIndex in messages.indices) node.selectIndex else 0
                val message = messages[index]
                when (message.role) {
                    MessageRole.USER, MessageRole.ASSISTANT -> {
                        val messageId = message.id.toString()
                        db.insert("message_rows", null, messageValues(conversationId, message, order, fallbackMicros))
                        var ordinal = 0
                        val toolCalls = mutableListOf<String>()
                        for (part in message.parts) {
                            when (part) {
                                is UIMessagePart.Text -> insertPart(db, conversationId, messageId, ordinal++, "text", part.text, fallbackMicros)
                                is UIMessagePart.Reasoning -> insertPart(db, conversationId, messageId, ordinal++, "reasoning", part.reasoning, fallbackMicros)
                                is UIMessagePart.Notice -> insertPart(db, conversationId, messageId, ordinal++, "text", noticeText(part), fallbackMicros)
                                is UIMessagePart.Image -> insertAttachmentPart(db, conversationId, messageId, ordinal++, "image", part.url, null, null, fallbackMicros, includeFiles, attachments, attachmentIndex)
                                is UIMessagePart.Document -> insertAttachmentPart(db, conversationId, messageId, ordinal++, "file", part.url, part.fileName, part.mime, fallbackMicros, includeFiles, attachments, attachmentIndex)
                                is UIMessagePart.Video -> insertAttachmentPart(db, conversationId, messageId, ordinal++, "file", part.url, null, "video/*", fallbackMicros, includeFiles, attachments, attachmentIndex)
                                is UIMessagePart.Audio -> insertAttachmentPart(db, conversationId, messageId, ordinal++, "file", part.url, null, "audio/*", fallbackMicros, includeFiles, attachments, attachmentIndex)
                                is UIMessagePart.Tool -> toolCalls += toolCallPayload(part.toolCallId, part.toolName, part.inputAsJson(), flattenToolOutput(part.output))
                                is UIMessagePart.ServerTool -> toolCalls += toolCallPayload(part.toolCallId, part.toolName, part.input, part.output?.toString().orEmpty())
                                is UIMessagePart.ToolCall, is UIMessagePart.ToolResult, UIMessagePart.Search -> Unit
                            }
                        }
                        for (payload in toolCalls) insertPart(db, conversationId, messageId, ordinal++, "tool_call", payload, fallbackMicros)
                        order++
                        messageCount++
                        lastAssistantId = if (message.role == MessageRole.ASSISTANT) messageId else lastAssistantId
                        lastAssistantOrdinal = if (message.role == MessageRole.ASSISTANT) ordinal else lastAssistantOrdinal
                    }
                    MessageRole.TOOL -> {
                        // tool 消息并入前一条 assistant 消息：其 Tool/ServerTool 输出成为 tool_call part
                        val targetId = lastAssistantId ?: continue
                        var ordinal = lastAssistantOrdinal ?: continue
                        val payloads = toolCallPayloadsOfToolMessage(message.parts)
                        if (payloads.isEmpty()) continue
                        for (payload in payloads) insertPart(db, conversationId, targetId, ordinal++, "tool_call", payload, fallbackMicros)
                        lastAssistantOrdinal = ordinal
                    }
                    MessageRole.SYSTEM -> Unit // Kelivo 时间线只渲染 user/assistant，跳过
                }
            }
        }
        return ExportStats(
            conversationCount = conversationCount,
            messageCount = messageCount,
            attachments = attachments,
        )
    }

    private fun conversationValues(conversation: ConversationEntity, createdAtMicros: Long): ContentValues =
        ContentValues().apply {
            put("id", conversation.id)
            put("title", conversation.title.ifBlank { "Untitled" })
            put("created_at", createdAtMicros)
            put("updated_at", conversation.updateAt * 1000L)
            put("is_pinned", if (conversation.isPinned) 1 else 0)
            put("assistant_id", conversation.assistantId)
            // truncate_index / version_selections_json / chat_suggestions_json 等列走默认值
        }

    private fun messageValues(
        conversationId: String,
        message: UIMessage,
        messageOrder: Int,
        fallbackMicros: Long,
    ): ContentValues = ContentValues().apply {
        put("id", message.id.toString())
        put("conversation_id", conversationId)
        put("role", if (message.role == MessageRole.USER) "user" else "assistant")
        put("timestamp", messageMicros(message, fallbackMicros))
        put("model_id", message.modelSnapshot?.modelId ?: message.modelId?.toString())
        message.usage?.let { usage ->
            put("total_tokens", usage.totalTokens)
            put("prompt_tokens", usage.promptTokens)
            put("completion_tokens", usage.completionTokens)
            put("cached_tokens", usage.cachedTokens)
        }
        message.generationTimeMs?.let { put("duration_ms", it) }
        message.translation?.let { put("translation", it) }
        put("message_order", messageOrder)
        put("extras_json", "{}")
    }

    private fun messageMicros(message: UIMessage, fallbackMicros: Long): Long =
        runCatching {
            message.createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() * 1000L
        }.getOrElse { fallbackMicros }

    private fun insertPart(
        db: SQLiteDatabase,
        conversationId: String,
        revisionId: String,
        ordinal: Int,
        kind: String,
        payload: String,
        micros: Long,
    ) {
        db.insert("message_part_rows", null, ContentValues().apply {
            put("conversation_id", conversationId)
            put("revision_id", revisionId)
            put("ordinal", ordinal)
            put("kind", kind)
            put("payload", payload)
            put("created_at", micros)
            put("updated_at", micros)
        })
    }

    private suspend fun insertAttachmentPart(
        db: SQLiteDatabase,
        conversationId: String,
        revisionId: String,
        ordinal: Int,
        kind: String,
        url: String,
        displayName: String?,
        mime: String?,
        micros: Long,
        includeFiles: Boolean,
        attachments: MutableList<AttachmentEntry>,
        attachmentIndex: MutableMap<String, AttachmentEntry>,
    ) {
        val resolved = resolveAttachment(url, displayName, mime) ?: return
        val entryName = registerAttachment(resolved, includeFiles, attachments, attachmentIndex) ?: return
        val payload = if (kind == "image") {
            buildJsonObject { put("uri", KelivoAttachmentPaths.kelivoUri(entryName)) }.toString()
        } else {
            buildJsonObject {
                put("uri", KelivoAttachmentPaths.kelivoUri(entryName))
                put("name", resolved.displayName)
                if (mime != null) put("mime", mime)
            }.toString()
        }
        insertPart(db, conversationId, revisionId, ordinal, kind, payload, micros)
    }

    /**
     * 登记一条待打包的附件，返回它在包里的条目名。
     *
     * 同一个文件被引用多次时共用一个条目；不同来源撞到同一个名字（两个子目录里的 `a.md` 都并入
     * upload/）时按序号让开，避免后一条把前一条覆盖掉。名字让不开或不合规时返回 null，
     * 调用方按附件缺失跳过——manifest 与 zip 用的是同一份列表，不会出现只有一侧有的条目。
     */
    private fun registerAttachment(
        resolved: ResolvedAttachment,
        includeFiles: Boolean,
        attachments: MutableList<AttachmentEntry>,
        attachmentIndex: MutableMap<String, AttachmentEntry>,
    ): String? {
        val baseName = KelivoAttachmentPaths.entryName(resolved.folder, resolved.fileName)
        if (!includeFiles) return baseName.takeIf { KelivoAttachmentPaths.isSafeEntryName(it) }
        val name = KelivoAttachmentPaths.entryNameFor(
            folder = resolved.folder,
            fileName = resolved.fileName,
            source = resolved.file,
            taken = { key -> attachmentIndex[key]?.file },
        ) ?: return null
        // 同一文件复用条目时只登记一次，否则 ZipOutputStream 会因同名条目终止整包。
        if (name !in attachmentIndex) {
            val entry = AttachmentEntry(name, resolved.file)
            attachmentIndex[name] = entry
            attachments += entry
        }
        return name
    }

    /**
     * 一条附件引用的来源。
     *
     * 两条路径的边界不一样：
     * - `file://` 必须落在 filesDir 的白名单目录里（[KelivoAttachmentPaths.sourceFile]），否则整条跳过；
     * - `content://` 先按生成的名字落盘到缓存，再确认缓存里的这个文件解析得回缓存目录
     *   （[KelivoAttachmentPaths.cacheTarget] 已做 containment）。DISPLAY_NAME 是不可信的展示串，
     *   只用来生成清洗过的归档名和 part 的 `name`，不参与落盘路径。
     *
     * 拒绝的附件返回 null，调用方与「文件缺失」走同一条跳过路径，不影响整包导出。
     */
    private suspend fun resolveAttachment(url: String, displayName: String?, mime: String?): ResolvedAttachment? {
        val uri = Uri.parse(url)
        val source = when (uri.scheme) {
            "file" -> resolveFileUri(uri.path, displayName) ?: return null
            "content" -> resolveContentUri(uri, displayName, mime) ?: return null
            else -> return null
        }
        return ResolvedAttachment(
            folder = source.folder,
            fileName = source.fileName,
            file = source.file,
            displayName = KelivoAttachmentPaths.displayName(
                source.displayName ?: displayName ?: source.file.name
            ),
        )
    }

    /** `file://`：只认 filesDir 白名单目录里的真实文件，其余（含软链逃逸）返回 null */
    private suspend fun resolveFileUri(path: String?, displayName: String?): SourceFile? {
        val file = KelivoAttachmentPaths.sourceFile(filesDir, path) ?: return null
        val relativePath = KelivoAttachmentPaths.relativePath(filesDir, file) ?: return null
        return SourceFile(
            file = file,
            folder = KelivoAttachmentPaths.archiveFolder(relativePath),
            fileName = KelivoAttachmentPaths.sanitizeSegment(file.name),
            // 消息里的 fileName 是用户当初看到的名字；没有时退回 managed files 的记录，再退回磁盘名
            displayName = displayName?.takeIf { it.isNotBlank() }
                ?: filesManager.getByRelativePath(relativePath)?.displayName,
        )
    }

    /** `content://`：落盘名是生成的，归档名用清洗过的展示名，展示名原样写进 part */
    private fun resolveContentUri(uri: Uri, displayName: String?, mime: String?): SourceFile? {
        val copied = copyContentUri(uri, displayName, mime) ?: return null
        return SourceFile(
            file = copied.file,
            folder = KelivoAttachmentPaths.archiveFolder(null),
            fileName = KelivoAttachmentPaths.sanitizeSegment(copied.displayName ?: copied.file.name),
            displayName = copied.displayName,
        )
    }

    private data class SourceFile(
        val file: File,
        val folder: String,
        val fileName: String,
        val displayName: String?,
    )

    /**
     * `content://` 读流落盘。
     *
     * 落盘文件名由 [KelivoAttachmentPaths.cacheTarget] 生成，不用 DISPLAY_NAME：那个串由外部
     * ContentProvider 给出，可以是 `../../datastore/settings.preferences_pb`，resolve 之后写穿缓存目录。
     * DISPLAY_NAME 只作为展示名留在 part 的 `name` 字段，扩展名另取一份做归档用。
     */
    private fun copyContentUri(uri: Uri, displayName: String?, mime: String?): CopiedContent? {
        val name = displayName?.takeIf { it.isNotBlank() } ?: runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        // token 由 uri 派生：同一条 uri 在会话里被引用多次时落在同一个缓存文件，
        // 归档条目名也就稳定（与以前按 DISPLAY_NAME 去重的结果一致），不同 uri 不会互相覆盖
        val target = KelivoAttachmentPaths.cacheTarget(
            cacheDir = context.cacheDir,
            displayName = name,
            mimeType = mime,
            token = KelivoAttachmentPaths.cacheToken(uri.toString()),
        ) ?: return null
        val copied = runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
            input.use {
                target.outputStream().use { output -> it.copyTo(output) }
            }
            target.takeIf { it.isFile }
        }.getOrNull() ?: return null
        // 落盘之后再看一眼 canonical path：目标必须解析回缓存目录之内
        if (!HostPaths.contains(KelivoAttachmentPaths.cacheDirectory(context.cacheDir), copied)) {
            runCatching { copied.delete() }
            return null
        }
        return CopiedContent(file = copied, displayName = name)
    }

    private data class CopiedContent(val file: File, val displayName: String?)

    private fun noticeText(part: UIMessagePart.Notice): String =
        if (part.title.isBlank()) part.text else part.title + "\n" + part.text

    private fun toolCallPayloadsOfToolMessage(parts: List<UIMessagePart>): List<String> {
        val result = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                is UIMessagePart.Tool -> result += toolCallPayload(part.toolCallId, part.toolName, part.inputAsJson(), flattenToolOutput(part.output))
                is UIMessagePart.ServerTool -> result += toolCallPayload(part.toolCallId, part.toolName, part.input, part.output?.toString().orEmpty())
                is UIMessagePart.ToolCall -> result += toolCallPayload(part.toolCallId, part.toolName, parseArgs(part.arguments), "")
                is UIMessagePart.ToolResult -> result += toolCallPayload(part.toolCallId, part.toolName, parseArgs(part.arguments.toString()), part.content.toString())
                else -> Unit
            }
        }
        return result
    }

    private fun toolCallPayload(id: String, name: String, arguments: JsonElement?, content: String): String =
        buildJsonObject {
            put("id", id)
            put("name", name)
            put("arguments", arguments ?: buildJsonObject { })
            put("content", content)
            put("metadata", buildJsonObject { })
        }.toString()

    private fun parseArgs(raw: String): JsonElement = runCatching {
        JsonInstant.parseToJsonElement(raw.ifBlank { "{}" })
    }.getOrElse { buildJsonObject { } }

    private fun flattenToolOutput(output: List<UIMessagePart>): String = output
        .joinToString("\n") { part ->
            when (part) {
                is UIMessagePart.Text -> part.text
                is UIMessagePart.Notice -> part.text
                is UIMessagePart.Image -> part.url
                is UIMessagePart.Document -> "[${part.fileName}] ${part.url}"
                else -> ""
            }
        }
        .trim()

    /**
     * 生成 Kelivo 的 settings.json：把 L3Hub 的 provider 与 assistant 配置按官方
     * 业务键（provider_configs_v1 / assistants_v1 / providers_order_v1）带进备份包，
     * 恢复时由 Kelivo 的 BusinessSettingsRouter 路由进 provider_rows / assistant_rows。
     * 无可导出的配置时保持空对象（Kelivo 接受 {}）。
     */
    private suspend fun buildSettingsJson(): String {
        val settings = settingsStore.settingsFlow.first()
        val providerConfigs = linkedMapOf<String, JsonObject>()
        for (provider in settings.providers) {
            toKelivoProvider(provider)?.let { providerConfigs[provider.id.toString()] = it }
        }
        val exportedProviderIds = providerConfigs.keys
        val assistants = buildJsonArray {
            for (assistant in settings.assistants) {
                toKelivoAssistant(assistant, exportedProviderIds, settings.providers)?.let { add(it) }
            }
        }
        val providerConfigsJson = buildJsonObject {
            for ((key, config) in providerConfigs) put(key, config)
        }
        val order = buildJsonArray {
            for (key in providerConfigs.keys) add(key)
        }
        return buildJsonObject {
            if (providerConfigs.isNotEmpty()) {
                put("provider_configs_v1", JsonPrimitive(providerConfigsJson.toString()))
                put("providers_order_v1", JsonPrimitive(order.toString()))
            }
            if (assistants.isNotEmpty()) {
                put("assistants_v1", JsonPrimitive(assistants.toString()))
            }
        }.toString()
    }

    /**
     * OpenAI / Google / Claude 映射到 Kelivo 的 ProviderKind；账号类（OAuth 凭据）没有对应类型，跳过。
     */
    private fun toKelivoProvider(provider: ProviderSetting): JsonObject? = when (provider) {
        is ProviderSetting.OpenAI -> kelivoProviderConfig(
            id = provider.id.toString(),
            enabled = provider.enabled,
            name = provider.name,
            apiKey = provider.apiKey,
            baseUrl = provider.baseUrl,
            providerType = "openai",
            chatPath = provider.chatCompletionsPath,
            useResponseApi = provider.useResponseApi,
            models = provider.models.map { it.modelId },
            customHeaders = provider.customHeaders.map { it.name to it.value },
        )
        is ProviderSetting.Claude -> kelivoProviderConfig(
            id = provider.id.toString(),
            enabled = provider.enabled,
            name = provider.name,
            apiKey = provider.apiKey,
            baseUrl = provider.baseUrl,
            providerType = "claude",
            chatPath = null,
            useResponseApi = null,
            models = provider.models.map { it.modelId },
            customHeaders = provider.customHeaders.map { it.name to it.value },
        )
        is ProviderSetting.Google -> kelivoProviderConfig(
            id = provider.id.toString(),
            enabled = provider.enabled,
            name = provider.name,
            apiKey = provider.apiKey,
            baseUrl = provider.baseUrl,
            providerType = "google",
            chatPath = null,
            useResponseApi = null,
            vertexAI = provider.vertexAI,
            location = provider.location.takeIf { provider.vertexAI },
            projectId = provider.projectId.takeIf { provider.vertexAI },
            models = provider.models.map { it.modelId },
            customHeaders = provider.customHeaders.map { it.name to it.value },
        )
        else -> null
    }

    private fun kelivoProviderConfig(
        id: String,
        enabled: Boolean,
        name: String,
        apiKey: String,
        baseUrl: String,
        providerType: String,
        chatPath: String?,
        useResponseApi: Boolean?,
        vertexAI: Boolean? = null,
        location: String? = null,
        projectId: String? = null,
        models: List<String>,
        customHeaders: List<Pair<String, String>>,
    ): JsonObject = buildJsonObject {
        put("id", id)
        put("enabled", enabled)
        put("name", name)
        put("apiKey", apiKey)
        put("baseUrl", baseUrl)
        put("providerType", providerType)
        if (chatPath != null) put("chatPath", chatPath)
        if (useResponseApi != null) put("useResponseApi", useResponseApi)
        if (vertexAI != null) put("vertexAI", vertexAI)
        if (location != null) put("location", location)
        if (projectId != null) put("projectId", projectId)
        put("models", buildJsonArray { models.forEach { add(it) } })
        put("modelOverrides", buildJsonObject { })
        put("customHeaders", buildJsonArray {
            for ((name, value) in customHeaders) {
                add(buildJsonObject { put("name", name); put("value", value) })
            }
        })
        put("apiKeys", buildJsonArray { })
        put("keyManagement", buildJsonObject { })
    }

    /**
     * assistant 的模型引用（Uuid）反查 provider + Model.modelId（API 模型串）：
     * chatModelProvider 指向导出的 provider key，chatModelId 落在该 provider 的 models 列表里；
     * 引用已删除模型或被跳过的本地/账号类 provider 时留空，让 Kelivo 走全局默认。
     */
    private fun toKelivoAssistant(
        assistant: Assistant,
        exportedProviderIds: Set<String>,
        providers: List<ProviderSetting>,
    ): JsonObject? {
        var chatModelProvider: String? = null
        var chatModelId: String? = null
        assistant.chatModelId?.let { modelId ->
            for (provider in providers) {
                val matched = provider.models.firstOrNull { it.id == modelId } ?: continue
                val providerKey = provider.id.toString()
                if (providerKey in exportedProviderIds) {
                    chatModelProvider = providerKey
                    chatModelId = matched.modelId
                }
                break
            }
        }
        return buildJsonObject {
            put("id", assistant.id.toString())
            put("name", assistant.name)
            assistant.temperature?.let { put("temperature", it) }
            assistant.topP?.let { put("topP", it) }
            assistant.maxTokens?.let { put("maxTokens", it) }
            put("systemPrompt", assistant.systemPrompt)
            put("streamOutput", assistant.streamOutput)
            put("messageTemplate", assistant.messageTemplate)
            put("allowConversationSystemPrompt", assistant.allowConversationSystemPrompt)
            put("searchEnabled", assistant.enableWebSearch)
            if (chatModelProvider != null) put("chatModelProvider", chatModelProvider)
            if (chatModelId != null) put("chatModelId", chatModelId)
            if (assistant.customHeaders.isNotEmpty()) {
                put("customHeaders", buildJsonArray {
                    for (header in assistant.customHeaders) {
                        add(buildJsonObject { put("name", header.name); put("value", header.value) })
                    }
                })
            }
            if (assistant.background != null) put("background", assistant.background)
            put("useGradientBackground", assistant.useGradientBackground)
        }
    }

    private fun buildManifestJson(
        stats: ExportStats,
        includeFiles: Boolean,
        settingsFile: File,
        dbFile: File,
    ): String {
        val entries = buildJsonObject {
            put("settings.json", hashEntry(settingsFile))
            put("database/kelivo.db", hashEntry(dbFile))
            for (attachment in stats.attachments) {
                put(attachment.entryName, hashEntry(attachment.file))
            }
        }
        val scope = buildJsonObject {
            for (category in KELIVO_SCOPE_CATEGORIES) put(category, true)
        }
        val database = buildJsonObject {
            put("entry", "database/kelivo.db")
            put("schemaVersion", 5)
            put("conversationCount", stats.conversationCount)
            put("messageCount", stats.messageCount)
        }
        return buildJsonObject {
            put("format", "kelivo-backup")
            put("formatVersion", 3)
            put("minimumReadableFormatVersion", 3)
            put("payloadKind", "sqlite")
            put("createdAtUtc", Instant.now().toString())
            put("appVersion", appVersion())
            put("includeChats", true)
            put("includeFiles", includeFiles)
            put("secretsIncluded", true)
            put("scope", scope)
            put("database", database)
            put("entries", entries)
        }.toString()
    }

    private fun hashEntry(file: File): JsonObject = buildJsonObject {
        put("bytes", file.length())
        put("sha256", sha256Hex(file))
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun addFileToZip(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(requireSafeEntryName(entryName)))
        file.inputStream().use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    /**
     * 归档条目名最后一道校验：绝对路径、反斜杠、空段与 `..` 段一律拒绝。
     *
     * 名字本该在 [KelivoAttachmentPaths] 里就清洗干净，这里只防以后有人绕过那条路。
     */
    private fun requireSafeEntryName(entryName: String): String {
        require(KelivoAttachmentPaths.isSafeEntryName(entryName)) {
            "Unsafe backup entry name: $entryName"
        }
        return entryName
    }

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "L3Hub"

    companion object {
        /** Kelivo BackupScope 的全部 15 个分类名；官方备份包全部置 true */
        private val KELIVO_SCOPE_CATEGORIES = listOf(
            "chats", "files", "assistants", "providers", "mcp",
            "environmentVariables", "skills", "worldBooks", "memories",
            "quickPhrases", "instructions", "workspaces", "searchServices",
            "speechServices", "settings",
        )
    }
}

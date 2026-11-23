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
 * - 附件：file:// 引用直接取文件；content:// 读流落盘；找不到文件则跳过该 part
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

    /** 附件放进 zip 时允许保留的顶层目录；其余目录一律并入 upload/ */
    private val allowedRoots = setOf("upload", "images", "fonts", "avatars")

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
            File(context.cacheDir, "kelivo-backup-upload").deleteRecursively()
        }
    }

    private data class ExportStats(
        val conversationCount: Int,
        val messageCount: Int,
        val attachments: List<AttachmentEntry>,
    )

    private data class AttachmentEntry(val entryName: String, val file: File)

    private data class ResolvedAttachment(
        val kelivoUri: String,
        val entryName: String,
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
        if (includeFiles && attachmentIndex.putIfAbsent(resolved.entryName, AttachmentEntry(resolved.entryName, resolved.file)) == null) {
            attachments += AttachmentEntry(resolved.entryName, resolved.file)
        }
        val payload = if (kind == "image") {
            buildJsonObject { put("uri", resolved.kelivoUri) }.toString()
        } else {
            buildJsonObject {
                put("uri", resolved.kelivoUri)
                put("name", resolved.displayName)
                if (mime != null) put("mime", mime)
            }.toString()
        }
        insertPart(db, conversationId, revisionId, ordinal, kind, payload, micros)
    }

    private suspend fun resolveAttachment(url: String, displayName: String?, mime: String?): ResolvedAttachment? {
        val uri = Uri.parse(url)
        val source: File = when (uri.scheme) {
            "file" -> uri.path?.let(::File)?.takeIf { it.isFile } ?: return null
            "content" -> copyContentUri(uri, displayName) ?: return null
            else -> return null
        }
        val relativePath = if (source.path.startsWith(filesDir.path + File.separator)) {
            source.relativeTo(filesDir).toString()
        } else {
            null
        }
        val folder = relativePath
            ?.substringBefore('/', "")
            ?.takeIf { it in allowedRoots }
            ?: "upload"
        val entryName = "$folder/${source.name}"
        val resolvedName = displayName
            ?: relativePath?.let { filesManager.getByRelativePath(it)?.displayName }
            ?: source.name
        return ResolvedAttachment(
            kelivoUri = "kelivo-file:///$folder/${source.name}",
            entryName = entryName,
            file = source,
            displayName = resolvedName,
        )
    }

    private fun copyContentUri(uri: Uri, displayName: String?): File? {
        val name = displayName ?: runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull() ?: "file"
        val target = File(context.cacheDir, "kelivo-backup-upload").apply { mkdirs() }
            .resolve(name)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.takeIf { it.isFile }
        }.getOrNull()
    }

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
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { input -> input.copyTo(zip) }
        zip.closeEntry()
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

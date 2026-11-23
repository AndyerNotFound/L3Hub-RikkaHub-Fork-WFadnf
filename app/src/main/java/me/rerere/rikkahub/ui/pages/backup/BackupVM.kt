package me.rerere.rikkahub.ui.pages.backup

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.saveUploadFromBytes
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.bindConfig
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.sync.KelivoExporter
import me.rerere.rikkahub.data.sync.RestoreOptions
import me.rerere.rikkahub.data.sync.RestoreStageResult
import me.rerere.rikkahub.data.sync.S3BackupItem
import me.rerere.rikkahub.data.sync.S3Sync
import me.rerere.rikkahub.data.sync.importer.ChatboxImporter
import me.rerere.rikkahub.data.sync.importer.CherryStudioProviderImporter
import me.rerere.rikkahub.data.sync.importer.DeepSeekImportResult
import me.rerere.rikkahub.data.sync.importer.MarkdownChatImporter
import me.rerere.rikkahub.data.sync.importer.XmlChatImporter
import me.rerere.rikkahub.data.sync.importer.DeepSeekMigrator
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import me.rerere.rikkahub.data.sync.webdav.WebDavSync
import me.rerere.rikkahub.utils.UiState
import java.io.File

private const val TAG = "BackupVM"

class BackupVM(
    private val settingsStore: SettingsStore,
    private val webDavSync: WebDavSync,
    private val s3Sync: S3Sync,
    private val conversationRepository: ConversationRepository,
    private val filesManager: FilesManager,
    private val kelivoExporter: KelivoExporter,
    private val deepSeekMigrator: DeepSeekMigrator,
) : ViewModel() {
    val settings = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Settings.dummy()
    )

    val webDavBackupItems = MutableStateFlow<UiState<List<WebDavBackupItem>>>(UiState.Idle)
    val s3BackupItems = MutableStateFlow<UiState<List<S3BackupItem>>>(UiState.Idle)
    val localBackupItems = MutableStateFlow(WebDavConfig.BackupItem.entries.toList())

    init {
        loadBackupFileItems()
        loadS3BackupFileItems()
    }

    fun updateSettings(fn: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsStore.update(fn)
        }
    }

    fun updateLocalBackupItems(items: List<WebDavConfig.BackupItem>) {
        localBackupItems.value = items
    }

    fun loadBackupFileItems() {
        viewModelScope.launch {
            runCatching {
                webDavBackupItems.emit(UiState.Loading)
                webDavBackupItems.emit(
                    value = UiState.Success(
                        data = webDavSync.listBackupFiles(
                            config = settings.value.webDavConfig
                        ).sortedByDescending { it.lastModified }
                    )
                )
            }.onFailure {
                webDavBackupItems.emit(UiState.Error(it))
            }
        }
    }

    suspend fun testWebDav() {
        webDavSync.testConnection(settings.value.webDavConfig)
    }

    suspend fun backup() {
        webDavSync.backup(settings.value.webDavConfig, incremental = incrementalBackup())
        recordBackupTime()
    }

    suspend fun restore(item: WebDavBackupItem, options: RestoreOptions): RestoreStageResult =
        webDavSync.restore(config = settings.value.webDavConfig, item = item, options = options)

    suspend fun deleteWebDavBackupFile(item: WebDavBackupItem) {
        webDavSync.deleteBackupFile(settings.value.webDavConfig, item)
    }

    suspend fun exportToFile(): File {
        val file = webDavSync.prepareBackupFile(
            config = settings.value.webDavConfig.copy(items = localBackupItems.value),
            incremental = incrementalBackup(),
        )
        recordBackupTime()
        return file
    }

    /** 导出原版 RikkaHub 能直接恢复的整份 zip；不记为一次常规备份 */
    suspend fun exportUpstreamCompat(): File = webDavSync.prepareUpstreamCompatFile(localBackupItems.value)

    /** 导出 Kelivo 能直接恢复的备份 zip；附件是否打包跟随备份内容勾选，不记为一次常规备份 */
    suspend fun exportKelivoCompat(): File = kelivoExporter.export(
        includeFiles = localBackupItems.value.contains(WebDavConfig.BackupItem.FILES),
    )

    /** 默认走增量（.l3b）；用户在数据备份页切到「全量」时仍按旧 zip 打包 */
    fun incrementalBackup(): Boolean = settings.value.backupFormat != BACKUP_FORMAT_FULL

    suspend fun restoreFromLocalFile(file: File, options: RestoreOptions): RestoreStageResult =
        webDavSync.restoreFromLocalFile(file, options)

    /** 从 Markdown 聊天记录新建一个会话，不覆盖任何已有数据 */
    suspend fun importMarkdown(text: String): MarkdownImportResult = withContext(Dispatchers.IO) {
        val parsed = MarkdownChatImporter.parse(text)
        require(parsed.messages.isNotEmpty()) { "No chat messages found in this file" }
        val title = parsed.title.ifBlank {
            parsed.messages.first().toText().trim().take(40).ifBlank { "Markdown import" }
        }
        conversationRepository.insertConversation(
            Conversation(
                assistantId = settings.value.assistantId,
                title = title,
                messageNodes = parsed.messages.map { it.toMessageNode() },
            )
        )
        MarkdownImportResult(title = title, count = parsed.messages.size)
    }

    /** 从 XML 聊天记录新建会话，不覆盖任何已有数据 */
    suspend fun importXml(text: String): XmlImportResult = withContext(Dispatchers.IO) {
        val chats = XmlChatImporter.parse(text).filter { it.messages.isNotEmpty() }
        require(chats.isNotEmpty()) { "No chat messages found in this file" }
        chats.forEach { chat ->
            val title = chat.title.ifBlank {
                chat.messages.first().toText().trim().take(40).ifBlank { "XML import" }
            }
            conversationRepository.insertConversation(
                Conversation(
                    assistantId = settings.value.assistantId,
                    title = title,
                    messageNodes = chat.messages.map { it.toMessageNode() },
                )
            )
        }
        val first = chats.first()
        XmlImportResult(
            title = first.title.ifBlank { first.messages.first().toText().trim().take(40) },
            conversations = chats.size,
            count = chats.sumOf { it.messages.size },
        )
    }

    suspend fun restoreFromChatBox(file: File): ChatboxRestoreResult = withContext(Dispatchers.IO) {
        val currentSettings = settings.value
        var importedConversations = 0
        var skippedExistingConversations = 0
        val result = ChatboxImporter.importStreaming(
            file = file,
            assistantId = currentSettings.assistantId,
            providers = currentSettings.providers,
            shouldImportConversation = { conversationId ->
                val exists = conversationRepository.existsConversationById(conversationId)
                if (exists) skippedExistingConversations++
                !exists
            },
            saveImage = { resource ->
                val entity = filesManager.saveUploadFromBytes(
                    bytes = resource.bytes,
                    displayName = resource.fileName,
                    mimeType = resource.mimeType,
                )
                filesManager.getFile(entity).toUri().toString()
            },
            onConversation = { conversation ->
                conversationRepository.insertConversation(conversation)
                importedConversations++
            }
        )

        val targetAssistantId = currentSettings.assistantId
        settingsStore.update { latestSettings ->
            latestSettings.copy(
                providers = result.providers + latestSettings.providers.filterNot { existing ->
                    result.providers.any { imported -> imported.id == existing.id }
                },
                assistants = latestSettings.assistants.map { assistant ->
                    if (result.hasConversationSystemPrompt && assistant.id == targetAssistantId) {
                        assistant.copy(allowConversationSystemPrompt = true)
                    } else {
                        assistant
                    }
                }
            )
        }

        Log.i(
            TAG,
            "restoreFromChatBox: import ${result.providers.size} providers, " +
                "$importedConversations conversations, skip $skippedExistingConversations existing, " +
                "import ${result.importedImageParts} images, drop ${result.skippedImageParts} images, " +
                "skip ${result.skippedForkMessages} fork messages and ${result.skippedSessions} sessions"
        )
        ChatboxRestoreResult(
            importedProviders = result.providers.size,
            importedConversations = importedConversations,
            skippedExistingConversations = skippedExistingConversations,
            importedImageParts = result.importedImageParts,
            skippedImageParts = result.skippedImageParts,
            skippedEmptyMessages = result.skippedEmptyMessages,
            skippedForkMessages = result.skippedForkMessages,
            skippedSessions = result.skippedSessions,
        )
    }

    suspend fun restoreFromDeepSeek(uri: Uri): DeepSeekImportResult = deepSeekMigrator.import(uri)

    fun restoreFromCherryStudio(file: File) {
        val importProviders = CherryStudioProviderImporter.importProviders(file)

        if (importProviders.isEmpty()) {
            throw IllegalArgumentException("No importable providers found in Cherry Studio backup")
        }

        Log.i(TAG, "restoreFromCherryStudio: import ${importProviders.size} providers: $importProviders")

        updateSettings {
            it.copy(
                providers = importProviders + it.providers,
            )
        }
    }

    // S3 Backup methods
    fun loadS3BackupFileItems() {
        viewModelScope.launch {
            runCatching {
                s3BackupItems.emit(UiState.Loading)
                s3BackupItems.emit(
                    value = UiState.Success(
                        data = s3Sync.listBackupFiles(
                            config = settings.value.s3Config
                        )
                    )
                )
            }.onFailure {
                s3BackupItems.emit(UiState.Error(it))
            }
        }
    }

    suspend fun testS3() {
        s3Sync.testS3(settings.value.s3Config)
    }

    suspend fun backupToS3() {
        s3Sync.backupToS3(settings.value.s3Config, incremental = incrementalBackup())
        recordBackupTime()
    }

    suspend fun restoreFromS3(item: S3BackupItem, options: RestoreOptions): RestoreStageResult =
        s3Sync.restoreFromS3(config = settings.value.s3Config, item = item, options = options)

    suspend fun deleteS3BackupFile(item: S3BackupItem) {
        s3Sync.deleteS3BackupFile(settings.value.s3Config, item)
    }

    private suspend fun recordBackupTime() {
        settingsStore.update { settings ->
            settings.copy(
                backupReminderConfig = settings.backupReminderConfig.copy(
                    lastBackupTime = System.currentTimeMillis()
                )
            )
        }
    }
}

/** 备份格式在设置里以字符串存，避免为一个开关新增枚举的序列化兼容问题 */
const val BACKUP_FORMAT_FULL = "FULL"

data class ChatboxRestoreResult(
    val importedProviders: Int,
    val importedConversations: Int,
    val skippedExistingConversations: Int,
    val importedImageParts: Int,
    val skippedImageParts: Int,
    val skippedEmptyMessages: Int,
    val skippedForkMessages: Int,
    val skippedSessions: Int,
)

data class MarkdownImportResult(val title: String, val count: Int)

data class XmlImportResult(val title: String, val conversations: Int, val count: Int)

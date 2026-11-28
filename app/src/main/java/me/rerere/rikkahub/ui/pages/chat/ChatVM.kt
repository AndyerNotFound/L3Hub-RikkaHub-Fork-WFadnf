package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.ChatRequestPreview
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.data.model.Guidance
import me.rerere.rikkahub.data.model.GuidanceMode
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.NodeFavoriteTarget
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.WorkspaceBinding
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.model.withWorkspaceBinding
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.service.BackgroundShellManager
import me.rerere.rikkahub.service.BackgroundShellTask
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.InboxState
import me.rerere.rikkahub.service.SessionActivity
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.web.ApiException
import java.util.Locale
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ChatVM"

class ChatVM(
    id: String,
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val chatService: ChatService,
    val updateChecker: UpdateChecker,
    private val filesManager: FilesManager,
    private val favoriteRepository: FavoriteRepository,
    private val folderRepository: FolderRepository,
    private val backgroundShellManager: BackgroundShellManager,
) : ViewModel() {
    /**
     * 当前会话 id。
     *
     * 以前是 `val`，跟着 ViewModel 一辈子。改成可切换的之后，同一个 ChatPage 能换会话，
     * 顶栏、输入框、悬浮栏都不用重建——只有消息区跟着这个值换内容。
     *
     * 下面所有按会话取的流都用 [flatMapLatest] 跟着它走：切换时会退订旧会话的流、
     * 订阅新的，中间不会有两份同时活着。
     */
    private val _conversationId = MutableStateFlow(Uuid.parse(id))
    val conversationId: StateFlow<Uuid> = _conversationId.asStateFlow()

    val conversation: StateFlow<Conversation> = _conversationId
        .flatMapLatest { chatService.getConversationFlow(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            chatService.getConversationFlow(_conversationId.value).value,
        )
    var chatListInitialized by mutableStateOf(false) // 聊天列表是否已经滚动到底部

    /**
     * 列表动作与会话页共用同一份实现（见 ConversationListActions）。
     * 侧边栏整页化之后它自己也要一份，所以实现不能只挂在 ChatVM 上。
     */
    private val listActions = ConversationListActions(
        settingsStore = settingsStore,
        chatService = chatService,
        scope = viewModelScope,
    )


    // 聊天输入状态 - 保存在 ViewModel 中避免 TransactionTooLargeException
    val inputState = ChatInputState()

    val voiceSession = VoiceSessionController(viewModelScope, context::getString) {
        chatService.enqueueVoiceMessage(_conversationId.value, it)
    }

    // 异步任务 (从ChatService获取，响应式)
    // 这三个都跟着 _conversationId 走：换会话时退订旧的、订阅新的
    val conversationJob: StateFlow<Job?> =
        _conversationId
            .flatMapLatest { chatService.getGenerationJobStateFlow(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sessionActivity: StateFlow<SessionActivity?> =
        _conversationId
            .flatMapLatest { chatService.getActivityFlow(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val processingStatus: StateFlow<String?> =
        _conversationId
            .flatMapLatest { chatService.getProcessingStatusFlow(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 会话列表的「谁在生成」不在这里：侧边栏整页化之后它不需要会话 VM，
    // 那份流搬到了 ConversationListActions（见那个文件）。

    init {
        // 添加对话引用
        chatService.addConversationReference(_conversationId.value)

        // 初始化对话
        viewModelScope.launch {
            chatService.initializeConversation(_conversationId.value)
        }

        // 记住对话ID, 方便下次启动恢复
        context.writeStringPreference("lastConversationId", _conversationId.value.toString())

        // 换会话时把引用和初始化补上：旧会话的引用要还回去，
        // 否则 ChatService 那边会一直以为它还在前台开着
        viewModelScope.launch {
            _conversationId.drop(1).collect { id ->
                chatService.initializeConversation(id)
                context.writeStringPreference("lastConversationId", id.toString())
            }
        }
    }

    /**
     * 切到另一条会话。
     *
     * 不重建页面：顶栏、输入框、悬浮栏都是同一个实例，只有消息区跟着 [conversationId] 换。
     * 旧会话的引用在这里还掉，新的补上；输入框的草稿和编辑态一起清掉——
     * 那是上一条会话的内容，带到新会话里去是错的。
     */
    fun switchConversation(id: Uuid) {
        if (id == _conversationId.value) return
        val previous = _conversationId.value
        // 先清输入态再换：切换动画里卡片不该还挂着上一条会话的草稿
        inputState.clearText()
        inputState.editingMessage = null
        chatListInitialized = false
        _conversationId.value = id
        chatService.addConversationReference(id)
        chatService.removeConversationReference(previous)
    }

    override fun onCleared() {
        voiceSession.stop()
        super.onCleared()
        // 移除对话引用
        chatService.removeConversationReference(_conversationId.value)
    }

    // 用户设置
    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    // 流式输出时会话每个 chunk 都在变，助手和模型只取决于下面这几个字段
    private val configuredConversation = conversation.distinctUntilChanged { old, new ->
        old.assistantId == new.assistantId && old.config == new.config &&
            old.modeInjectionIds == new.modeInjectionIds && old.lorebookIds == new.lorebookIds
    }

    // 会话所在文件夹绑定的工作区。文件夹是分组的单位：换绑、移进移出都要立刻反映到界面上。
    // 子代理会话自己没有分组，跟父会话走，父链要现查一次（这里不像普通会话那样有现成的 Flow 可跟）
    val folderWorkspaceBinding: StateFlow<WorkspaceBinding> = conversation
        .map { it.folderId to it.parentConversationId }
        .distinctUntilChanged()
        .flatMapLatest { (folderId, parentId) ->
            if (parentId == null) {
                folderRepository.observeWorkspaceBinding(folderId)
            } else {
                flow { emit(folderRepository.workspaceBindingOf(folderId, parentId)) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WorkspaceBinding.None)

    // 会话视角下的助手：会话开始后，模型、思考级别、搜索等以会话上固定的配置为准；
    // 文件夹绑了工作区时，那一组盖在会话自己的绑定上面
    val assistant: StateFlow<Assistant> = combine(
        settings,
        configuredConversation,
        folderWorkspaceBinding,
    ) { settings, conversation, binding ->
        settings.getAssistantOf(conversation).withWorkspaceBinding(binding)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        settings.value.getAssistantOf(conversation.value),
    )

    // 当前模型
    val currentChatModel: StateFlow<Model?> = combine(settings, configuredConversation) { settings, conversation ->
        settings.getChatModelOf(conversation)
    }.stateIn(viewModelScope, SharingStarted.Lazily, settings.value.getChatModelOf(conversation.value))

    // 错误状态
    val errors: StateFlow<List<ChatError>> = chatService.errors

    fun dismissError(id: Uuid) = chatService.dismissError(id)

    fun clearAllErrors() = chatService.clearAllErrors()

    // 收件箱跟着会话走；这里落成 StateFlow，界面侧可以直接读 .value、
    // 也能用不带 initialValue 的 collectAsStateWithLifecycle
    val messageQueue: StateFlow<InboxState> = _conversationId
        .flatMapLatest { chatService.getInboxFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxState())

    fun removeQueuedMessage(id: Uuid) = chatService.removeInboxItem(_conversationId.value, id)

    fun beginEditQueuedMessage(id: Uuid) = chatService.beginEditQueuedMessage(_conversationId.value, id)

    fun finishEditQueuedMessage(id: Uuid, parts: List<UIMessagePart>?) =
        chatService.finishEditQueuedMessage(_conversationId.value, id, parts)

    fun resumeMessageQueue() = chatService.resumeInbox(_conversationId.value)

    // 这个会话启动的后台命令，按启动时间排序；换会话时跟着换
    val backgroundTasks: StateFlow<List<BackgroundShellTask>> =
        combine(backgroundShellManager.tasks, _conversationId) { tasks, conversationId ->
            tasks.values.filter { it.conversationId == conversationId }.sortedBy { it.startedAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun stopBackgroundTask(id: String) = backgroundShellManager.stop(id)

    /**
     * 清单的手动编辑入口：用户在任务卡片上点完成、加一条、取消一条时走这里。
     * 和 todo_write 共用 ChatService.updateTodos 这一个写入口，内存和库里只有一份真值。
     */
    fun updateTodos(todos: List<TodoItem>) {
        // keepOwner：手改的清单不属于任何一条消息，别把归属挪到最新节点上，
        // 否则重新生成那条消息会连清单一起清掉
        viewModelScope.launch { chatService.updateTodos(_conversationId.value, todos, keepOwner = true) }
    }

    // 生成完成
    val generationDoneFlow: SharedFlow<Uuid> = chatService.generationDoneFlow

    // MCP管理器
    val mcpManager = chatService.mcpManager

    // 更新设置
    fun updateSettings(fn: (Settings) -> Settings): Job {
        return viewModelScope.launch {
            var oldSettings: Settings? = null
            var newSettings: Settings? = null
            settingsStore.update { old ->
                fn(old).also { new ->
                    oldSettings = old
                    newSettings = new
                }
            }
            // 检查用户头像是否有变化，如果有则删除旧头像
            checkUserAvatarDelete(oldSettings ?: return@launch, newSettings ?: return@launch)
        }
    }

    // 检查用户头像删除
    private fun checkUserAvatarDelete(oldSettings: Settings, newSettings: Settings) {
        val oldAvatar = oldSettings.displaySetting.userAvatar
        val newAvatar = newSettings.displaySetting.userAvatar

        val oldUrl = oldAvatar.mediaUrl
        if (oldUrl != null && oldUrl != newAvatar.mediaUrl) {
            filesManager.deleteChatFiles(listOf(oldUrl.toUri()))
        }
    }

    // 聊天页对助手的修改：新会话直接改助手，会话开始后只改当前会话
    fun updateAssistant(assistant: Assistant) {
        viewModelScope.launch {
            chatService.updateChatAssistant(_conversationId.value) { assistant }
        }
    }

    // 会话所在助手的文件夹，顶栏胶囊的选单用。文件夹属于助手，子代理会话或从别的助手打开的会话
    // 不能拿抽屉里当前助手的清单
    val folders: StateFlow<List<Folder>> = conversation
        .map { it.assistantId }
        .distinctUntilChanged()
        .flatMapLatest { assistantId -> folderRepository.getFoldersOfAssistant(assistantId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 顶栏胶囊里改会话文件夹：还没落库的新会话只改内存，首次落库时一并写入；已落库的会话只改这一列
    fun updateFolder(folderId: Uuid?) {
        if (conversation.value.folderId == folderId) return
        viewModelScope.launch {
            chatService.updateConversationFolder(_conversationId.value, folderId)
        }
    }

    // 顶栏文件夹选单里新建文件夹，建好直接把当前会话放进去
    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val folder = folderRepository.createFolder(conversation.value.assistantId, trimmed)
            chatService.updateConversationFolder(_conversationId.value, folder.id)
        }
    }

    // 设置聊天模型
    fun setChatModel(model: Model) {
        viewModelScope.launch {
            chatService.updateChatAssistant(_conversationId.value) { it.copy(chatModelId = model.id) }
        }
    }

    // 切换搜索方式
    fun updateSearch(enableWebSearch: Boolean, builtInSearch: Boolean) {
        viewModelScope.launch {
            chatService.updateChatSearch(_conversationId.value, enableWebSearch, builtInSearch)
        }
    }

    // Update checker
    val updateState = settingsStore.settingsFlow
        .map { settings ->
            BuildConfig.UPDATE_CHECK_ENABLED &&
                !BuildConfig.IS_PLAY_STORE &&
                !settings.init &&
                settings.displaySetting.updateCheckDisabledUntilEpochMillis <= System.currentTimeMillis()
        }
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (enabled) updateChecker.updateState else flowOf(UiState.Loading)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            UiState.Loading,
        )

    /**
     * 处理消息发送
     *
     * @param content 消息内容
     * @param answer 是否触发消息生成，如果为false，则仅添加消息到消息列表中
     * @return 是否已被受理；false 时调用方应把内容留在输入框里（关闭消息队列后正在压缩上下文、
     * 或者队列暂停还压着旧消息时会拒绝，不再退化成排队）
     */
    fun handleMessageSend(
        content: List<UIMessagePart>,
        answer: Boolean = true,
        interject: Boolean = false,
    ): Boolean {
        if (content.isEmptyInputMessage()) return false

        return chatService.sendMessage(_conversationId.value, content, answer, interject)
    }

    /** 加入一条引导；预设用多轮，手输的用单轮，卡片上还能再切 */
    fun addGuidance(title: String, content: String, mode: GuidanceMode) {
        chatService.addGuidance(
            conversationId = _conversationId.value,
            content = content,
            title = title,
            mode = mode,
        )
    }

    fun toggleGuidanceMode(guidance: Guidance) {
        chatService.setGuidanceMode(
            conversationId = _conversationId.value,
            guidanceId = guidance.id,
            mode = if (guidance.mode == GuidanceMode.SINGLE) GuidanceMode.REPEAT else GuidanceMode.SINGLE,
        )
    }

    fun removeGuidance(guidance: Guidance) {
        chatService.removeGuidance(_conversationId.value, guidance.id)
    }

    fun handleMessageEdit(parts: List<UIMessagePart>, messageId: Uuid) {
        if (parts.isEmptyInputMessage()) return

        viewModelScope.launch {
            chatService.editMessage(_conversationId.value, messageId, parts)
        }
    }

    fun handleCompressContext(additionalPrompt: String, keepRecentMessages: Int) {
        viewModelScope.launch {
            chatService.compressConversation(_conversationId.value, additionalPrompt, keepRecentMessages).onFailure {
                chatService.addError(
                    it,
                    _conversationId.value,
                    title = context.getString(R.string.error_title_compress_conversation)
                )
            }
        }
    }

    suspend fun forkMessage(message: UIMessage): Conversation {
        return chatService.forkConversationAtMessage(_conversationId.value, message.id)
    }

    fun deleteMessage(message: UIMessage) {
        viewModelScope.launch {
            chatService.deleteMessage(_conversationId.value, message)
        }
    }

    fun showDeleteBlockedWhileGeneratingError() {
        chatService.addError(
            error = IllegalStateException(context.getString(R.string.chat_page_stop_generating_to_delete)),
            conversationId = _conversationId.value,
            title = context.getString(R.string.error_title_operation)
        )
    }

    fun regenerateAtMessage(message: UIMessage) {
        chatService.regenerateAtMessage(_conversationId.value, message.id)
    }

    fun handleToolApproval(
        toolCallId: String,
        approved: Boolean,
        reason: String = ""
    ) {
        chatService.handleToolApproval(_conversationId.value, toolCallId, approved, reason)
    }

    fun handleToolAnswer(
        toolCallId: String,
        answer: String,
    ) {
        chatService.handleToolApproval(_conversationId.value, toolCallId, approved = true, answer = answer)
    }

    fun stopGeneration() {
        viewModelScope.launch {
            chatService.stopGeneration(_conversationId.value)
        }
    }

    fun updateTitle(title: String) {
        listActions.updateTitle(_conversationId.value, title)
    }

    fun updateTitle(conversationId: Uuid, title: String) {
        listActions.updateTitle(conversationId, title)
    }

    fun deleteConversation(conversation: Conversation): Job =
        listActions.deleteConversation(conversation)

    fun updatePinnedStatus(conversation: Conversation) {
        listActions.updatePinnedStatus(conversation)
    }

    fun moveConversationToAssistant(conversation: Conversation, targetAssistantId: Uuid) {
        listActions.moveConversationToAssistant(
            conversation = conversation,
            targetAssistantId = targetAssistantId,
            // 被挪的正好是当前会话时，把当前助手也切过去（原来就有的行为）
            currentConversationId = _conversationId.value,
        )
    }

    fun translateMessage(message: UIMessage, targetLanguage: Locale) {
        chatService.translateMessage(_conversationId.value, message, targetLanguage)
    }

    fun generateTitle(conversation: Conversation, force: Boolean = false) {
        listActions.generateTitle(conversation, force)
    }

    fun clearTranslationField(messageId: Uuid) {
        viewModelScope.launch {
            chatService.clearTranslationField(_conversationId.value, messageId)
        }
    }

    fun selectMessageNode(nodeId: Uuid, selectIndex: Int) {
        viewModelScope.launch {
            try {
                chatService.selectMessageNode(_conversationId.value, nodeId, selectIndex)
            } catch (e: ApiException) {
                // 界面上的分支已经过时（比如刚被清理掉），切换不了就算了
                Log.w(TAG, "selectMessageNode: ${e.message}")
            }
        }
    }

    fun updateConversationSystemPrompt(systemPrompt: String?) {
        viewModelScope.launch {
            chatService.updateConversationSystemPrompt(_conversationId.value, systemPrompt)
        }
    }

    /** 会话详情打开时读一次模型视角的请求快照；工具和 MCP 列表可能要等一会儿，由 ChatService 放到 IO 线程 */
    suspend fun buildRequestPreview(): ChatRequestPreview =
        chatService.buildRequestPreview(_conversationId.value)

    fun updateWorkspaceCwd(workspaceId: Uuid, workspaceCwd: String?) {
        viewModelScope.launch {
            chatService.updateWorkspaceCwd(_conversationId.value, workspaceId, workspaceCwd)
        }
    }

    fun toggleMessageFavorite(node: MessageNode) {
        viewModelScope.launch {
            val currentlyFavorited = favoriteRepository.isNodeFavorited(_conversationId.value, node.id)
            if (currentlyFavorited) {
                favoriteRepository.removeNodeFavorite(_conversationId.value, node.id)
            } else {
                favoriteRepository.addNodeFavorite(
                    NodeFavoriteTarget(
                        conversationId = _conversationId.value,
                        conversationTitle = conversation.value.title,
                        nodeId = node.id,
                        node = node
                    )
                )
            }

            chatService.setNodeFavorite(_conversationId.value, node.id, favorite = !currentlyFavorited)
        }
    }

}

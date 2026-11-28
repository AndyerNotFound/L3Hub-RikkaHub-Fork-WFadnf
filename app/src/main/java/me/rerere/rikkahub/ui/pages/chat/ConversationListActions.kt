package me.rerere.rikkahub.ui.pages.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.ChatService
import kotlin.uuid.Uuid

/**
 * 会话列表的读写动作，只有一份实现。
 *
 * 这些动作只认「某一条会话」，不认「当前打开的是哪一条」——侧边栏整页化之后，
 * 它已经不再天然属于某个 [ChatVM]（那一页可能根本没有打开的会话）。
 *
 * 从 [ChatVM] 抽出来的判据是「方法体是否只用到传进来的会话」：
 * [updateTitle]、[deleteConversation]、[updatePinnedStatus]、
 * [moveConversationToAssistant]、[generateTitle] 全部只吃参数；
 * [conversationJobs] 是 ChatService 的全局流，本来就跟某个会话无关。
 *
 * 做成普通类而不是 ViewModel，是因为 [ChatVM] 和 [ConversationListVM] 都要用它：
 * Koin 里让一个 viewModel 定义 `get()` 另一个 viewModel 会拿到一个不挂在
 * ViewModelStore 上的孤儿实例，onCleared 不会触发。两边各自 new 一个、共用同一份实现，
 * 而底层 [chatService] 本来就是单例，没有状态被复制。
 *
 * @param scope 用宿主的 viewModelScope，宿主清掉时这些协程一起取消
 */
class ConversationListActions(
    private val settingsStore: SettingsStore,
    private val chatService: ChatService,
    private val scope: CoroutineScope,
) {

    /** 所有会话各自有没有在生成，侧边栏的转圈标记读它 */
    val conversationJobs: StateFlow<Map<Uuid, Job?>> = chatService
        .getConversationJobs()
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun updateTitle(conversationId: Uuid, title: String) {
        scope.launch {
            chatService.updateTitle(conversationId, title)
        }
    }

    fun deleteConversation(conversation: Conversation): Job =
        scope.launch {
            chatService.deleteConversation(conversation.id)
        }

    fun updatePinnedStatus(conversation: Conversation) {
        scope.launch {
            chatService.toggleConversationPinned(conversation.id)
        }
    }

    /**
     * 把会话挪到别的助手。
     *
     * @param currentConversationId 当前打开的会话。被挪的正好是它时，顺手把当前助手切过去——
     *   这是原来 [ChatVM] 里的行为，会话页和侧边栏都得保留，所以从参数进来而不是读字段。
     */
    fun moveConversationToAssistant(
        conversation: Conversation,
        targetAssistantId: Uuid,
        currentConversationId: Uuid? = null,
    ) {
        scope.launch {
            chatService.moveConversationToAssistant(conversation.id, targetAssistantId)
            if (currentConversationId != null && conversation.id == currentConversationId) {
                settingsStore.selectAssistant(targetAssistantId)
            }
        }
    }

    fun generateTitle(conversation: Conversation, force: Boolean = false) {
        scope.launch {
            chatService.generateTitle(conversation.id, force)
        }
    }

    /** 改用户设置；侧边栏里只改排序和当前助手 */
    fun updateSettings(fn: (Settings) -> Settings): Job =
        scope.launch {
            settingsStore.update(fn)
        }
}

/**
 * 侧边栏那一页自己的 ViewModel。
 *
 * 它只需要列表动作，不需要任何会话状态——这正是整页化之后要的东西。
 * 所有实现都在 [ConversationListActions]，这里只是把它绑到自己的 viewModelScope 上。
 */
class ConversationListVM(
    settingsStore: SettingsStore,
    chatService: ChatService,
) : ViewModel() {

    private val actions = ConversationListActions(
        settingsStore = settingsStore,
        chatService = chatService,
        scope = viewModelScope,
    )

    val conversationJobs = actions.conversationJobs

    fun updateTitle(conversationId: Uuid, title: String) =
        actions.updateTitle(conversationId, title)

    fun deleteConversation(conversation: Conversation): Job =
        actions.deleteConversation(conversation)

    fun updatePinnedStatus(conversation: Conversation) =
        actions.updatePinnedStatus(conversation)

    fun moveConversationToAssistant(
        conversation: Conversation,
        targetAssistantId: Uuid,
        currentConversationId: Uuid? = null,
    ) = actions.moveConversationToAssistant(conversation, targetAssistantId, currentConversationId)

    fun generateTitle(conversation: Conversation, force: Boolean = false) =
        actions.generateTitle(conversation, force)

    fun updateSettings(fn: (Settings) -> Settings): Job =
        actions.updateSettings(fn)
}

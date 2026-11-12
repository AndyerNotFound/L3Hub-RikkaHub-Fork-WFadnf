package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.MemoryConsolidationScheduler
import me.rerere.rikkahub.data.ai.MemoryConsolidationStatus
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.repository.memoryId
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.hooks.readStringSetPreference
import me.rerere.rikkahub.ui.hooks.writeStringSetPreference
import me.rerere.rikkahub.utils.toLocalString
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.uuid.Uuid

class ChatDrawerVM(
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val folderRepo: FolderRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val chatService: ChatService,
    private val memoryConsolidationScheduler: MemoryConsolidationScheduler,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val assistantIdFlow = settingsStore.settingsFlow
        .map { it.assistantId }
        .distinctUntilChanged()

    private val sortOrderFlow = settingsStore.settingsFlow
        .map { it.displaySetting.conversationSortOrder }
        .distinctUntilChanged()

    // 文件夹绑定的写操作串行化，见 updateFolderWorkspaces
    private val folderWriteMutex = Mutex()

    // 收起的日期分组。停在 SharedPreferences 里，而不是 SavedStateHandle：
    // 后者只活到这一份 activity 实例，换主题重进、从最近任务里恢复、冷启动都会回到展开态。
    // 折叠是用户自己收的，任何一次刷新都不该替他拉开
    private val _collapsedDates = MutableStateFlow(
        context.readStringSetPreference(KEY_COLLAPSED_DATES)
            .mapNotNullTo(mutableSetOf<LocalDate>()) { raw ->
                raw.toLongOrNull()?.let { LocalDate.ofEpochDay(it) }
            }
            .toSet()
    )
    val collapsedDates: StateFlow<Set<LocalDate>> = _collapsedDates.asStateFlow()

    // 收起的文件夹段。竖排排序下文件夹头可点，收起后只剩标题；与日期分组同一份存储
    private val _collapsedFolders = MutableStateFlow(
        context.readStringSetPreference(KEY_COLLAPSED_FOLDERS)
    )
    val collapsedFolders: StateFlow<Set<String>> = _collapsedFolders.asStateFlow()

    // 当前助手的文件夹列表（Room Flow，增删改自动刷新）。
    // 顺序由内容决定，不看手工排列：按分组里最新一条会话的时间倒序，
    // 没有会话的分组沉到最后，彼此之间保持建立顺序（sortedWith 是稳定排序）
    val folders: StateFlow<List<Folder>> = assistantIdFlow
        .flatMapLatest { assistantId ->
            combine(
                folderRepo.getFoldersOfAssistant(assistantId),
                folderRepo.getFolderLatestActivity(assistantId),
            ) { list, latestAt ->
                list.sortedWith(
                    compareByDescending<Folder> { latestAt[it.id] ?: Instant.MIN },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 文件夹能绑定的工作区
    val workspaces: StateFlow<List<WorkspaceEntity>> = workspaceRepository.listFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前助手的记忆库正在后台整理时的进度，没在整理时为 null；
    // 没开记忆的助手即使共用的全局记忆库在整理也不显示
    val memoryDreaming: StateFlow<MemoryConsolidationStatus?> = settingsStore.settingsFlow
        .map { settings -> settings.getCurrentAssistant().takeIf { it.enableMemory }?.memoryId }
        .distinctUntilChanged()
        .flatMapLatest { memoryId ->
            if (memoryId == null) {
                flowOf<MemoryConsolidationStatus?>(null)
            } else {
                memoryConsolidationScheduler.statusOf(memoryId).map { it.takeIf { status -> status.running } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 所有会话按文件夹归堆，再依次插文件夹头、置顶头和周标题
    private val lightConversations: Flow<List<Conversation>> =
        combine(assistantIdFlow, sortOrderFlow) { assistantId, sortOrder -> assistantId to sortOrder }
            .flatMapLatest { (assistantId, sortOrder) ->
                conversationRepo.getLightConversationsOfAssistant(assistantId, sortOrder)
            }

    val verticalItems: StateFlow<List<ConversationListItem>> =
        combine(assistantIdFlow, sortOrderFlow, folders, lightConversations) { assistantId, sortOrder, folderList, conversations ->
            buildVerticalItems(
                folders = folderList,
                conversations = conversations.filter { it.assistantId == assistantId },
                sortOrder = sortOrder,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun buildVerticalItems(
        folders: List<Folder>,
        conversations: List<Conversation>,
        sortOrder: ConversationSortOrder,
    ): List<ConversationListItem> {
        val byFolder = conversations.groupBy { it.folderId }
        val items = mutableListOf<ConversationListItem>()

        fun appendSection(folder: Folder?, conversationsOfFolder: List<Conversation>) {
            items += ConversationListItem.FolderHeader(
                folderId = folder?.id,
                label = folder?.name ?: context.getString(R.string.chat_page_folder_default),
            )
            var pinnedHeaderAdded = false
            var lastWeek: LocalDate? = null
            conversationsOfFolder.forEach { conversation ->
                if (conversation.isPinned) {
                    if (!pinnedHeaderAdded) {
                        items += ConversationListItem.PinnedHeader
                        pinnedHeaderAdded = true
                    }
                } else {
                    val week = conversation.sortDate(sortOrder).weekStart()
                    if (week != lastWeek) {
                        items += ConversationListItem.DateHeader(
                            date = week,
                            label = getWeekLabel(week),
                            sortOrder = sortOrder,
                        )
                        lastWeek = week
                    }
                }
                items += ConversationListItem.Item(conversation)
            }
        }

        // 「未归类」排在最前，其余按文件夹自己的顺序；空文件夹也留下标题，拖拽排序时看得见落点
        val unfiled = byFolder[null].orEmpty()
        if (unfiled.isNotEmpty()) appendSection(null, unfiled)
        folders.forEach { folder -> appendSection(folder, byFolder[folder.id].orEmpty()) }
        return items
    }

    val verticalScrollIndex: Int get() = savedStateHandle[KEY_VERTICAL_SCROLL_INDEX] ?: 0
    val verticalScrollOffset: Int get() = savedStateHandle[KEY_VERTICAL_SCROLL_OFFSET] ?: 0

    fun saveVerticalScrollPosition(index: Int, offset: Int) {
        savedStateHandle[KEY_VERTICAL_SCROLL_INDEX] = index
        savedStateHandle[KEY_VERTICAL_SCROLL_OFFSET] = offset
    }

    /** 换排序方式后旧下标不再适用，回到顶部 */
    fun resetScrollPositions() {
        savedStateHandle.keys()
            .filter { key -> key.startsWith("verticalScroll") }
            .forEach { key -> savedStateHandle.remove<Any>(key) }
    }

    fun toggleDateCollapsed(date: LocalDate) {
        _collapsedDates.update { dates ->
            if (date in dates) dates - date else dates + date
        }
        persistCollapsedDates()
    }

    private fun persistCollapsedDates() {
        context.writeStringSetPreference(
            KEY_COLLAPSED_DATES,
            _collapsedDates.value.mapTo(mutableSetOf<String>()) { it.toEpochDay().toString() },
        )
    }

    fun toggleFolderCollapsed(folderId: Uuid?) {
        val key = folderCollapseKey(folderId)
        _collapsedFolders.update { keys -> if (key in keys) keys - key else keys + key }
        persistCollapsedFolders()
    }

    private fun persistCollapsedFolders() {
        context.writeStringSetPreference(KEY_COLLAPSED_FOLDERS, _collapsedFolders.value)
    }

    /**
     * 改文件夹绑定的工作区：这个分组里的会话都改用这组工作区。
     *
     * 只改文件夹这一处，会话各自固定的绑定保持原样——移出分组就回到自己的工作区。
     */
    fun updateFolderWorkspaces(folderId: Uuid, workspaceIds: Set<Uuid>) {
        viewModelScope.launch {
            // 连点几下会起好几个协程，整组覆盖的 UPDATE 之间没有顺序保证，串起来才不会丢一次勾选
            folderWriteMutex.withLock {
                val current = folders.value.firstOrNull { it.id == folderId }
                folderRepo.updateFolderWorkspaces(
                    id = folderId,
                    primary = current?.primaryWorkspaceId ?: workspaceIds.firstOrNull(),
                    workspaceIds = workspaceIds,
                )
            }
        }
    }

    fun renameFolder(folderId: Uuid, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            folderRepo.renameFolder(folderId, trimmed)
        }
    }

    /**
     * 删除文件夹。若文件夹内有正在生成回复的会话，拒绝删除并返回 false（UI 层据此提示用户）。
     */
    fun deleteFolder(folderId: Uuid): Boolean {
        if (chatService.hasGeneratingConversationInFolder(folderId)) {
            return false
        }
        viewModelScope.launch {
            // 经 ChatService 删除：会同步清空活跃 session 内存态的 folderId，避免整对象保存写回已删文件夹
            chatService.deleteFolder(folderId)
        }
        return true
    }

    fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        viewModelScope.launch {
            // 经 ChatService 移动：活跃会话会先同步内存态，避免后续整对象保存覆盖 folder_id
            chatService.moveConversationToFolder(conversationId, folderId)
        }
    }

    private fun Conversation.sortDate(sortOrder: ConversationSortOrder): LocalDate {
        val instant = when (sortOrder) {
            ConversationSortOrder.UPDATE_TIME -> updateAt
            ConversationSortOrder.CREATE_TIME -> createAt
        }
        return instant.atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun LocalDate.weekStart(): LocalDate =
        with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private companion object {
        // 这两个是 SharedPreferences 的键，不再是 SavedStateHandle 的
        const val KEY_COLLAPSED_DATES = "drawer_collapsed_dates"
        const val KEY_COLLAPSED_FOLDERS = "drawer_collapsed_folders"
        const val KEY_VERTICAL_SCROLL_INDEX = "verticalScrollIndex"
        const val KEY_VERTICAL_SCROLL_OFFSET = "verticalScrollOffset"
    }

    private fun getWeekLabel(start: LocalDate): String {
        val thisWeek = LocalDate.now().weekStart()
        return when (start) {
            thisWeek -> context.getString(R.string.chat_page_week_this)
            thisWeek.minusWeeks(1) -> context.getString(R.string.chat_page_week_last)
            else -> {
                val end = start.plusDays(6)
                val includeYear = start.year != thisWeek.year
                context.getString(
                    R.string.chat_page_week_range,
                    start.toLocalString(includeYear),
                    end.toLocalString(includeYear),
                )
            }
        }
    }
}

/** 文件夹折叠状态的键；folderId 为 null 时表示「未归类」那一段 */
internal fun folderCollapseKey(folderId: Uuid?): String = folderId?.toString() ?: "unfiled"

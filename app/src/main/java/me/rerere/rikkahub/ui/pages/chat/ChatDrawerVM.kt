package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
import me.rerere.rikkahub.utils.toLocalString
import java.time.DayOfWeek
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

    // 当前选中的文件夹筛选，null 表示「未归类」视图
    private val _selectedFolderId = MutableStateFlow<Uuid?>(null)
    val selectedFolderId: StateFlow<Uuid?> = _selectedFolderId.asStateFlow()

    // 收起的日期分组。放在 activity 级 VM 里，切换会话、关闭侧栏后不会被重置；
    // 同时写进 SavedStateHandle，进程重建后和滚动位置保持同一份生命周期
    private val _collapsedDates = MutableStateFlow(
        savedStateHandle.get<LongArray>(KEY_COLLAPSED_DATES)
            ?.map { LocalDate.ofEpochDay(it) }
            ?.toSet()
            .orEmpty()
    )
    val collapsedDates: StateFlow<Set<LocalDate>> = _collapsedDates.asStateFlow()

    // 当前助手的文件夹列表（Room Flow，增删改自动刷新）
    val folders: StateFlow<List<Folder>> = assistantIdFlow
        .flatMapLatest { folderRepo.getFoldersOfAssistant(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 文件夹能绑定的工作区
    val workspaces: StateFlow<List<WorkspaceEntity>> = workspaceRepository.listFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前助手的记忆库是否正在后台整理；没开记忆的助手即使共用的全局记忆库在整理也不显示
    val memoryDreaming: StateFlow<Boolean> = settingsStore.settingsFlow
        .map { settings -> settings.getCurrentAssistant().takeIf { it.enableMemory }?.memoryId }
        .distinctUntilChanged()
        .flatMapLatest { memoryId ->
            if (memoryId == null) flowOf(false) else memoryConsolidationScheduler.statusOf(memoryId).map { it.running }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * 某个文件夹视图的会话分页。
     *
     * 每个文件夹单独取一份：切换文件夹时两份列表要同时在组合里做横向转场，
     * 共用一条只跟着选中文件夹走的分页流，旧视图会拿到新视图的数据。
     */
    fun conversationsOf(folderId: Uuid?): Flow<PagingData<ConversationListItem>> =
        combine(assistantIdFlow, sortOrderFlow) { assistantId, sortOrder -> assistantId to sortOrder }
            .flatMapLatest { (assistantId, sortOrder) ->
                val paging = if (folderId == null) {
                    conversationRepo.getUnfiledConversationsOfAssistantPaging(assistantId, sortOrder)
                } else {
                    conversationRepo.getConversationsOfFolderPaging(assistantId, folderId, sortOrder)
                }
                paging.map { pagingData -> pagingData.withWeekHeaders(sortOrder) }
            }
            .cachedIn(viewModelScope)

    private fun PagingData<Conversation>.withWeekHeaders(
        sortOrder: ConversationSortOrder,
    ): PagingData<ConversationListItem> =
        map { ConversationListItem.Item(it) }
            .insertSeparators<ConversationListItem.Item, ConversationListItem> { before, after ->
                when {
                    after == null -> null
                    after.conversation.isPinned -> {
                        if (before == null) ConversationListItem.PinnedHeader else null
                    }

                    else -> {
                        // 按周分组（周一开始），减少列表里过密的日期标题
                        val afterWeek = after.conversation.sortDate(sortOrder).weekStart()
                        val sameGroup = before != null &&
                            !before.conversation.isPinned &&
                            before.conversation.sortDate(sortOrder).weekStart() == afterWeek
                        if (sameGroup) {
                            null
                        } else {
                            ConversationListItem.DateHeader(
                                date = afterWeek,
                                label = getWeekLabel(afterWeek),
                                sortOrder = sortOrder,
                            )
                        }
                    }
                }
            }

    private val verticalSortingFlow = settingsStore.settingsFlow
        .map { it.displaySetting.drawerVerticalSorting }
        .distinctUntilChanged()

    // 竖向排序：所有会话按文件夹归堆，再依次插文件夹头、置顶头和周标题
    // 开关关掉时不查这份全量清单，免得只为扁平视图白读一遍数据库
    private val lightConversations: Flow<List<Conversation>> =
        combine(verticalSortingFlow, assistantIdFlow, sortOrderFlow) { vertical, assistantId, sortOrder ->
            Triple(vertical, assistantId, sortOrder)
        }.flatMapLatest { (vertical, assistantId, sortOrder) ->
            if (vertical) {
                conversationRepo.getLightConversationsOfAssistant(assistantId, sortOrder)
            } else {
                flowOf(emptyList())
            }
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

    // 拖动排序是连发的，Room 回流慢一拍：以最近一次写下去的顺序为基准，
    // 否则后一次会拿旧列表算位置，几次快速拖动就互相盖掉
    private var lastReorderedIds: List<Uuid>? = null

    /** 把文件夹拖到可见列表里的 [targetIndex]（0 起，不含「未归类」和「新建」） */
    fun moveFolderTo(folderId: Uuid, targetIndex: Int) {
        viewModelScope.launch {
            folderWriteMutex.withLock {
                val known = folders.value.map { it.id }
                val base = lastReorderedIds
                    ?.takeIf { ids -> ids.size == known.size && ids.toSet() == known.toSet() }
                    ?: known
                val ordered = base.toMutableList()
                val from = ordered.indexOf(folderId)
                if (from < 0) return@withLock
                ordered.removeAt(from)
                val to = targetIndex.coerceIn(0, ordered.size)
                ordered.add(to, folderId)
                if (ordered == base) return@withLock
                lastReorderedIds = ordered
                folderRepo.reorderFolders(ordered)
            }
        }
    }

    // 每个文件夹视图各自记住滚动位置；key 带 V2，早期版本存的是含 header 的绝对下标
    fun scrollIndex(folderId: Uuid?): Int = savedStateHandle[scrollIndexKey(folderId)] ?: 0

    fun scrollOffset(folderId: Uuid?): Int = savedStateHandle[scrollOffsetKey(folderId)] ?: 0

    fun saveScrollPosition(folderId: Uuid?, index: Int, offset: Int) {
        savedStateHandle[scrollIndexKey(folderId)] = index
        savedStateHandle[scrollOffsetKey(folderId)] = offset
    }

    val verticalScrollIndex: Int get() = savedStateHandle[KEY_VERTICAL_SCROLL_INDEX] ?: 0
    val verticalScrollOffset: Int get() = savedStateHandle[KEY_VERTICAL_SCROLL_OFFSET] ?: 0

    fun saveVerticalScrollPosition(index: Int, offset: Int) {
        savedStateHandle[KEY_VERTICAL_SCROLL_INDEX] = index
        savedStateHandle[KEY_VERTICAL_SCROLL_OFFSET] = offset
    }

    /** 换排序方式后旧下标不再适用，所有视图都回到顶部 */
    fun resetScrollPositions() {
        savedStateHandle.keys()
            .filter { key -> key.startsWith("scrollIndexV2/") || key.startsWith("scrollOffsetV2/") || key.startsWith("verticalScroll") }
            .forEach { key -> savedStateHandle.remove<Any>(key) }
    }

    private fun scrollIndexKey(folderId: Uuid?): String = "scrollIndexV2/${folderId ?: "unfiled"}"

    private fun scrollOffsetKey(folderId: Uuid?): String = "scrollOffsetV2/${folderId ?: "unfiled"}"

    init {
        // 助手切换时重置文件夹筛选，回到「聊天」视图，
        // 避免继续显示上一个助手文件夹内的会话（文件夹是助手内分组）
        viewModelScope.launch {
            assistantIdFlow.collect {
                _selectedFolderId.value = null
            }
        }
    }

    fun selectFolder(folderId: Uuid?) {
        _selectedFolderId.value = folderId
    }

    // 已经为哪些会话自动展开过所在分组。列表因数据刷新、切换文件夹重建时会重新跑定位逻辑，
    // 这里认领一次就够，避免把用户刚刚收起的组又拉开
    private val autoRevealedConversations = LinkedHashSet<Uuid>()

    /** 第一次处理某个会话时返回 true；之后（包括列表重建）都返回 false */
    fun claimAutoReveal(conversationId: Uuid): Boolean {
        if (!autoRevealedConversations.add(conversationId)) return false
        if (autoRevealedConversations.size > MAX_AUTO_REVEAL_MEMORY) {
            autoRevealedConversations.remove(autoRevealedConversations.first())
        }
        return true
    }

    fun toggleDateCollapsed(date: LocalDate) {
        _collapsedDates.update { dates ->
            if (date in dates) dates - date else dates + date
        }
        persistCollapsedDates()
    }

    /** 当前会话所在的周被收起时由列表回调，保证打开的会话不会藏在折叠组里。 */
    fun expandDate(date: LocalDate) {
        if (date !in _collapsedDates.value) return
        _collapsedDates.update { it - date }
        persistCollapsedDates()
    }

    private fun persistCollapsedDates() {
        savedStateHandle[KEY_COLLAPSED_DATES] =
            _collapsedDates.value.map { it.toEpochDay() }.toLongArray()
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val assistantId = assistantIdFlow.first()
            folderRepo.createFolder(assistantId, trimmed)
        }
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
            if (_selectedFolderId.value == folderId) {
                _selectedFolderId.value = null
            }
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
        const val KEY_COLLAPSED_DATES = "collapsedDates"
        const val KEY_VERTICAL_SCROLL_INDEX = "verticalScrollIndex"
        const val KEY_VERTICAL_SCROLL_OFFSET = "verticalScrollOffset"
        const val MAX_AUTO_REVEAL_MEMORY = 32
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

package me.rerere.rikkahub.ui.pages.chat

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.ImageToVideo
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sorting01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.MemoryConsolidationStatus
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.ui.components.ai.AssistantPicker
import me.rerere.rikkahub.ui.components.ai.WorkspaceSelectSheet
import me.rerere.rikkahub.ui.components.ui.BackupReminderCard
import me.rerere.rikkahub.ui.components.ui.Greeting
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.UpdateCard
import me.rerere.rikkahub.ui.components.ui.advancedMaterial
import me.rerere.rikkahub.ui.components.ui.glassColor
import me.rerere.rikkahub.ui.components.ui.navigationMaterial
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.readBooleanPreference
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceBoolean
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.modifier.MotionExpandCollapse
import me.rerere.rikkahub.ui.modifier.onClick
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.theme.adaptiveShape
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.utils.UpdateInfo
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.toDp
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.components.AppModalSheet
import me.rerere.ui.components.Tooltip
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

// 抽屉宽度：原来是写死的 300dp，按比例放大后还要跟屏幕宽度取小，免得窄屏上撑出屏幕
private val DRAWER_WIDTH_BASE = 300.dp
private const val DRAWER_WIDTH_SCALE = 1.2f

// 「新聊天」胶囊整体缩放的倍数，锚点在自己的左下角：左下角不动，向右上收缩
private const val NEW_CHAT_PILL_SCALE = 0.8f

// 底部用户头像（设置入口）整体缩放的倍数，锚点在自己的右下角
private const val DRAWER_AVATAR_SCALE = 0.8f

// 滚动停下后再写 SavedStateHandle，避免逐帧写
private const val SCROLL_POSITION_SAVE_DELAY_MS = 300L

// scrollIndex 存的是会话列表内的下标（不含 header 项），放回 LazyColumn 时要补上 header
private fun Int.toListItemIndex(headerCount: Int): Int =
    if (this <= 0) 0 else this + headerCount

@Composable
fun ChatDrawerContent(
    navController: Navigator,
    /**
     * 会话列表的读写动作。
     *
     * 早先这里是 `vm: ChatVM`，但抽屉只用到它那几个「按会话 id 操作」的方法；
     * 侧边栏整页化之后那一页可能根本没有打开的会话，所以改吃 [ConversationListVM]。
     */
    listVm: ConversationListVM,
    settings: Settings,
    /** 当前打开的会话，用于高亮；侧边栏整页里可能为 null（没有会话在下面） */
    currentConversationId: Uuid?,
    onNewChat: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    // 抽屉底板使用页面背景取样源；没有源或关闭高级外观时底板退回实色
    hazeState: HazeState? = null,
    // 横屏三栏的左栏宽度由列宽决定，不再按窗口比例算
    widthOverride: Dp? = null,
    /**
     * 整页形态：侧边栏作为历史里的一页时传 true。
     *
     * 抽屉形态的外壳是 [ModalDrawerSheet]（自带宽度、圆角、玻璃底板），整页形态下这些
     * 都由页面自己负责，套上去会变成"页面里又浮着一张卡片"。内容本身两边完全一样。
     */
    fullPage: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val repo = koinInject<ConversationRepository>()

    val activity = context as ComponentActivity
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = activity)

    val folders by drawerVm.folders.collectAsStateWithLifecycle()
    val bindableWorkspaces by drawerVm.workspaces.collectAsStateWithLifecycle()
    val memoryDreaming by drawerVm.memoryDreaming.collectAsStateWithLifecycle()
    val collapsedDates by drawerVm.collapsedDates.collectAsStateWithLifecycle()
    val collapsedFolders by drawerVm.collapsedFolders.collectAsStateWithLifecycle()
    val verticalItems by drawerVm.verticalItems.collectAsStateWithLifecycle()

    // 矮屏（如手表、横屏手机）上顶部固定会把会话列表挤没，改为跟随列表一起滚动
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val windowWidth = LocalWindowInfo.current.containerDpSize.width
    val drawerWidth = widthOverride ?: minOf(DRAWER_WIDTH_BASE * DRAWER_WIDTH_SCALE, windowWidth)
    val scrollableHeader = windowHeight < 640.dp
    // header 是否占掉 LazyColumn 的第 0 位，决定保存/恢复滚动位置时要不要补一位
    val listHeaderCount = if (scrollableHeader) 1 else 0

    val drawerSortOrder = settings.displaySetting.conversationSortOrder
    // 换排序方式时重建列表状态，回到顶部，不留在另一种排序下的位置
    val verticalListState = remember(drawerSortOrder) {
        LazyListState(
            firstVisibleItemIndex = drawerVm.verticalScrollIndex.toListItemIndex(listHeaderCount),
            firstVisibleItemScrollOffset = drawerVm.verticalScrollOffset,
        )
    }

    val conversationJobs by listVm.conversationJobs.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
    )

    // 昵称编辑状态
    val nicknameEditState = useEditState<String> { newNickname ->
        listVm.updateSettings {
            it.copy(
                displaySetting = it.displaySetting.copy(
                    userNickname = newNickname
                )
            )
        }
    }

    // 移动对话状态
    var showMoveToAssistantSheet by remember { mutableStateOf(false) }
    var conversationToMove by remember { mutableStateOf<Conversation?>(null) }

    // 文件夹相关状态
    var showMoveToFolderSheet by remember { mutableStateOf(false) }
    var conversationToMoveFolder by remember { mutableStateOf<Conversation?>(null) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }
    var folderToBindWorkspace by remember { mutableStateOf<Folder?>(null) }
    // 这次勾选的草稿：Room 回流要一个来回，草稿保证连续点几下不会互相盖掉
    var folderWorkspaceDraft by remember { mutableStateOf<Set<Uuid>>(emptySet()) }

    // 扁平与竖向两种列表共用同一批回调，省得两处各写一份后行为走样
    fun openConversation(conversation: Conversation) {
        // 直接换会话：新会话页带自己的抽屉状态，旧的随这一页一起退场
        navigateToChatPage(navController, conversation.id)
    }

    val onDeleteConversation: (Conversation) -> Unit = { conversation ->
        scope.launch {
            listVm.deleteConversation(conversation).join()
            if (conversation.id == currentConversationId) {
                navigateToChatPage(navController)
            }
        }
    }
    val onRenameConversation: (Conversation, String) -> Unit = { conversation, title ->
        listVm.updateTitle(conversation.id, title)
    }
    val onPinConversation: (Conversation) -> Unit = { listVm.updatePinnedStatus(it) }
    val onMoveConversationToAssistant: (Conversation) -> Unit = {
        conversationToMove = it
        showMoveToAssistantSheet = true
    }
    val onMoveConversationToFolder: (Conversation) -> Unit = {
        conversationToMoveFolder = it
        showMoveToFolderSheet = true
    }
    val onDeleteSelectedConversations: (List<Conversation>) -> Unit = { list ->
        scope.launch {
            list.forEach { listVm.deleteConversation(it).join() }
            if (list.any { it.id == currentConversationId }) {
                navigateToChatPage(navController)
            }
        }
    }
    val onPinSelectedConversations: (List<Conversation>) -> Unit = { list ->
        // 有未置顶的就全部置顶，否则全部取消置顶
        val target = list.any { !it.isPinned }
        list.filter { it.isPinned != target }.forEach { listVm.updatePinnedStatus(it) }
    }

    val updateCheckDisabledUntil = settings.displaySetting.updateCheckDisabledUntilEpochMillis
    var updateChecksEnabled by remember(updateCheckDisabledUntil) {
        mutableStateOf(updateCheckDisabledUntil <= System.currentTimeMillis())
    }
    LaunchedEffect(updateCheckDisabledUntil) {
        while (true) {
            val remaining = updateCheckDisabledUntil - System.currentTimeMillis()
            if (remaining <= 0) {
                updateChecksEnabled = true
                break
            }
            updateChecksEnabled = false
            delay(minOf(remaining, 60 * 60 * 1_000L))
        }
    }

    // 更新检查原先借 ChatVM 的 updateState；抽屉整页化后不再持有 ChatVM，
    // 这里按同一套条件自己订阅 UpdateChecker，避免为一个卡片把整个会话 VM 拉进来
    val updateChecker = koinInject<UpdateChecker>()
    val updateCheckActive = BuildConfig.UPDATE_CHECK_ENABLED &&
        !BuildConfig.IS_PLAY_STORE &&
        !settings.init &&
        updateChecksEnabled
    val updateState by produceState<UiState<UpdateInfo>>(
        initialValue = UiState.Loading,
        updateCheckActive,
    ) {
        if (!updateCheckActive) {
            value = UiState.Loading
            return@produceState
        }
        updateChecker.updateState.collect { value = it }
    }

    // 抽屉顶部：更新/备份提醒、用户信息、常用入口和排序
    val drawerHeader: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (updateCheckActive) {
                UpdateCard(
                    state = updateState,
                    updateChecker = updateChecker,
                )
            }

            BackupReminderCard(
                settings = settings,
                onClick = { navController.navigate(Screen.Backup) },
            )

            DrawerActions(navController = navController)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_page_conversation_history),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                ConversationSortButton(
                    sortOrder = settings.displaySetting.conversationSortOrder,
                    onSortOrderChange = { sortOrder ->
                        listVm.updateSettings {
                            it.copy(
                                displaySetting = it.displaySetting.copy(
                                    conversationSortOrder = sortOrder
                                )
                            )
                        }
                        // 换排序后回到顶部，否则会停留在原来滚动到的位置
                        drawerVm.resetScrollPositions()
                    },
                )
            }
        }
    }

    // 左栏不要圆角：横屏它是常驻的一列，竖屏滑出来也是一整块边，圆角会让它浮在页面上的卡片
    val drawerShape = RectangleShape
    // 高级外观开启后，抽屉底板改由 advancedMaterial 画：材质、着色、模糊半径和性能开关
    // 全部来自 navigationMaterial()，和导航栏其余部分共用同一份参数。
    // 关掉高级外观时只保留抽屉自身的实色底。
    val drawerMaterial = LocalSettings.current.advancedAppearanceSetting
        .takeIf { it.enableAdvancedAppearance }
        ?.navigationMaterial()

    // 内容本体：抽屉、横屏左栏、整页侧边栏三种形态共用这一份
    val panel: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!scrollableHeader) {
                drawerHeader()
            }

            LaunchedEffect(verticalListState, listHeaderCount) {
                snapshotFlow {
                    verticalListState.firstVisibleItemIndex to verticalListState.firstVisibleItemScrollOffset
                }
                    .distinctUntilChanged()
                    .collectLatest { (index, offset) ->
                        delay(SCROLL_POSITION_SAVE_DELAY_MS)
                        drawerVm.saveVerticalScrollPosition(
                            (index - listHeaderCount).coerceAtLeast(0),
                            offset,
                        )
                    }
            }
            ConversationSectionList(
                currentConversationId = currentConversationId,
                items = verticalItems,
                conversationJobs = conversationJobs.keys,
                listState = verticalListState,
                collapsedDates = collapsedDates,
                onToggleDate = drawerVm::toggleDateCollapsed,
                collapsedFolders = collapsedFolders,
                onToggleFolder = drawerVm::toggleFolderCollapsed,
                scopeKey = settings.assistantId to "vertical",
                header = if (scrollableHeader) drawerHeader else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onClick = { conversation ->
                    openConversation(conversation)
                },
                onRegenerateTitle = { listVm.generateTitle(it, true) },
                onRename = onRenameConversation,
                onDelete = onDeleteConversation,
                onPin = onPinConversation,
                onMoveToAssistant = onMoveConversationToAssistant,
                onMoveToFolder = onMoveConversationToFolder,
                onRenameFolder = { folderId ->
                    folders.firstOrNull { it.id == folderId }?.let { folderToRename = it }
                },
                onDeleteFolder = { folderId ->
                    folders.firstOrNull { it.id == folderId }?.let { folderToDelete = it }
                },
                onBindFolderWorkspace = { folderId ->
                    folders.firstOrNull { it.id == folderId }?.let { folder ->
                        folderWorkspaceDraft = folder.allWorkspaceIds
                        folderToBindWorkspace = folder
                    }
                },
                onDeleteSelected = onDeleteSelectedConversations,
                onPinSelected = onPinSelectedConversations,
            )

            memoryDreaming?.let { status ->
                MemoryDreamingEntry(
                    status = status,
                    onClick = { navController.navigate(Screen.AssistantMemory(settings.assistantId.toString())) },
                )
            }

            // 助手选择器
            AssistantPicker(
                settings = settings,
                onSelectAssistant = { assistant ->
                    val updateJob = listVm.updateSettings { it.copy(assistantId = assistant.id) }
                    scope.launch {
                        updateJob.join()
                        val id = if (context.readBooleanPreference("create_new_conversation_on_start", true)) {
                            Uuid.random()
                        } else {
                            repo.getConversationsOfAssistant(assistant.id)
                                .first()
                                .firstOrNull()
                                ?.id ?: Uuid.random()
                        }
                        navigateToChatPage(navigator = navController, chatId = id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                onClickSetting = {
                    val currentAssistantId = settings.assistantId
                    navController.navigate(Screen.AssistantDetail(id = currentAssistantId.toString()))
                }
            )

            DrawerBottomBar(
                avatar = settings.displaySetting.userAvatar,
                name = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                onNewChat = onNewChat,
                onOpenSettings = onOpenSettings,
            )
        }
    }

    // 三种形态共用同一份内容，只有外壳不同：
    // - fullPage：整页侧边栏，内容直接铺满，底板由页面负责
    // - 其余：抽屉 / 横屏左栏，套 ModalDrawerSheet
    if (fullPage) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (drawerMaterial != null) {
                        Modifier.advancedMaterial(
                            shape = drawerShape,
                            base = MaterialTheme.colorScheme.surfaceContainerLow,
                            material = drawerMaterial,
                            hazeState = hazeState,
                            readableFloor = .65f,
                        )
                    } else {
                        Modifier
                    }
                ),
        ) {
            panel()
        }
    } else {
        // 底板只取页面背景层，不取会话列表，抽屉滑出来时列表内容不会跟着糊成一团
        ModalDrawerSheet(
            modifier = Modifier
                .width(drawerWidth)
                .then(
                    if (drawerMaterial != null) {
                        Modifier.advancedMaterial(
                            shape = drawerShape,
                            base = MaterialTheme.colorScheme.surfaceContainerLow,
                            material = drawerMaterial,
                            hazeState = hazeState,
                            // 抽屉压在会话列表上，着色不能掉到 0.65 以下：再低正文会透上来
                            readableFloor = .65f,
                        )
                    } else {
                        Modifier
                    }
                ),
            drawerShape = drawerShape,
            // 着色由材质层统一铺，这里保持透明；没有源时它会退化成半透明色
            drawerContainerColor = if (drawerMaterial != null) Color.Transparent
            else MaterialTheme.colorScheme.surfaceContainerLow,
            // 底色交给玻璃层以后 contentColorFor 推不出内容色，这里显式钉住（= surfaceContainerLow 的默认值）
            drawerContentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            panel()
        }
    }

    // 昵称编辑对话框
    nicknameEditState.EditStateContent { nickname, onUpdate ->
        AnimatedAlertDialog(
            onDismissRequest = {
                nicknameEditState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_nickname))
            },
            text = {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.chat_page_nickname_placeholder)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        nicknameEditState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        nicknameEditState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // 移动到文件夹 Bottom Sheet
    val doMove: (Uuid?) -> Unit = { folderId ->
        conversationToMoveFolder?.let { conversation ->
            drawerVm.moveConversationToFolder(conversation.id, folderId)
            showMoveToFolderSheet = false
            conversationToMoveFolder = null
        }
    }
    AppModalSheet(
        visible = showMoveToFolderSheet,
        onDismissRequest = {
            showMoveToFolderSheet = false
            conversationToMoveFolder = null
        },
        title = stringResource(R.string.chat_page_move_to_folder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // 移出文件夹（未归类）
            Surface(
                onClick = { doMove(null) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = if (conversationToMoveFolder?.folderId == null) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(HugeIcons.Folder01, null)
                    Text(
                        text = stringResource(R.string.chat_page_remove_from_folder),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(folders) { folder ->
                    val isCurrent = folder.id == conversationToMoveFolder?.folderId
                    Surface(
                        onClick = { doMove(folder.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        tonalElevation = if (isCurrent) 2.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(HugeIcons.Folder01, null)
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    // 重命名文件夹对话框
    folderToRename?.let { folder ->
        var name by remember(folder.id) { mutableStateOf(folder.name) }
        AnimatedAlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text(stringResource(R.string.chat_page_rename_folder)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        drawerVm.renameFolder(folder.id, name)
                        folderToRename = null
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.chat_page_save)) }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // 删除文件夹确认
    folderToDelete?.let { folder ->
        AnimatedAlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(R.string.chat_page_delete_folder)) },
            text = { Text(stringResource(R.string.chat_page_delete_folder_confirm, folder.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (drawerVm.deleteFolder(folder.id)) {
                            folderToDelete = null
                        } else {
                            toaster.show(context.getString(R.string.chat_page_delete_folder_generating), type = ToastType.Warning)
                        }
                    }
                ) { Text(stringResource(R.string.chat_page_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // 文件夹绑定工作区：这个分组里的会话共用这组工作区。
    // 可见性交给 sheet 自己：收起时先播完退场再拆窗口，不在外面用 if 包着
    WorkspaceSelectSheet(
        visible = folderToBindWorkspace != null,
        boundIds = folderWorkspaceDraft,
        workspaces = bindableWorkspaces,
        supportingText = stringResource(R.string.chat_page_folder_workspace_hint),
        onSelect = { ids ->
            val parsed = ids.mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }.toSet()
            folderWorkspaceDraft = parsed
            folderToBindWorkspace?.let { folder -> drawerVm.updateFolderWorkspaces(folder.id, parsed) }
        },
        onManage = {
            folderToBindWorkspace = null
            navController.navigate(Screen.Workspaces)
        },
        onDismiss = { folderToBindWorkspace = null },
    )

    // 移动到助手 Bottom Sheet
    AppModalSheet(
        visible = showMoveToAssistantSheet,
        onDismissRequest = {
            showMoveToAssistantSheet = false
            conversationToMove = null
        },
        title = stringResource(R.string.chat_page_move_to_assistant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(settings.assistants) { assistant ->
                    AssistantItem(
                        assistant = assistant,
                        isCurrentAssistant = assistant.id == conversationToMove?.assistantId,
                        onClick = {
                            conversationToMove?.let { conversation ->
                                listVm.moveConversationToAssistant(
                                    conversation = conversation,
                                    targetAssistantId = assistant.id,
                                    // 挪的正好是当前打开的会话时，把当前助手也切过去
                                    currentConversationId = currentConversationId,
                                )
                                showMoveToAssistantSheet = false
                                conversationToMove = null
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerActions(navController: Navigator) {
    // 上方这几行入口可以整体收起，给会话列表腾地方
    var collapsed by rememberSharedPreferenceBoolean("drawer_actions_collapsed", false)
    Column {
        Surface(
            onClick = { collapsed = !collapsed },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = MaterialTheme.shapes.medium,
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_page_shortcuts),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (collapsed) HugeIcons.ArrowDown01 else HugeIcons.ArrowUp01,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MotionExpandCollapse(visible = !collapsed) {
            Column {
                // 历史记录入口，行尾是搜索入口
                DrawerEntry(
                    icon = HugeIcons.TransactionHistory,
                    text = stringResource(R.string.chat_page_history),
                    onClick = { navController.navigate(Screen.History) },
                    trailingIcon = HugeIcons.Search01,
                    trailingDescription = stringResource(R.string.chat_page_search_chats),
                    onTrailingClick = { navController.navigate(Screen.MessageSearch) },
                )

                // 媒体创作入口
                DrawerEntry(
                    icon = HugeIcons.ImageToVideo,
                    text = stringResource(R.string.media_creation_title),
                    onClick = { navController.navigate(Screen.MediaCreationSessions) },
                )

                // 翻译入口
                DrawerEntry(
                    icon = HugeIcons.LanguageCircle,
                    text = stringResource(R.string.chat_page_menu_ai_translator),
                    onClick = { navController.navigate(Screen.Translator) },
                )

                DrawerEntry(
                    icon = HugeIcons.LookTop,
                    text = stringResource(R.string.assistant_page_title),
                    onClick = { navController.navigate(Screen.Assistant) },
                )

                DrawerEntry(
                    icon = HugeIcons.InLove,
                    text = stringResource(R.string.favorite_page_title),
                    onClick = { navController.navigate(Screen.Favorite) },
                )

                DrawerEntry(
                    icon = HugeIcons.ChartColumn,
                    text = stringResource(R.string.chat_page_stats),
                    onClick = { navController.navigate(Screen.Stats) },
                )
            }
        }
    }
}

@Composable
private fun DrawerEntry(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    trailingIcon: ImageVector? = null,
    trailingDescription: String? = null,
    onTrailingClick: () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = glassColor(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (trailingIcon != null) {
                // 不用 IconButton，它的 48dp 最小尺寸会把这一行撑得比其他行高
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onTrailingClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = trailingDescription,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// 后台正在整理当前助手的记忆，点开是记忆页
@Composable
private fun DrawerBottomBar(
    avatar: Avatar,
    name: String,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            onClick = onNewChat,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                // 只缩绘制，不改布局：缩小后左下角留在原位，点击热区跟着变小的同时仍对齐视觉范围
                .graphicsLayer {
                    scaleX = NEW_CHAT_PILL_SCALE
                    scaleY = NEW_CHAT_PILL_SCALE
                    transformOrigin = TransformOrigin(0f, 1f)
                },
            shape = CircleShape,
            // primaryContainer 不是主色 CTA 的实心底色，跟着抽屉一起玻璃；
            // 文字色 onPrimaryContainer 保持不透明，深色主题下的对比度要真机确认
            color = glassColor(MaterialTheme.colorScheme.primaryContainer),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.PencilEdit01,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.chat_page_new_chat),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        UIAvatar(
            name = name,
            value = avatar,
            avatarSize = 52.dp,
            videoPlayback = true,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = DRAWER_AVATAR_SCALE
                    scaleY = DRAWER_AVATAR_SCALE
                    transformOrigin = TransformOrigin(1f, 1f)
                },
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun MemoryDreamingEntry(status: MemoryConsolidationStatus, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = glassColor(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ContainedLoadingIndicator(modifier = Modifier.size(18.dp))
            Text(
                text = status.roundProgress
                    ?.let { stringResource(R.string.memory_dream_progress, status.roundDone, status.roundTotal) }
                    ?: stringResource(R.string.memory_dreaming),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DrawerAction(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Tooltip(
            tooltip = {
                label()
            }
        ) {
            Box(
                modifier = Modifier
                    .padding(10.dp)
                    .size(20.dp),
            ) {
                icon()
            }
        }
    }
}

@Composable
private fun ConversationSortButton(
    sortOrder: ConversationSortOrder,
    onSortOrderChange: (ConversationSortOrder) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // 容器变换的起点：这颗圆形排序按钮。菜单从它的窗口矩形长出来，
    // 不是旁边冒出一张卡
    val anchor = rememberSelectMenuAnchor()

    // 文案在组合里读好：content lambda 在 LaunchedEffect 里跑，没有 Composition
    val updateTimeLabel = stringResource(R.string.chat_page_sort_by_update_time)
    val createTimeLabel = stringResource(R.string.chat_page_sort_by_create_time)

    rememberNativeSelectMenu(
        expanded = menuExpanded,
        onDismiss = { menuExpanded = false },
        anchorBounds = anchor.value.value,
        icons = ConversationSortOrder.entries.map { null },
    ) {
        ConversationSortOrder.entries.forEach { option ->
            val label = when (option) {
                ConversationSortOrder.UPDATE_TIME -> updateTimeLabel
                ConversationSortOrder.CREATE_TIME -> createTimeLabel
            }
            addItem(label, null, selected = option == sortOrder) {
                if (option != sortOrder) onSortOrderChange(option)
            }
        }
    }

    Surface(
        onClick = { menuExpanded = true },
        shape = CircleShape,
        color = glassColor(MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.then(anchor.modifier),
    ) {
        Tooltip(
            tooltip = { Text(stringResource(R.string.chat_page_sort_conversations)) }
        ) {
            Icon(
                imageVector = HugeIcons.Sorting01,
                contentDescription = stringResource(R.string.chat_page_sort_conversations),
                modifier = Modifier
                    .padding(8.dp)
                    .size(16.dp),
            )
        }
    }
}

@Composable
private fun AssistantItem(
    assistant: Assistant,
    isCurrentAssistant: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isCurrentAssistant) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isCurrentAssistant) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UIAvatar(
                name = assistant.name,
                value = assistant.avatar,
                onUpdate = {},
                modifier = Modifier.size(40.dp),
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isCurrentAssistant) {
                    Text(
                        text = stringResource(R.string.assistant_page_current_assistant),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

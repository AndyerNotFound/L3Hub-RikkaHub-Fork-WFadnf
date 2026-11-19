package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.transformers.collectLorebookInjections
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.FloatingBarItem
import me.rerere.rikkahub.data.model.GuidanceMode
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.effectiveContextWindow
import me.rerere.rikkahub.data.model.isContextLimitReached
import me.rerere.rikkahub.data.model.lastContextTokens
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.BackgroundShellStatus
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.SessionActivity
import me.rerere.rikkahub.ui.components.ai.BackgroundTasksSheet
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.ChatQuotaStrip
import me.rerere.rikkahub.ui.components.ai.ChatQuotaStripDashboard
import me.rerere.rikkahub.ui.components.ai.rememberChatQuotaStripData
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.ai.rememberGenerationSpeed
import me.rerere.rikkahub.ui.components.message.ConversationUsageDetails
import me.rerere.rikkahub.ui.components.message.LocalConversationStatusInSidePanel
import me.rerere.rikkahub.ui.components.message.LocalTurnChangesInSidePanel
import me.rerere.rikkahub.ui.components.message.editedFilesIn
import me.rerere.rikkahub.ui.components.message.tools.LocalToolDetailHost
import me.rerere.rikkahub.ui.components.message.tools.ToolDetailHostState
import me.rerere.rikkahub.ui.components.message.usageTokenSpeed
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.ColumnResizeHandle
import me.rerere.rikkahub.ui.components.ui.SlideOverDrawer
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.modifier.MotionExpandCollapse
import me.rerere.rikkahub.ui.modifier.rememberMotionBlurSteps
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.theme.LocalUiSlots
import me.rerere.rikkahub.ui.theme.rememberSlideSpec
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.components.AnimatedModalBottomSheet
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val filesManager: FilesManager = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    // 会话状态按 State 交给列表，页面本身只在下面这几个派生值变化时重组。
    // 直接在页面里读 conversation，流式输出每来一个 token 都会让整页（输入框、顶栏、
    // 抽屉、背景）跟着走一遍；真正需要逐 token 更新的只有消息列表。
    val conversationState = vm.conversation.collectAsStateWithLifecycle()
    val conversationId by remember(conversationState) {
        derivedStateOf { conversationState.value.id }
    }
    val conversationFolderId by remember(conversationState) {
        derivedStateOf { conversationState.value.folderId }
    }
    val conversationMessageCount by remember(conversationState) {
        derivedStateOf { conversationState.value.messageNodes.size }
    }
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    val folders by vm.folders.collectAsStateWithLifecycle()
    val folderBinding by vm.folderWorkspaceBinding.collectAsStateWithLifecycle()
    // 文件夹绑了工作区时，工作区入口只展示这份绑定，改要去抽屉的文件夹菜单里改。
    // 子代理会话自己没有分组，工作区来自父会话的文件夹，那个文件夹可能属于别的助手、这里查不到名字，
    // 所以空串表示「被文件夹接管，但名字未知」
    val folderWorkspaceName = remember(conversationFolderId, folders, folderBinding) {
        val ownFolder = folders.firstOrNull { it.id == conversationFolderId }
        when {
            ownFolder != null && ownFolder.allWorkspaceIds.isNotEmpty() -> ownFolder.name
            folderBinding.isNotEmpty -> ""
            else -> null
        }
    }
    // 竖屏的侧边栏：自己画的滑入抽屉（见 SlideOverDrawer），不用 Material 那个会回弹的弹簧
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    val drawerSlideSpec = rememberSlideSpec<Float>(animated = !setting.displaySetting.disableAnimations)
    // 背景层（AssistantBackground）单独一份 Haze 源：抽屉和浮层的玻璃都取它，
    // 取聊天列表会把正文一起糊进来。提到这一层是为了抽屉也拿得到
    val backgroundHazeState = rememberHazeState()
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    // 键盘开合不进组合：inset 每帧都在变，读进这一层等于整页跟着键盘重组一遍，
    // 玻璃、模糊、英雄卡都要重新走一次；这里只订阅「已经收起」这一个信号
    LaunchedEffect(imeInsets, density) {
        snapshotFlow { imeInsets.getBottom(density) > 0 }
            .filter { !it }
            .collect { focusManager.clearFocus(force = true) }
    }

    // 抽屉开着时接管返回键
    BackHandler(enabled = drawerOpen) {
        drawerOpen = false
    }

    // Clear input focus so popup transitions cannot reopen the keyboard.
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    val windowDpSize = currentWindowDpSize()
    // 横屏三栏：宽度够同时放下聊天区和左右两列才启用（见 ChatSidePanel.kt）
    val threePaneLayout = windowDpSize.width > windowDpSize.height && windowDpSize.width >= 600.dp
    // 左栏在横屏里是一列，不是抽屉：默认展开，顶栏的菜单按钮负责收合
    var sidebarOpen by rememberSaveable { mutableStateOf(true) }
    // 横屏三栏的列宽：拖分隔条改，关掉页面重进还在
    var sidebarWidthDp by rememberSaveable { mutableStateOf(LANDSCAPE_SIDEBAR_WIDTH.value) }
    var chatWidthDp by rememberSaveable { mutableStateOf(0f) }
    var sidebarResizing by remember { mutableStateOf(false) }
    // 转进横屏时把模态抽屉收掉，免得转回竖屏后它还开着（#1304）
    LaunchedEffect(threePaneLayout) {
        if (threePaneLayout && drawerOpen) {
            drawerOpen = false
        }
    }

    val openSettingsFromDrawer: () -> Unit = {
        // 不先收抽屉：设置页整屏盖上来时它跟着一起退场，
        // 先滑回去再推设置页等于两段动画，反而拖
        navController.navigate(Screen.Setting)
    }
    val startNewChat: () -> Unit = {
        // 横屏三栏没有模态抽屉可收；竖屏收一下，免得新会话页还被挡着
        if (!threePaneLayout) drawerOpen = false
        navigateToChatPage(navController, Uuid.random())
    }

    val startVoiceMode = rememberVoiceModeStarter(vm, setting)

    val inputState = vm.inputState

    // 初始化输入状态（处理传入的 files 和 text 参数）
    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull { file ->
                filesManager.getFileMimeType(file)
            }
            val parts = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
            inputState.messageContent = parts
        }
        text?.base64Decode()?.let { decodedText ->
            if (decodedText.isNotEmpty()) {
                inputState.setMessageText(decodedText)
            }
        }
    }

    val chatListState = rememberLazyListState()
    LaunchedEffect(nodeId, conversationMessageCount) {
        val conversation = conversationState.value
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) {
                    chatListState.scrollToItem(index)
                }
            } else {
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            }
            vm.chatListInitialized = true
        }
    }

    // 横屏的工具调用详情：挂在页面最外层，从右侧滑入，不另开窗口
    val toolDetailHost = remember { ToolDetailHostState() }
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalToolDetailHost provides toolDetailHost) {
        if (threePaneLayout) {
            val baseDensity = LocalDensity.current
            // 横屏三栏：整块缩到 75%。三列并排后每列都只剩竖屏的三分之一宽，
            // 字号、行高、控件一起缩，信息量才塞得回去
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density * LANDSCAPE_UI_SCALE,
                    fontScale = baseDensity.fontScale,
                ),
            ) {
                // 三列在缩放后的坐标系里量：整块窗口先除回 0.75
                val totalWidth = windowDpSize.width / LANDSCAPE_UI_SCALE
                val maxChatWidth = (totalWidth - (if (sidebarOpen) sidebarWidthDp else 0f).dp - 260.dp)
                    .coerceAtLeast(320.dp)
                // 中列默认按窗口比例，拖过以后按拖出来的宽度
                val chatWidth = (if (chatWidthDp > 0f) chatWidthDp.dp else (totalWidth * 0.4f))
                    .coerceIn(320.dp, maxChatWidth)
                Row(modifier = Modifier.fillMaxSize()) {
                    // 左栏：原来的抽屉内容，横屏里并排放，宽度动画负责开合
                    val sidebarSlideSpec = rememberSlideSpec<Dp>(
                        animated = !setting.displaySetting.disableAnimations,
                    )
                    val sidebarWidth by animateDpAsState(
                        targetValue = if (sidebarOpen) sidebarWidthDp.dp else 0.dp,
                        // 拖动时不要弹簧，手柄和边缘之间不能有一帧的滞后
                        animationSpec = if (sidebarResizing) {
                            snap()
                        } else {
                            sidebarSlideSpec
                        },
                        label = "chatSidebarWidth",
                    )
                    if (sidebarWidth > 0.dp) {
                        Box(
                            modifier = Modifier
                                .width(sidebarWidth)
                                .fillMaxHeight()
                                .clipToBounds(),
                        ) {
                            ChatDrawerContent(
                                navController = navController,
                                currentConversationId = conversationId,
                                vm = vm,
                                settings = setting,
                                onNewChat = startNewChat,
                                onOpenSettings = openSettingsFromDrawer,
                                // 不传 onCloseDrawer：点会话时左栏要留着，它是常驻列不是抽屉
                                hazeState = backgroundHazeState,
                                widthOverride = sidebarWidthDp.dp,
                            )
                        }
                        ColumnResizeHandle(
                            onDrag = { delta ->
                                sidebarWidthDp = (sidebarWidthDp + delta.value).coerceIn(200f, 440f)
                            },
                            onDragStart = { sidebarResizing = true },
                            onDragEnd = { sidebarResizing = false },
                        )
                    }
                    ChatPageContent(
                        onStartVoiceMode = startVoiceMode,
                        onCreateNewChat = startNewChat,
                        inputState = inputState,
                        loadingJob = loadingJob,
                        processingStatus = processingStatus,
                        setting = setting,
                        conversationState = conversationState,
                        conversationId = conversationId,
                        folderId = conversationFolderId,
                        messageCount = conversationMessageCount,
                        folderWorkspaceName = folderWorkspaceName,
                        navController = navController,
                        vm = vm,
                        chatListState = chatListState,
                        assistant = assistant,
                        currentChatModel = currentChatModel,
                        threePane = true,
                        sidebarOpen = sidebarOpen,
                        onOpenSidebar = { sidebarOpen = !sidebarOpen },
                        chatWidth = chatWidth,
                        onChatResize = { delta ->
                            val maxChat = (totalWidth - (if (sidebarOpen) sidebarWidthDp else 0f).dp - 260.dp)
                                .coerceAtLeast(320.dp)
                            // 读状态本身，不读这一帧的 chatWidth：一次手势里可能连来好几个事件
                            val current = if (chatWidthDp > 0f) chatWidthDp.dp else (totalWidth * 0.4f)
                            chatWidthDp = (current + delta).coerceIn(320.dp, maxChat).value
                        },
                        backgroundHazeState = backgroundHazeState,
                        errors = errors,
                        onDismissError = { vm.dismissError(it) },
                        onClearAllErrors = { vm.clearAllErrors() },
                    )
                }
            }
        } else {
            SlideOverDrawer(
                open = drawerOpen,
                onOpenChange = { drawerOpen = it },
                animationSpec = drawerSlideSpec,
                sheet = {
                    ChatDrawerContent(
                        navController = navController,
                        currentConversationId = conversationId,
                        vm = vm,
                        settings = setting,
                        onNewChat = startNewChat,
                        onOpenSettings = openSettingsFromDrawer,
                        onCloseDrawer = { drawerOpen = false },
                        hazeState = backgroundHazeState,
                    )
                },
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    onCreateNewChat = startNewChat,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversationState = conversationState,
                    conversationId = conversationId,
                    folderId = conversationFolderId,
                    messageCount = conversationMessageCount,
                    folderWorkspaceName = folderWorkspaceName,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    assistant = assistant,
                    currentChatModel = currentChatModel,
                    threePane = false,
                    sidebarOpen = drawerOpen,
                    onOpenSidebar = { drawerOpen = true },
                    backgroundHazeState = backgroundHazeState,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }
        }
        ToolDetailSidePanel(host = toolDetailHost)
    }
}

@Composable
private fun ChatPageContent(
    onStartVoiceMode: () -> Unit,
    onCreateNewChat: () -> Unit,
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    // 会话用 State 传进来：流式输出每个 token 都换一份 Conversation，
    // 这里只读派生值，逐 token 的部分留给 ChatList。
    conversationState: State<Conversation>,
    // 下面三个派生值在 ChatPage 里已经算过（抽屉、文件夹名、首屏滚动都在用），
    // 传进来是为了不在两层各挂一份 derivedStateOf
    conversationId: Uuid,
    // 会话所属文件夹：顶栏的文件夹胶囊读它
    folderId: Uuid?,
    // 消息节点数：详情面板和请求快照的刷新键
    messageCount: Int,
    // 文件夹绑了工作区时传文件夹名：工作区入口只展示这份绑定（见 ChatPage 顶部）
    folderWorkspaceName: String?,
    // 横屏三栏：中间是聊天区，右栏是标签页式的看板（见 ChatSidePanel.kt）
    threePane: Boolean,
    sidebarOpen: Boolean,
    onOpenSidebar: () -> Unit,
    // 中列宽度：横屏三栏时定宽，收起左栏时中列左移、右栏变宽；null 表示没有右栏，中列吃满全宽
    chatWidth: Dp? = null,
    // 拖动中列与右栏之间的分隔条
    onChatResize: (Dp) -> Unit = {},
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    assistant: Assistant,
    currentChatModel: Model?,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
    // 背景层的 Haze 源：抽屉和浮层共用，见 ChatPage 里的注释
    backgroundHazeState: HazeState,
) {
    val scope = rememberCoroutineScope()
    val sessionActivity by vm.sessionActivity.collectAsStateWithLifecycle()
    // 顶栏胶囊的文件夹选单：按会话自己的助手取，不看抽屉里当前选中的助手
    val conversationFolders by vm.folders.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    // 页面这一层不直接读整份会话：流式输出每个 token 都会换一份 Conversation，
    // 直接读会让输入框、顶栏、背景、抽屉整页跟着重组。下面只派生页面真正关心的字段，
    // 结果没变就不会让页面重组；逐 token 的正文留给 ChatList 自己读 State。
    // conversationId / folderId / messageCount 由 ChatPage 算一次传进来，不在这里再挂一份。
    // 本轮生成速度：消息列表下方的加载图标与输入区上方的表盘共用这一份采样
    val generationSpeed = rememberGenerationSpeed(conversationState, loadingJob != null)
    val hasMessages by remember(conversationState) {
        derivedStateOf { conversationState.value.messageNodes.isNotEmpty() }
    }
    val messageIds by remember(conversationState) {
        derivedStateOf { conversationState.value.messageNodes.map { it.id } }
    }
    val todos by remember(conversationState) { derivedStateOf { conversationState.value.todos } }
    val workspaceCwds by remember(conversationState) {
        derivedStateOf { conversationState.value.workspaceCwds }
    }
    val customSystemPrompt by remember(conversationState) {
        derivedStateOf { conversationState.value.customSystemPrompt }
    }
    val newConversation by remember(conversationState) {
        derivedStateOf { conversationState.value.newConversation }
    }
    val contextTokens by remember(conversationState) {
        derivedStateOf { conversationState.value.lastContextTokens() }
    }
    val lastUsageMessage by remember(conversationState) {
        derivedStateOf { conversationState.value.currentMessages.lastOrNull { it.usage != null } }
    }
    val lastAssistantMessageId by remember(conversationState) {
        derivedStateOf {
            conversationState.value.currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id
        }
    }
    val turnEditedFiles by remember(conversationState) {
        derivedStateOf {
            conversationState.value.currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
                ?.let { editedFilesIn(it.parts) }
                .orEmpty()
        }
    }

    // 新消息入场的跟踪器放在这一层：空会话时 ChatList 还没挂载，
    // 放在列表里的话首条消息会被当成「打开会话时已经存在」而跳过动画。
    val messageEnterTracker = remember(conversationId) { MessageEnterTracker() }
    // 打开已有会话时首帧也是空列表，紧接着到位的那一批是历史加载，不是新追加；
    // 只吞掉这一批的入场，之后真正追加的消息照常滑进来
    var emptyWasHistoryLoad by remember { mutableStateOf(false) }
    val enteringMessageIds = messageEnterTracker.track(
        ids = messageIds,
        animateAppends = !emptyWasHistoryLoad,
    )
    val consumeMessageEnter = remember(messageEnterTracker) { messageEnterTracker::consume }

    // —— 空会话里输入区的落点 ——
    // 输入框始终挂在 Scaffold 的 bottomBar 上，空会话时整块往上抬到英雄卡下面：
    // 同一个组合实例从中间滑到底部，不会有「旧输入框消失、新输入框闪现」的交接。
    // 抬升量 = 输入栏高度 +（英雄卡底边 + 16dp - 输入栏底边）：
    // 底边钉在窗口底部，栏高在绘制阶段现取，高度动画的每一帧都能同一帧对齐。
    // 之前拿「输入栏顶边」的实测值反推，读到的是上一帧的位置，
    // 收起和再展开时整块会被往回拽一帧，看起来就是闪一下。
    var inputBarBottomPx by remember { mutableFloatStateOf(0f) }
    var emptyHeroBottomPx by remember { mutableFloatStateOf(0f) }
    // 英雄卡和输入栏都量到以后再显示空态：否则第一帧会按「没有输入框」的位置算，随后再跳一下
    val emptyLayoutReady by remember {
        derivedStateOf { emptyHeroBottomPx > 0f && inputBarBottomPx > 0f }
    }
    // 只有「新会话还空着」才需要播这段滑动和英雄卡退场；打开已有会话时首帧也是空列表，
    // 那是历史还在加载，直接切过去，不能让输入框从中间滑到底部
    var animateEmptyExit by remember { mutableStateOf(false) }
    LaunchedEffect(hasMessages, newConversation) {
        if (!hasMessages) {
            animateEmptyExit = newConversation
            emptyWasHistoryLoad = !newConversation
        } else {
            emptyWasHistoryLoad = false
        }
    }
    // 空态整块要等英雄卡退场播完才能拆；列表已经挂上来以后它只是浮在上面的退场层
    val emptyHeroTransition = remember { MutableTransitionState(!hasMessages) }
    LaunchedEffect(hasMessages) { emptyHeroTransition.targetState = !hasMessages }
    val keepEmptyLayout = !hasMessages ||
        (animateEmptyExit && (emptyHeroTransition.currentState || !emptyHeroTransition.isIdle))

    val density = LocalDensity.current
    // 底栏绘制期读同一个键盘 inset，和英雄卡的平移对齐
    val imeInsets = WindowInsets.ime
    val inputLiftSpec = rememberSlideSpec<Float>(animated = !setting.displaySetting.disableAnimations)
    val emptyAnchorGapPx = with(density) { 16.dp.toPx() }
    val animateInputToBottom = hasMessages && animateEmptyExit
    // 首条消息发出后从空态位置滑到底部：空态落点每帧都是现算的，
    // 这里只推一个 0→1 的进度，发送后的第一帧还是发送前的位置，
    // 不会先落回底部、等下一帧再弹上来
    val inputExitProgress = remember { Animatable(0f) }
    // 首条消息滑到底部期间保持「键盘 inset 交给页面平移」的布局，
    // 等滑完再交回底栏；否则发送那一帧会把键盘高度加回底栏，输入区先跳一下
    var inputExitRunning by remember { mutableStateOf(false) }
    val inputScrimAlpha by animateFloatAsState(
        targetValue = if (hasMessages) 1f else 0f,
        animationSpec = if (animateInputToBottom) inputLiftSpec else snap(),
        label = "chatInputScrim",
    )
    val inputBlurSteps = rememberMotionBlurSteps(maxRadius = 6.dp)
    val inputBlur = remember { Animatable(0f) }
    LaunchedEffect(hasMessages) {
        if (!hasMessages) {
            inputExitProgress.snapTo(0f)
            inputExitRunning = false
            return@LaunchedEffect
        }
        if (animateInputToBottom) {
            inputExitRunning = true
            try {
                // 空态那一层滑动的模糊；本来就是带消息打开的不用糊
                if (!setting.displaySetting.disableAnimations && emptyLayoutReady) {
                    inputBlur.snapTo(1f)
                    launch { inputBlur.animateTo(0f, inputLiftSpec) }
                }
                inputExitProgress.animateTo(1f, inputLiftSpec)
            } finally {
                inputExitRunning = false
            }
        } else {
            inputExitProgress.snapTo(1f)
            inputExitRunning = false
        }
    }

    // 上下文占用取最近一次回复的真实用量, 到达模型的 MAX 门限后禁止再发送
    val contextLimitReached = isContextLimitReached(contextTokens, currentChatModel?.maxContextTokens)
    // 模型可以关掉强制拦截，此时只在发送时提醒一次
    val contextLimitBlocksSend = contextLimitReached && currentChatModel?.enforceMaxContext != false
    val contextLimitMessage = stringResource(R.string.chat_page_context_limit_reached)
    // 顶栏限额条看的是当前模型所在的渠道，切换模型后跟着换
    val currentProvider = remember(currentChatModel, setting.providers) {
        currentChatModel?.findProvider(setting.providers)
    }
    // 限额看板：折叠条留在顶栏，展开的看板叠在内容层上，展开时不改顶栏的布局高度。
    // 轮询只跑一份，折叠条的摘要和看板读同一份数据
    val quotaProvider = currentProvider.takeIf { setting.displaySetting.showQuotaBar }
    val quotaData = quotaProvider?.let { rememberChatQuotaStripData(it) }
    var quotaExpanded by rememberSaveable(currentProvider?.id?.toString()) { mutableStateOf(false) }

    // Usage 看板里的最近一次请求速度
    val usageTokenSpeed = remember(lastUsageMessage) { lastUsageMessage?.usageTokenSpeed() }
    // 每个 chunk 都会重建消息列表，世界书匹配要扫最近几轮的全文，不能放在组合里做。
    // 键只取节点数：流式正文每来一个 token 都重启一次扫描，前一次永远跑不完，
    // 派发到 Default 的工作全成了白跑。注入数只在下一轮请求前有意义，条数变化再算就够。
    val lorebookInjectionCount by produceState(
        0,
        conversationId,
        messageCount,
        assistant.lorebookIds,
        setting.lorebooks,
    ) {
        value = if (assistant.lorebookIds.isEmpty()) {
            0
        } else {
            withContext(Dispatchers.Default) {
                collectLorebookInjections(
                    conversationState.value.currentMessages,
                    assistant,
                    setting.lorebooks,
                ).size
            }
        }
    }
    // 详情面板要显示工作区名字和路径，跟着仓库的列表数据走
    val workspaces by remember(workspaceRepository) { workspaceRepository.listFlow() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    // 悬浮栏「终端」槽位只列当前会话真正绑定、且允许命令行的本地工作区
    val floatingBarTerminalWorkspaces = remember(assistant.allWorkspaceIds, workspaces) {
        val boundIds = assistant.allWorkspaceIds.map { it.toString() }.toSet()
        workspaces.filter { it.id in boundIds && it.supportsTerminal() }
    }

    // 上下文已经爆了且开了自动压缩：点发送先确认，确认后由 ChatService 先压缩再发送
    var showCompressAndSendDialog by remember { mutableStateOf(false) }
    // 四条发送路径共用的收尾动作（清空 + 100ms 后滚到底），见 ChatSendActions
    val sendActions = ChatSendActions(
        inputState = inputState,
        vm = vm,
        scope = scope,
        chatListState = chatListState,
    )

    var previewMode by rememberSaveable { mutableStateOf(false) }
    val previewListState = rememberLazyListState()
    val selectionState = rememberChatSelectionState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    // 顶栏「详情」展开状态：面板挂在顶栏下面，展开时把聊天内容整体往下让
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showFilesSheet by remember { mutableStateOf(false) }
    // 悬浮栏「终端」：绑了多个可用工作区时先选一个
    var showTerminalPicker by remember { mutableStateOf(false) }
    var showTaskTree by remember { mutableStateOf(false) }
    var showUsageSheet by remember { mutableStateOf(false) }
    val chatModelListState = rememberModelListState(
        modelId = assistant.chatModelId,
        providers = setting.providers,
        type = ModelType.CHAT,
    )
    // 横屏三栏的右栏：一个看板一个标签页，顺序就是标签条上的顺序（状态见 ChatPaneTabs）
    val paneTabs = rememberChatPaneTabs()
    // 后台任务弹层从输入框里提到这一层：横屏要能把它放进右侧栏
    var showBackgroundTasks by remember { mutableStateOf(false) }
    val backgroundTasks by vm.backgroundTasks.collectAsStateWithLifecycle()
    val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()

    // 会话详情展开时才解析一次请求快照：工具、系统提示词和本轮注入都按当前配置重新算
    val detailsPanelVisible =
        (showDetails && !threePane) || (threePane && paneTabs.selected == ChatSidePane.Details)
    val requestPreviewState by produceState<RequestPreviewUiState>(
        RequestPreviewUiState.Idle,
        detailsPanelVisible,
        assistant,
        currentChatModel,
        customSystemPrompt,
        messageCount,
        workspaceCwds,
        setting.modeInjections,
        setting.lorebooks,
        setting.mcpServers,
        setting.assistants,
    ) {
        if (!detailsPanelVisible) {
            value = RequestPreviewUiState.Idle
            return@produceState
        }
        value = RequestPreviewUiState.Loading
        value = try {
            RequestPreviewUiState.Ready(vm.buildRequestPreview())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RequestPreviewUiState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    // 打开某个覆盖层：横屏进右栏的标签页，竖屏还是原来的弹层 / 对话框
    fun openPane(pane: ChatSidePane) {
        if (threePane) {
            paneTabs.open(pane)
            return
        }
        when (pane) {
            ChatSidePane.Details -> showDetails = true
            ChatSidePane.Usage -> showUsageSheet = true
            ChatSidePane.Files -> showFilesSheet = true
            // 工作区文件只在横屏右栏里出现；竖屏从输入框的附件 / 工作区入口进
            ChatSidePane.Workspace -> Unit
            ChatSidePane.Terminal -> Unit
            ChatSidePane.TaskTree -> showTaskTree = true
            ChatSidePane.ModelPicker -> chatModelListState.open()
            ChatSidePane.BackgroundTasks -> showBackgroundTasks = true
            // 本轮改动在竖屏是消息下面那一行，没有对应的弹层
            ChatSidePane.Changes -> Unit
            // 清单与引导在竖屏还是输入区上方那张卡片
            ChatSidePane.Status -> Unit
        }
    }

    // 收掉右栏当前的标签页
    fun closePane() {
        if (!threePane) return
        paneTabs.closeSelected()
    }

    // 顶栏「详情」：横屏是右栏的标签页，竖屏还是顶栏底下那块的展开
    fun toggleDetailsPane() {
        if (threePane) {
            if (paneTabs.selectedName == ChatSidePane.Details.name) {
                paneTabs.close(ChatSidePane.Details)
            } else {
                paneTabs.open(ChatSidePane.Details)
            }
        } else {
            showDetails = !showDetails
        }
    }

    fun dismissFiles() {
        if (threePane) {
            if (paneTabs.openNames.contains(ChatSidePane.Files.name)) paneTabs.close(ChatSidePane.Files)
        } else {
            showFilesSheet = false
        }
    }

    // 本轮改动：横屏收进右栏。一轮里第一次改文件时把标签页开出来，但不抢当前看板
    var lastAutoOpenedTurn by remember { mutableStateOf<Uuid?>(null) }
    LaunchedEffect(threePane, lastAssistantMessageId, turnEditedFiles) {
        if (!threePane || turnEditedFiles.isEmpty()) return@LaunchedEffect
        val turnId = lastAssistantMessageId ?: return@LaunchedEffect
        // 同一轮不重复开：用户手动关掉「本轮改动」以后别再弹回来
        if (lastAutoOpenedTurn == turnId) return@LaunchedEffect
        lastAutoOpenedTurn = turnId
        paneTabs.ensureOpen(ChatSidePane.Changes)
    }

    // 清单与引导：横屏收进右栏，出现时把标签页开出来，不抢当前看板
    val hasStatusContent = todos.isNotEmpty() || messageQueue.guidances.isNotEmpty()
    LaunchedEffect(threePane, hasStatusContent) {
        if (!threePane || !hasStatusContent) return@LaunchedEffect
        paneTabs.ensureOpen(ChatSidePane.Status)
    }

    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { dismissFiles() },
    )
    val allowAudioVideoAttachments =
        currentChatModel?.findProvider(setting.providers) is ProviderSetting.Google

    // 发送入口：模型、上下文门限检查都走这里，分支见 chatSendGate
    val sendInput: () -> Unit = {
        when (
            chatSendGate(
                hasModel = currentChatModel != null,
                contextLimitReached = contextLimitReached,
                contextLimitBlocksSend = contextLimitBlocksSend,
                autoCompress = assistant.autoCompress,
            )
        ) {
            ChatSendGate.SelectModel -> toaster.show(
                context.getString(R.string.media_creation_page_select_model_first),
                type = ToastType.Error,
            )

            // 到顶且开着自动压缩：先确认，确认后由 ChatService 压缩再发送
            ChatSendGate.ConfirmCompress -> showCompressAndSendDialog = true

            // 到顶、模型开了强制拦截：提示一次，不发送
            ChatSendGate.BlockedByContextLimit ->
                toaster.show(contextLimitMessage, type = ToastType.Error)

            // 到顶但模型关了强制拦截：也提示一次，然后照常发送
            ChatSendGate.Allow -> {
                if (contextLimitReached) toaster.show(contextLimitMessage, type = ToastType.Error)
                sendActions.submit()
            }
        }
    }

    // 消息滚到标题栏下面时才显示胶囊背景，空页面或在顶部时按钮直接浮在背景上
    // 概览页有自己的列表，切过去后跟着它判断
    val listScrolledUnderTopBar by remember(chatListState, previewListState) {
        derivedStateOf {
            if (previewMode) previewListState.canScrollBackward else chatListState.canScrollBackward
        }
    }

    val completionProviders = remember(assistant.allWorkspaceIds, workspaceCwds, workspaceRepository) {
        assistant.allWorkspaceIds.map { workspaceId ->
            WorkspaceCompletionProvider(
                workspaceId = workspaceId.toString(),
                repository = workspaceRepository,
                currentCwd = workspaceCwds[workspaceId.toString()],
            )
        }
    }

    TTSAutoPlay(vm = vm, setting = setting)

    // 概览页按返回键先回聊天页，不再直接退出会话
    BackHandler(previewMode) {
        previewMode = false
    }

    @Composable
    fun ChatInputContent(
        todos: List<TodoItem> = emptyList(),
        modifier: Modifier = Modifier,
        liftedBy: Dp = 0.dp,
        applyWindowInsets: Boolean = true,
        // 空会话把输入法位移交给页面绘制期平移，底栏不再随键盘改高度
        deferImeInset: Boolean = false,
        // 底部渐变遮罩的浓淡：空会话里输入框被抬到页面中间，遮罩跟着淡出，
        // 免得跟着滑下来之前先在卡片周围显出一条底色带
        scrimAlpha: () -> Float = { 1f },
    ) {
        val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
        ChatInput(
            onStartVoiceMode = onStartVoiceMode,
            voiceState = voiceState,
            onStopVoiceMode = vm.voiceSession::stop,
            // 空会话里输入区顶边钉在英雄卡下面，高度变化从顶部往下长；
            // 贴底之后再换回底部对齐
            pinContentToTop = !hasMessages,
            state = inputState,
            messageQueue = messageQueue,
            onRemoveQueuedMessage = vm::removeQueuedMessage,
            onBeginEditQueuedMessage = vm::beginEditQueuedMessage,
            onFinishEditQueuedMessage = vm::finishEditQueuedMessage,
            onResumeMessageQueue = vm::resumeMessageQueue,
            guideMessages = setting.guideMessages,
            // 预设是长期约束，默认多轮；手输的一次性提醒默认单轮，卡片上点一下就能换
            onAddGuidance = { preset ->
                vm.addGuidance(preset.title, preset.content, GuidanceMode.REPEAT)
            },
            onSendAsGuidance = {
                val text = inputState.textContent.text.toString().trim()
                if (text.isNotEmpty()) {
                    vm.addGuidance(title = "", content = text, mode = GuidanceMode.SINGLE)
                    inputState.clearText()
                    toaster.show(context.getString(R.string.guidance_added), type = ToastType.Info)
                }
            },
            onGuidanceToggleMode = vm::toggleGuidanceMode,
            onGuidanceRemove = vm::removeGuidance,
            runningBackgroundTasks = backgroundTasks.count { it.status == BackgroundShellStatus.RUNNING },
            onShowBackgroundTasks = { openPane(ChatSidePane.BackgroundTasks) },
            loading = loadingJob != null,
            settings = setting,
            assistant = assistant,
            chatModel = currentChatModel,
            todos = todos,
            onTodosChange = vm::updateTodos,
            contextTokens = contextTokens,
            usageTokenSpeed = usageTokenSpeed,
            generationSpeed = generationSpeed,
            onUpdateContextMeterStyle = { style ->
                vm.updateSettings { latest ->
                    latest.copy(displaySetting = latest.displaySetting.copy(contextMeterStyle = style))
                }
            },
            injectedLorebookCount = lorebookInjectionCount,
            onShowUsage = { openPane(ChatSidePane.Usage) },
            hazeState = backgroundHazeState,
            completionProviders = completionProviders,
            onCancelClick = {
                vm.stopGeneration()
            },
            onSendClick = {
                // 首条消息的滑落要先锁住底栏的键盘 inset 布局，滑完再交回
                if (!hasMessages) inputExitRunning = true
                sendInput()
            },
            onInterjectClick = {
                if (contextLimitReached) {
                    toaster.show(contextLimitMessage, type = ToastType.Error)
                    if (!contextAllowsSend(contextLimitReached, contextLimitBlocksSend)) {
                        return@ChatInput
                    }
                }
                sendActions.send(interject = true)
            },
            onLongSendClick = {
                if (!hasMessages) inputExitRunning = true
                if (contextLimitReached) {
                    toaster.show(contextLimitMessage, type = ToastType.Error)
                    if (!contextAllowsSend(contextLimitReached, contextLimitBlocksSend)) {
                        return@ChatInput
                    }
                }
                // 编辑态写回那条消息；否则发出去但不触发回复
                if (!sendActions.submitEdit()) sendActions.send(answer = false)
            },
            onUpdateChatModel = vm::setChatModel,
            onUpdateAssistant = vm::updateAssistant,
            onMoreClick = {
                openPane(ChatSidePane.Files)
            },
            floatingBarItems = setting.displaySetting.floatingBarItems,
            onFloatingBarItemClick = { item ->
                when (item) {
                    // 聊天槽位只负责把会话列表拉出来；已经展开时不再点一下关掉
                    FloatingBarItem.CHAT -> if (!sidebarOpen) onOpenSidebar()
                    FloatingBarItem.MEDIA_CREATION -> navController.navigate(Screen.MediaCreationSessions)
                    FloatingBarItem.TRANSLATOR -> navController.navigate(Screen.Translator)
                    FloatingBarItem.ASSISTANT -> navController.navigate(Screen.Assistant)
                    FloatingBarItem.FAVORITE -> navController.navigate(Screen.Favorite)
                    FloatingBarItem.HISTORY -> navController.navigate(Screen.History)
                    FloatingBarItem.STATS -> navController.navigate(Screen.Stats)
                    FloatingBarItem.IMAGE_GEN -> navController.navigate(Screen.ImageGen)
                    FloatingBarItem.WORKSPACES -> navController.navigate(Screen.Workspaces)
                    FloatingBarItem.TERMINAL -> when {
                        floatingBarTerminalWorkspaces.isEmpty() ->
                            navController.navigate(Screen.Workspaces)

                        threePane -> paneTabs.open(ChatSidePane.Terminal)

                        floatingBarTerminalWorkspaces.size == 1 ->
                            navController.navigate(
                                Screen.WorkspaceTerminal(floatingBarTerminalWorkspaces.first().id)
                            )

                        else -> showTerminalPicker = true
                    }
                    FloatingBarItem.SEARCH -> navController.navigate(Screen.MessageSearch)
                    FloatingBarItem.SETTINGS -> navController.navigate(Screen.Setting)
                }
            },
            modifier = modifier,
            liftedBy = liftedBy,
            applyWindowInsets = applyWindowInsets,
            deferImeInset = deferImeInset,
            // 横屏三栏去掉输入区上方的渐变遮罩
            showScrim = !threePane,
            // 绘制阶段才读，滑动时只重画遮罩，不带着整页重组
            scrimAlpha = scrimAlpha,
        )
    }

    Surface(
        color = LocalUiSlots.current.pageBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        AssistantBackground(
            setting = setting,
            modifier = Modifier
                .hazeSource(backgroundHazeState),
            // 横屏三栏不再盖渐变遮罩，背景图原样透出来
            scrim = !threePane,
        )
        // 横屏三栏：本轮改动那一行收进右栏，消息下面不再重复
        CompositionLocalProvider(
            LocalTurnChangesInSidePanel provides threePane,
            // 横屏时清单与引导收进右栏，输入区不再重复渲染
            LocalConversationStatusInSidePanel provides threePane,
        ) {
        Row(modifier = Modifier.fillMaxSize()) {
        // 横屏中列按定宽排，余量留给右栏；竖屏没有右栏，中列必须吃满整宽
        Box(
            modifier = if (chatWidth != null) {
                Modifier.width(chatWidth).fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            },
        ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column {
                    TopBar(
                        hazeState = backgroundHazeState,
                        settings = setting,
                        assistant = assistant,
                        chatModel = currentChatModel,
                        folderId = folderId,
                        folders = conversationFolders,
                        hasMessages = hasMessages,
                        threePane = threePane,
                        sidebarOpen = sidebarOpen,
                        onOpenSidebar = onOpenSidebar,
                        onNewChat = {
                            navigateToChatPage(navController)
                        },
                        onShowTaskTree = { openPane(ChatSidePane.TaskTree) },
                        onShowModelPicker = { openPane(ChatSidePane.ModelPicker) },
                        onSelectFolder = vm::updateFolder,
                        onCreateFolder = { showCreateFolderDialog = true },
                        onShowOverview = {
                            selectionState.cancel()
                            previewMode = true
                        },
                        onExport = {
                            // 跟消息上的分享一样进入多选，默认全选，用户可以再挑
                            previewMode = false
                            selectionState.start(conversationState.value.messageNodes.map { it.id })
                        },
                        onDelete = { showDeleteConfirm = true },
                        showCapsuleBackground = listScrolledUnderTopBar,
                        // 横屏的详情在右侧栏里，而侧栏是模态的、整高盖住顶栏：
                        // 这个提亮只在竖屏（顶栏底下展开）时看得见
                        detailsExpanded = showDetails,
                        onToggleDetails = { toggleDetailsPane() },
                    )
                    if (quotaProvider != null && quotaData != null) {
                        ChatQuotaStrip(
                            provider = quotaProvider,
                            data = quotaData,
                            expanded = quotaExpanded,
                            onToggle = { quotaExpanded = !quotaExpanded },
                            displaySetting = setting.displaySetting,
                            hazeState = backgroundHazeState,
                        )
                    }
                }
            },
            bottomBar = {
                // 输入框常驻在 bottomBar：空会话时只是一层位移把它抬到英雄卡下面，
                // 发送首条消息时它自己滑下去，列表的底部留白也始终按它的实际高度算。
                // 测量放在外层，位移加在里层，免得抬升量把自己量进去形成反馈。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 底栏钉在窗口底部，这条边不随输入区高度动，适合当抬升量的基准
                        .onGloballyPositioned { inputBarBottomPx = it.boundsInRoot().bottom },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                // 空态：输入区顶边钉在英雄卡下面。栏高在绘制阶段现取，
                                // 高度动画的每一帧都能同一帧对齐，不会往回拽一帧。
                                // 离开空态时按同一份落点乘进度收回去，第一帧与发送前完全一致。
                                // 键盘 inset 不再进底栏高度，空态的输入区随英雄卡一起
                                // 只上移键盘高度的一半，这里在绘制期读同一个 inset 对齐。
                                // 位移取整：半像素平移会让玻璃和文字在每帧重新采样，出现轻微闪烁
                                val heroShift = -(imeInsets.getBottom(this) / 2f)
                                    .roundToInt().toFloat()
                                val emptyLift = (size.height + emptyHeroBottomPx +
                                    emptyAnchorGapPx - inputBarBottomPx).roundToInt() + heroShift
                                translationY = when {
                                    !emptyLayoutReady -> 0f
                                    !hasMessages -> emptyLift
                                    // 只有新会话发出首条消息时才从这里滑下去；
                                    // 已有会话（或删空后重发）没有空态那一段，直接贴底
                                    animateInputToBottom ->
                                        emptyLift * (1f - inputExitProgress.value)
                                    else -> 0f
                                }
                                alpha = if (hasMessages || emptyLayoutReady) 1f else 0f
                                renderEffect = inputBlurSteps.at(inputBlur.value)
                            },
                    ) {
                        ChatInputContent(
                            todos = todos,
                            deferImeInset = !hasMessages || inputExitRunning,
                            scrimAlpha = { inputScrimAlpha },
                        )
                    }
                }
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (hasMessages) {
                    // 普通消息和子代理通知共用同一段删除判断，生成中一律拦下
                    val deleteMessage: (UIMessage) -> Unit = { message ->
                        if (loadingJob != null) {
                            vm.showDeleteBlockedWhileGeneratingError()
                        } else {
                            vm.deleteMessage(message)
                        }
                    }
                    ChatList(
                        innerPadding = innerPadding,
                        conversationState = conversationState,
                        state = chatListState,
                        previewListState = previewListState,
                        selectionState = selectionState,
                        loading = loadingJob != null,
                        generationSpeed = generationSpeed,
                        compressing = sessionActivity == SessionActivity.Compressing,
                        onCreateNewChat = onCreateNewChat,
                        processingStatus = processingStatus,
                        previewMode = previewMode,
                        onPreviewModeChange = { previewMode = it },
                        settings = setting,
                        bubbleHazeState = backgroundHazeState,
                        contentSource = backgroundHazeState,
                        errors = errors,
                        onDismissError = onDismissError,
                        onClearAllErrors = onClearAllErrors,
                        onRegenerate = {
                            vm.regenerateAtMessage(it)
                        },
                        onEdit = {
                            inputState.editingMessage = it.id
                            inputState.setContents(it.parts)
                        },
                        onEditSubAgentNotice = { message, text ->
                            // 子代理通知的正文改写：直接写回会话，不进输入框的编辑状态
                            vm.handleMessageEdit(
                                parts = listOf(UIMessagePart.Text(text)),
                                messageId = message.id,
                            )
                        },
                        onForkMessage = {
                            scope.launch {
                                val fork = vm.forkMessage(message = it)
                                navigateToChatPage(navController, chatId = fork.id)
                            }
                        },
                        onDelete = deleteMessage,
                        onDeleteSubAgentNotice = deleteMessage,
                        onUpdateMessage = { newNode ->
                            vm.selectMessageNode(newNode.id, newNode.selectIndex)
                        },
                        onClickSuggestion = { suggestion ->
                            inputState.editingMessage = null
                            inputState.setMessageText(suggestion)
                        },
                        onTranslate = { message, locale ->
                            vm.translateMessage(message, locale)
                        },
                        onClearTranslation = { message ->
                            vm.clearTranslationField(message.id)
                        },
                        onJumpToMessage = { index ->
                            previewMode = false
                            scope.launch {
                                // 预览页滑出、主列表重新挂载都在这一帧，等下一次测量再定位，
                                // 现在算距离用的是预览页那份还没挂上的布局
                                chatListState.requestScrollToItem(index)
                            }
                        },
                        onToolApproval = { toolCallId, approved, reason ->
                            vm.handleToolApproval(toolCallId, approved, reason)
                        },
                        onToolAnswer = { toolCallId, answer ->
                            vm.handleToolAnswer(toolCallId, answer)
                        },
                        onToggleFavorite = { node ->
                            vm.toggleMessageFavorite(node)
                        },
                        onConversationSystemPromptChange = { newPrompt ->
                            vm.updateConversationSystemPrompt(newPrompt)
                        },
                        emptyHero = null,
                        enteringMessageIds = enteringMessageIds,
                        onMessageEnterFinished = consumeMessageEnter,
                    )
                }
                // 空会话的英雄卡：列表挂上来之后它只负责退场（淡出 + 模糊收掉），
                // 输入框早在 bottomBar 上滑下去了，这里不再重复渲染输入区
                if (keepEmptyLayout) {
                    EmptyChatContent(
                        innerPadding = innerPadding,
                        assistant = assistant,
                        slogan = setting.displaySetting.emptyChatSlogan,
                        hazeState = backgroundHazeState,
                        visibleState = emptyHeroTransition,
                        contentAlpha = if (hasMessages || emptyLayoutReady) 1f else 0f,
                        scrollEnabled = !hasMessages,
                        onHeroPlaced = { bottom -> emptyHeroBottomPx = bottom },
                    )
                }
                // 叠层浮层的外侧点击：展开状态下点空白处收起，和点标题栏的开关等价。
                // 这一层盖住聊天内容，所以展开期间列表不跟着滚动——浮层是模态的，
                // 否则一下点按会一半落给浮层、一半落给后面的列表。
                if ((showDetails && !threePane) || quotaExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(showDetails, quotaExpanded) {
                                detectTapGestures {
                                    if (quotaExpanded) quotaExpanded = false
                                    if (showDetails && !threePane) showDetails = false
                                }
                            }
                    )
                }
                // 详情浮层盖在聊天内容上方：不占 Scaffold 的布局，展开和收起都不会推挤聊天区。
                MotionExpandCollapse(
                    visible = showDetails && !threePane,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding())
                        .fillMaxWidth(),
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    ) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    ) + fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                ) {
                    ConversationDetailsPanel(
                        assistant = assistant,
                        model = currentChatModel,
                        settings = setting,
                        workspaces = workspaces,
                        folderWorkspaceName = folderWorkspaceName,
                        contextTokens = contextTokens,
                        messageCount = messageCount,
                        previewState = requestPreviewState,
                        hazeState = backgroundHazeState,
                        onCollapse = { showDetails = false },
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                    )
                }
                // 限额看板也走叠层：展开只动这一层的高度，顶栏与内容层的可用高度不变。
                // 之前挂在顶栏里，展开动画每帧改一次顶栏高度，空会话页的英雄卡与输入栏
                // 要跟着逐帧重排，看起来整页在重建
                if (quotaProvider != null && quotaData != null) {
                    ChatQuotaStripDashboard(
                        provider = quotaProvider,
                        data = quotaData,
                        expanded = quotaExpanded,
                        running = loadingJob != null,
                        displaySetting = setting.displaySetting,
                        hazeState = backgroundHazeState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = innerPadding.calculateTopPadding()),
                    )
                }
            }
        }

        }
        // 横屏三栏：右栏是标签页式的看板，聊天区不再被弹层盖住
        if (threePane) {
            ChatSidePanes(
                panes = paneTabs.openPanes,
                selected = paneTabs.selected,
                onSelect = { paneTabs.select(it) },
                onOpen = { paneTabs.open(it) },
                onClose = { paneTabs.close(it) },
                onClosePane = { closePane() },
                onOpenConversation = { id ->
                    closePane()
                    navigateToChatPage(navController, chatId = id)
                },
                onResize = onChatResize,
                // 右栏吃掉剩余宽度：左栏收起时中列左移，空出来的位置全给它
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                // 右栏背后是页面背景，取背景那份 Haze 源；聊天内容的源只覆盖左边
                hazeState = backgroundHazeState,
                conversationState = conversationState,
                conversationId = conversationId,
                assistant = assistant,
                chatModel = currentChatModel,
                setting = setting,
                workspaces = workspaces,
                folderWorkspaceName = folderWorkspaceName,
                contextTokens = contextTokens,
                messageCount = messageCount,
                requestPreviewState = requestPreviewState,
                turnHasChanges = turnEditedFiles.isNotEmpty(),
                todos = todos,
                guidances = messageQueue.guidances,
                hideCompletedTodoCard = setting.displaySetting.hideCompletedTodoCard,
                running = loadingJob != null,
                inputState = inputState,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = onStartVoiceMode,
                chatModelListState = chatModelListState,
                backgroundTasks = backgroundTasks,
                vm = vm,
            )
        }
        }
        }

        // 竖屏的后台任务弹层；横屏那份由上面的右侧栏接管
        BackgroundTasksSheet(
            visible = showBackgroundTasks && !threePane,
            tasks = backgroundTasks,
            onStop = vm::stopBackgroundTask,
            onDismiss = { showBackgroundTasks = false },
        )

        if (showCompressAndSendDialog) {
            AnimatedAlertDialog(
                onDismissRequest = { showCompressAndSendDialog = false },
                title = { Text(stringResource(R.string.chat_page_compress_before_send_title)) },
                text = { Text(stringResource(R.string.chat_page_compress_before_send_desc)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showCompressAndSendDialog = false
                            sendActions.submit()
                        }
                    ) {
                        Text(stringResource(R.string.chat_page_compress_and_send))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCompressAndSendDialog = false }) {
                        Text(stringResource(R.string.assistant_page_cancel))
                    }
                },
            )
        }

        AnimatedModalBottomSheet(
            visible = showUsageSheet && !threePane,
            onDismissRequest = { showUsageSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_message_usage_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                ConversationUsageDetails(
                    messages = conversationState.value.currentMessages,
                    contextLength = currentChatModel?.effectiveContextWindow,
                )
            }
        }

        SubAgentTaskSheet(
            visible = showTaskTree && !threePane,
            conversationId = conversationId,
            hazeState = backgroundHazeState,
            onDismiss = { showTaskTree = false },
            onOpenConversation = { id ->
                showTaskTree = false
                navigateToChatPage(navController, chatId = id)
            },
        )

        if (showCreateFolderDialog) {
            var folderName by remember { mutableStateOf("") }
            AnimatedAlertDialog(
                onDismissRequest = { showCreateFolderDialog = false },
                title = { Text(stringResource(R.string.chat_page_create_folder)) },
                text = {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.chat_page_folder_name)) },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.createFolder(folderName)
                            showCreateFolderDialog = false
                        },
                        enabled = folderName.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.chat_page_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateFolderDialog = false }) {
                        Text(stringResource(R.string.chat_page_cancel))
                    }
                },
            )
        }

        RikkaConfirmDialog(
            show = showDeleteConfirm,
            title = stringResource(R.string.confirm_delete),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                showDeleteConfirm = false
                scope.launch {
                    vm.deleteConversation(conversationState.value).join()
                    navigateToChatPage(navController)
                }
            },
            onDismiss = { showDeleteConfirm = false },
            text = { Text(stringResource(R.string.chat_page_delete_conversation_confirm)) },
        )

        ChatFilesPickerSheet(
            visible = showFilesSheet && !threePane,
            inputState = inputState,
            setting = setting,
            conversationState = conversationState,
            folderWorkspaceName = folderWorkspaceName,
            assistant = assistant,
            chatModel = currentChatModel,
            vm = vm,
            attachmentPickerActions = attachmentPickerActions,
            onStartVoiceMode = onStartVoiceMode,
            onDismiss = { showFilesSheet = false },
        )

        TerminalWorkspacePickerSheet(
            visible = showTerminalPicker && !threePane,
            workspaces = floatingBarTerminalWorkspaces,
            onSelect = { workspaceId ->
                showTerminalPicker = false
                navController.navigate(Screen.WorkspaceTerminal(workspaceId))
            },
            onDismiss = { showTerminalPicker = false },
        )

        // 横屏用右侧栏里的那份，这里只在竖屏组合
        if (!threePane) {
            ModelListSheet(state = chatModelListState) { model ->
                vm.setChatModel(model)
            }
        }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    visible: Boolean,
    inputState: ChatInputState,
    setting: Settings,
    conversationState: State<Conversation>,
    folderWorkspaceName: String?,
    assistant: Assistant,
    chatModel: Model?,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedModalBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
    ) {
        ChatFilesPickerContent(
            inputState = inputState,
            setting = setting,
            conversationState = conversationState,
            folderWorkspaceName = folderWorkspaceName,
            assistant = assistant,
            chatModel = chatModel,
            vm = vm,
            attachmentPickerActions = attachmentPickerActions,
            onStartVoiceMode = onStartVoiceMode,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 附件、工作区、搜索、扩展这些入口的本体：不含外层弹层，横屏时整块放进右侧栏 */
@Composable
internal fun ChatFilesPickerContent(
    inputState: ChatInputState,
    setting: Settings,
    conversationState: State<Conversation>,
    folderWorkspaceName: String?,
    assistant: Assistant,
    chatModel: Model?,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
    // 弹层里保持 wrap：sheet 的高度跟着内容走，别被撑成整屏；侧栏里传 fillMaxSize
    modifier: Modifier = Modifier,
) {
    val conversation = conversationState.value
    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showCompressDialog = false
        onDismiss()
    }

    Box(modifier = modifier) {
        FilesPicker(
            conversation = conversation,
            folderWorkspaceName = folderWorkspaceName,
            state = inputState,
            assistant = assistant,
            chatModel = chatModel,
            mcpManager = vm.mcpManager,
            onCompressContext = vm::handleCompressContext,
            onUpdateAssistant = vm::updateAssistant,
            onUpdateWorkspaceCwd = vm::updateWorkspaceCwd,
            onUpdateSearchMode = { mode ->
                vm.updateSearch(
                    enableWebSearch = mode == SearchMode.LOCAL,
                    builtInSearch = mode == SearchMode.BUILT_IN,
                )
            },
            onToggleSearchService = { id ->
                vm.updateSettings { latest ->
                    val defaultId = latest.searchServices
                        .getOrNull(latest.searchServiceSelected)
                        ?.id
                        ?.toString()
                    val current = latest.enabledSearchServiceIds.ifEmpty {
                        if (defaultId != null) setOf(defaultId) else emptySet()
                    }
                    val next = when {
                        id !in current -> current + id
                        current.size > 1 -> current - id
                        else -> current
                    }
                    latest.copy(enabledSearchServiceIds = next)
                }
            },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
            onSketch = attachmentPickerActions.onSketch,
            onStartVoiceMode = if (
                setting.getSelectedASRProvider()?.supportsServerVadVoiceMode == true &&
                voiceState.phase == VoicePhase.Off
            ) {
                {
                    dismissAll()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onStartVoiceMode()
                }
            } else null,
        )
    }
}

// 横屏三栏的整体缩放：0.75 就是整体缩小 25%
private const val LANDSCAPE_UI_SCALE = 0.75f

// 横屏三栏左栏的宽度（缩放后的 dp）
private val LANDSCAPE_SIDEBAR_WIDTH = 300.dp

/** 悬浮栏「终端」槽位：绑定了多个工作区时，先让用户挑一个再进终端。 */
@Composable
private fun TerminalWorkspacePickerSheet(
    visible: Boolean,
    workspaces: List<WorkspaceEntity>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedModalBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.floating_bar_item_terminal),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            CardGroup {
                workspaces.forEach { workspace ->
                    item(
                        onClick = { onSelect(workspace.id) },
                        leadingContent = { CardGroupIcon(HugeIcons.ComputerTerminal01) },
                        supportingContent = { Text(workspace.workspaceDir()) },
                        headlineContent = { Text(workspace.name) },
                    )
                }
            }
        }
    }
}

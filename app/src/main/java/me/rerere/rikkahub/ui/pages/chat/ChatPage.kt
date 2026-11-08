package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.toast.ToastType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Flowchart01
import me.rerere.hugeicons.stroke.Layers01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Share04
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.transformers.collectLorebookInjections
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.GuidanceMode
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.effectiveContextWindow
import me.rerere.rikkahub.data.model.isContextLimitReached
import me.rerere.rikkahub.data.model.lastContextTokens
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.BackgroundShellStatus
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.ai.BackgroundTasksContent
import me.rerere.rikkahub.ui.components.ai.BackgroundTasksSheet
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.ChatTopBarCapsule
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.ModelListContent
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.ai.chatInputHaze
import me.rerere.rikkahub.ui.components.ui.ColumnResizeHandle
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.message.ConversationUsageDetails
import me.rerere.rikkahub.ui.components.message.EditedFilesList
import me.rerere.rikkahub.ui.components.message.LocalTurnChangesInSidePanel
import me.rerere.rikkahub.ui.components.message.LocalConversationStatusInSidePanel
import me.rerere.rikkahub.ui.components.message.editedFilesIn
import me.rerere.rikkahub.ui.components.message.usageTokenSpeed
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.glassSurfaceAlpha
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.chat.terminal.TerminalChatInput
import me.rerere.rikkahub.ui.pages.chat.terminal.TerminalChatTopBar
import me.rerere.rikkahub.ui.pages.chat.terminal.TerminalModelPickerDialog
import me.rerere.rikkahub.ui.pages.chat.terminal.TerminalOutputDialog
import me.rerere.rikkahub.ui.pages.chat.terminal.parseTerminalCommand
import me.rerere.rikkahub.ui.pages.chat.terminal.terminalHelpText
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.terminal.TopAppBar
import me.rerere.rikkahub.ui.theme.LocalUiSlots
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.TerminalPalette
import me.rerere.rikkahub.ui.theme.TerminalTheme
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPaddingValue
import me.rerere.rikkahub.ui.theme.appButtonShadow
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.writeClipboardText
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
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
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    // 新会话发出第一条消息前，跟随抽屉当前选中的文件夹（#2133）
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
    val selectedFolderId by drawerVm.selectedFolderId.collectAsStateWithLifecycle()
    val folders by drawerVm.folders.collectAsStateWithLifecycle()
    val folderBinding by vm.folderWorkspaceBinding.collectAsStateWithLifecycle()
    // 文件夹绑了工作区时，工作区入口只展示这份绑定，改要去抽屉的文件夹菜单里改。
    // 子代理会话自己没有分组，工作区来自父会话的文件夹，那个文件夹可能属于别的助手、这里查不到名字，
    // 所以空串表示「被文件夹接管，但名字未知」
    val folderWorkspaceName = remember(conversation.folderId, folders, folderBinding) {
        val ownFolder = folders.firstOrNull { it.id == conversation.folderId }
        when {
            ownFolder != null && ownFolder.allWorkspaceIds.isNotEmpty() -> ownFolder.name
            folderBinding.isNotEmpty -> ""
            else -> null
        }
    }
    LaunchedEffect(selectedFolderId) {
        vm.updateDraftFolder(selectedFolderId)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    // 背景层（AssistantBackground）单独一份 Haze 源：抽屉和浮层的玻璃都取它，
    // 取聊天列表会把正文一起糊进来。提到这一层是为了抽屉也拿得到
    val backgroundHazeState = rememberHazeState()
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(imeVisible) {
        if (!imeVisible) {
            focusManager.clearFocus(force = true)
        }
    }

    // Handle back press when drawer is open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // Clear input focus so popup transitions cannot reopen the keyboard.
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
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
        if (threePaneLayout && drawerState.isOpen) {
            drawerState.close()
        }
    }

    val openSettingsFromDrawer: () -> Unit = {
        // 不先收抽屉：设置页整屏盖上来时它跟着一起退场，
        // 先滑回去再推设置页等于两段动画，反而拖
        navController.navigate(Screen.Setting)
    }
    val startNewChat: () -> Unit = {
        // 横屏三栏没有模态抽屉可收；竖屏收一下，免得新会话页还被挡着
        if (!threePaneLayout) scope.launch { drawerState.close() }
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
    LaunchedEffect(nodeId, conversation.messageNodes.size) {
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                    val sidebarWidth by animateDpAsState(
                        targetValue = if (sidebarOpen) sidebarWidthDp.dp else 0.dp,
                        // 拖动时不要弹簧，手柄和边缘之间不能有一帧的滞后
                        animationSpec = if (sidebarResizing) {
                            snap()
                        } else {
                            MaterialTheme.motionScheme.defaultSpatialSpec()
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
                                current = conversation,
                                vm = vm,
                                settings = setting,
                                onNewChat = startNewChat,
                                onOpenSettings = openSettingsFromDrawer,
                                // 不传 onCloseDrawer：点会话时左栏要留着，它是常驻列不是抽屉
                                avatarHero = sidebarOpen,
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
                        conversation = conversation,
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
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        onNewChat = startNewChat,
                        onOpenSettings = openSettingsFromDrawer,
                        onCloseDrawer = { drawerState.close() },
                        // 收起状态的模态抽屉在屏幕外，这时别让头像参与转场
                        avatarHero = drawerState.isOpen,
                        hazeState = backgroundHazeState,
                    )
                }
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    onCreateNewChat = startNewChat,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    folderWorkspaceName = folderWorkspaceName,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    assistant = assistant,
                    currentChatModel = currentChatModel,
                    threePane = false,
                    sidebarOpen = drawerState.isOpen,
                    onOpenSidebar = { scope.launch { drawerState.open() } },
                    backgroundHazeState = backgroundHazeState,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }
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
    conversation: Conversation,
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
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    // currentMessages 是 getter，每次访问都新建一份列表；同一帧里有多处要用，算一次就够。
    // 它只依赖 messageNodes，按节点列表做键不会漏掉分支切换。
    val currentMessages = remember(conversation.messageNodes) { conversation.currentMessages }

    // 上下文占用取最近一次回复的真实用量, 到达模型的 MAX 门限后禁止再发送
    val contextTokens = remember(conversation.messageNodes) { conversation.lastContextTokens() }
    val contextLimitReached = isContextLimitReached(contextTokens, currentChatModel?.maxContextTokens)
    // 模型可以关掉强制拦截，此时只在发送时提醒一次
    val contextLimitBlocksSend = contextLimitReached && currentChatModel?.enforceMaxContext != false
    val contextLimitMessage = stringResource(R.string.chat_page_context_limit_reached)

    // Usage 看板里的最近一次请求速度
    val lastUsageMessage = remember(conversation.messageNodes) {
        currentMessages.lastOrNull { it.usage != null }
    }
    val usageTokenSpeed = remember(lastUsageMessage) { lastUsageMessage?.usageTokenSpeed() }
    val lorebookInjectionCount = remember(conversation.messageNodes, assistant.lorebookIds, setting.lorebooks) {
        collectLorebookInjections(currentMessages, assistant, setting.lorebooks).size
    }
    // 详情面板要显示工作区名字和路径，跟着仓库的列表数据走
    val workspaces by remember(workspaceRepository) { workspaceRepository.listFlow() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // 上下文已经爆了且开了自动压缩：点发送先确认，确认后由 ChatService 先压缩再发送
    var showCompressAndSendDialog by remember { mutableStateOf(false) }
    val submitInput: () -> Unit = {
        if (inputState.isEditing()) {
            vm.handleMessageEdit(
                parts = inputState.getContents(),
                messageId = inputState.editingMessage!!,
            )
            inputState.clearInput()
        } else if (vm.handleMessageSend(inputState.getContents())) {
            inputState.clearInput()
            scope.launch {
                delay(100.milliseconds)
                chatListState.animateScrollToItem(
                    index = (chatListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
                )
            }
        }
        // 没被受理（关闭队列后正在压缩上下文、或队列暂停还压着旧消息）则保留输入框内容
    }

    var previewMode by rememberSaveable { mutableStateOf(false) }
    val previewListState = rememberLazyListState()
    val selectionState = rememberChatSelectionState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 顶栏「详情」展开状态：面板挂在顶栏下面，展开时把聊天内容整体往下让
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    var showFilesSheet by remember { mutableStateOf(false) }
    var showTaskTree by remember { mutableStateOf(false) }
    var showUsageSheet by remember { mutableStateOf(false) }
    var showTerminalModelPicker by remember { mutableStateOf(false) }
    val chatModelListState = rememberModelListState(
        modelId = assistant.chatModelId,
        providers = setting.providers,
        type = ModelType.CHAT,
    )
    var terminalOutput by remember { mutableStateOf<String?>(null) }
    // 横屏三栏的右栏：一个看板一个标签页，顺序就是标签条上的顺序
    var openPaneNames by rememberSaveable { mutableStateOf(listOf(ChatSidePane.Details.name)) }
    var selectedPaneName by rememberSaveable { mutableStateOf<String?>(ChatSidePane.Details.name) }
    // 后台任务弹层从输入框里提到这一层：横屏要能把它放进右侧栏
    var showBackgroundTasks by remember { mutableStateOf(false) }
    val backgroundTasks by vm.backgroundTasks.collectAsStateWithLifecycle()
    val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()

    val openPanes = remember(openPaneNames) {
        openPaneNames.mapNotNull { name -> ChatSidePane.entries.firstOrNull { it.name == name } }
    }
    val selectedPane = remember(selectedPaneName, openPanes) {
        selectedPaneName?.let { name -> openPanes.firstOrNull { it.name == name } }
    }

    // 对标签的操作一律直接读状态，不读这一帧的 openPanes：连点两下时后者还是旧值
    fun openTab(pane: ChatSidePane) {
        if (openPaneNames.none { it == pane.name }) {
            openPaneNames = openPaneNames + pane.name
        }
        selectedPaneName = pane.name
    }

    fun closeTab(pane: ChatSidePane) {
        val remaining = openPaneNames.filterNot { it == pane.name }
        openPaneNames = remaining
        if (selectedPaneName == pane.name || selectedPaneName !in remaining) {
            selectedPaneName = remaining.lastOrNull()
        }
    }

    // 打开某个覆盖层：横屏进右栏的标签页，竖屏还是原来的弹层 / 对话框
    fun openPane(pane: ChatSidePane) {
        if (threePane) {
            openTab(pane)
            return
        }
        when (pane) {
            ChatSidePane.Details -> showDetails = true
            ChatSidePane.Usage -> showUsageSheet = true
            ChatSidePane.Files -> showFilesSheet = true
            // 工作区文件只在横屏右栏里出现；竖屏从输入框的附件 / 工作区入口进
            ChatSidePane.Workspace -> Unit
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
        val name = selectedPaneName?.takeIf { it in openPaneNames } ?: openPaneNames.firstOrNull()
        name?.let { target -> ChatSidePane.entries.firstOrNull { it.name == target } }
            ?.let { closeTab(it) }
    }

    // 顶栏「详情」：横屏是右栏的标签页，竖屏还是顶栏底下那块的展开
    fun toggleDetailsPane() {
        if (threePane) {
            if (selectedPaneName == ChatSidePane.Details.name) {
                closeTab(ChatSidePane.Details)
            } else {
                openTab(ChatSidePane.Details)
            }
        } else {
            showDetails = !showDetails
        }
    }

    fun dismissFiles() {
        if (threePane) {
            if (openPaneNames.contains(ChatSidePane.Files.name)) closeTab(ChatSidePane.Files)
        } else {
            showFilesSheet = false
        }
    }

    // 本轮改动：横屏收进右栏。一轮里第一次改文件时把标签页开出来，但不抢当前看板
    val lastAssistantMessage = remember(currentMessages) {
        currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
    }
    val turnEditedFiles = remember(lastAssistantMessage) {
        lastAssistantMessage?.let { editedFilesIn(it.parts) }.orEmpty()
    }
    var lastAutoOpenedTurn by remember { mutableStateOf<Uuid?>(null) }
    LaunchedEffect(threePane, lastAssistantMessage?.id, turnEditedFiles) {
        if (!threePane || turnEditedFiles.isEmpty()) return@LaunchedEffect
        val turnId = lastAssistantMessage?.id ?: return@LaunchedEffect
        // 同一轮不重复开：用户手动关掉「本轮改动」以后别再弹回来
        if (lastAutoOpenedTurn == turnId) return@LaunchedEffect
        lastAutoOpenedTurn = turnId
        if (openPaneNames.none { it == ChatSidePane.Changes.name }) {
            openPaneNames = openPaneNames + ChatSidePane.Changes.name
        }
    }

    // 清单与引导：横屏收进右栏，出现时把标签页开出来，不抢当前看板
    val hasStatusContent = conversation.todos.isNotEmpty() || messageQueue.guidances.isNotEmpty()
    LaunchedEffect(threePane, hasStatusContent) {
        if (!threePane || !hasStatusContent) return@LaunchedEffect
        if (openPaneNames.none { it == ChatSidePane.Status.name }) {
            openPaneNames = openPaneNames + ChatSidePane.Status.name
        }
    }

    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { dismissFiles() },
    )
    val allowAudioVideoAttachments =
        currentChatModel?.findProvider(setting.providers) is ProviderSetting.Google

    // 终端与普通输入共用的发送入口：模型、上下文门限检查都走这里
    val sendInput: () -> Unit = {
        if (currentChatModel == null) {
            toaster.show(context.getString(R.string.media_creation_page_select_model_first), type = ToastType.Error)
        } else if (contextLimitBlocksSend && assistant.autoCompress) {
            showCompressAndSendDialog = true
        } else if (contextLimitReached) {
            toaster.show(contextLimitMessage, type = ToastType.Error)
            if (contextLimitBlocksSend) Unit else submitInput()
        } else {
            submitInput()
        }
    }

    val sendInterject: () -> Unit = interject@{
        if (contextLimitReached) {
            toaster.show(contextLimitMessage, type = ToastType.Error)
            if (contextLimitBlocksSend) return@interject
        }
        if (vm.handleMessageSend(inputState.getContents(), interject = true)) {
            inputState.clearInput()
            scope.launch {
                delay(100.milliseconds)
                chatListState.animateScrollToItem(
                    index = (chatListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
                )
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

    fun terminalMessages(): List<me.rerere.ai.ui.UIMessage> = currentMessages

    fun terminalMessageAt(arg: String, defaultAssistant: Boolean = false): me.rerere.ai.ui.UIMessage? {
        val index = arg.trim().toIntOrNull()
        return if (index != null) {
            terminalMessages().getOrNull(index)
        } else if (defaultAssistant) {
            terminalMessages().lastOrNull { it.role == MessageRole.ASSISTANT }
        } else {
            terminalMessages().lastOrNull()
        }
    }

    fun terminalConversationMarkdown(): String = buildString {
        appendLine("# ${conversation.title.ifBlank { "conversation" }}")
        appendLine()
        terminalMessages().forEach { message ->
            val role = when (message.role) {
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT -> "Assistant"
                else -> "System"
            }
            appendLine("## $role")
            appendLine()
            appendLine(message.toText())
            appendLine()
        }
    }

    fun runTerminalCommand(raw: String) {
        val command = parseTerminalCommand(raw) ?: return
        var clearInput = true
        when (command.name) {
            "help" -> terminalOutput = terminalHelpText { context.getString(it) }
            "status" -> terminalOutput = buildString {
                appendLine("assistant : ${assistant.name.ifBlank { "assistant" }}")
                appendLine("model     : ${currentChatModel?.displayName ?: "no-model"}")
                appendLine("context   : ${contextTokens ?: 0} / ${currentChatModel?.maxContextTokens ?: "-"}")
                appendLine("messages  : ${currentMessages.size}")
                appendLine("speed     : ${usageTokenSpeed?.let { "%.1f tok/s".format(it) } ?: "-"}")
                append("queue     : ${messageQueue.messages.size}")
            }
            "model" -> {
                if (command.args.isBlank()) {
                    showTerminalModelPicker = true
                } else {
                    val models = setting.providers.flatMap { it.models }
                        .filter { it.type == ModelType.CHAT }
                    val query = command.args.lowercase()
                    val matches = models.filter {
                        it.displayName.lowercase().contains(query) ||
                            it.modelId.lowercase().contains(query)
                    }
                    when {
                        matches.size == 1 -> {
                            vm.setChatModel(matches.first())
                            val picked = matches.first()
                            terminalOutput = "model -> ${picked.displayName.ifBlank { picked.modelId }}"
                        }
                        matches.isEmpty() -> terminalOutput = "no model matched: ${command.args}"
                        else -> terminalOutput = buildString {
                            appendLine("multiple matches, use a longer name:")
                            matches.take(20).forEach {
                                appendLine("  ${it.displayName.ifBlank { it.modelId }}  [${it.modelId}]")
                            }
                        }
                    }
                }
            }
            "new" -> navigateToChatPage(navController)
            "messages" -> terminalOutput = buildString {
                terminalMessages().forEachIndexed { index, message ->
                    val role = if (message.role == MessageRole.USER) "U" else "A"
                    val preview = message.toText().lineSequence().firstOrNull().orEmpty().take(72)
                    appendLine("[%02d] %s %s".format(index, role, preview))
                }
            }
            "send" -> {
                if (command.args.isBlank()) {
                    terminalOutput = "usage: /send <text>"
                } else {
                    vm.handleMessageSend(listOf(UIMessagePart.Text(command.args)))
                    scope.launch {
                        delay(100.milliseconds)
                        chatListState.animateScrollToItem(
                            index = (chatListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
                        )
                    }
                }
            }
            "retry" -> {
                val message = terminalMessageAt(command.args, defaultAssistant = true)
                if (message == null) {
                    terminalOutput = "no assistant message to regenerate"
                } else {
                    vm.regenerateAtMessage(message)
                }
            }
            "edit" -> {
                val message = terminalMessageAt(command.args)
                if (message == null) {
                    terminalOutput = "message not found: ${command.args}"
                } else {
                    inputState.editingMessage = message.id
                    inputState.setContents(message.parts)
                    clearInput = false
                }
            }
            "delete" -> {
                val message = terminalMessageAt(command.args)
                when {
                    message == null -> terminalOutput = "message not found: ${command.args}"
                    loadingJob != null -> vm.showDeleteBlockedWhileGeneratingError()
                    else -> vm.deleteMessage(message)
                }
            }
            "copy" -> {
                val message = terminalMessageAt(command.args)
                if (message == null) {
                    terminalOutput = "message not found: ${command.args}"
                } else {
                    context.copyMessageToClipboard(message)
                    toaster.show(context.getString(R.string.chat_page_message_copied), type = ToastType.Success)
                }
            }
            "branch" -> {
                val message = terminalMessageAt(command.args)
                if (message == null) {
                    terminalOutput = "message not found: ${command.args}"
                } else {
                    scope.launch {
                        val fork = vm.forkMessage(message)
                        navigateToChatPage(navController, chatId = fork.id)
                    }
                }
            }
            "compress" -> vm.handleCompressContext(command.args, keepRecentMessages = 0)
            "usage" -> openPane(ChatSidePane.Usage)
            "tree" -> openPane(ChatSidePane.TaskTree)
            "files" -> openPane(ChatSidePane.Files)
            "voice" -> onStartVoiceMode()
            "stop" -> vm.stopGeneration()
            "title" -> {
                if (command.args.isBlank()) {
                    terminalOutput = "usage: /title <text>"
                } else {
                    vm.updateTitle(command.args)
                    terminalOutput = "title -> ${command.args}"
                }
            }
            "pin" -> {
                vm.updatePinnedStatus(conversation)
                terminalOutput = if (conversation.isPinned) "unpinned" else "pinned"
            }
            "search" -> {
                val enabled = when (command.args.lowercase()) {
                    "on", "1", "true", "yes" -> true
                    "off", "0", "false", "no" -> false
                    else -> null
                }
                if (enabled == null) {
                    terminalOutput = "usage: /search on|off"
                } else {
                    vm.updateSearch(enableWebSearch = enabled, builtInSearch = enabled)
                    terminalOutput = "web search -> ${if (enabled) "on" else "off"}"
                }
            }
            "export" -> {
                val markdown = terminalConversationMarkdown()
                context.writeClipboardText(markdown)
                terminalOutput = "conversation copied as markdown (${markdown.length} chars)"
            }
            "save" -> {
                val markdown = terminalConversationMarkdown()
                val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
                val file = File(dir, "conversation-${conversation.id}-${System.currentTimeMillis()}.md")
                runCatching { file.writeText(markdown) }
                    .onSuccess { terminalOutput = "saved -> ${file.absolutePath}" }
                    .onFailure { terminalOutput = "save failed: ${it.message}" }
            }
            "assistant" -> {
                if (command.args.isBlank()) {
                    terminalOutput = buildString {
                        appendLine("assistants:")
                        setting.assistants.forEach { item ->
                            val mark = if (item.id == conversation.assistantId) "*" else " "
                            appendLine("$mark ${item.name.ifBlank { "assistant" }}  [${item.id}]")
                        }
                    }
                } else {
                    val query = command.args.lowercase()
                    val matches = setting.assistants.filter { it.name.lowercase().contains(query) }
                    when {
                        matches.size == 1 -> {
                            vm.moveConversationToAssistant(conversation, matches.first().id)
                            terminalOutput = "assistant -> ${matches.first().name}"
                        }
                        matches.isEmpty() -> terminalOutput = "no assistant matched: ${command.args}"
                        else -> terminalOutput = buildString {
                            appendLine("multiple matches:")
                            matches.take(20).forEach { appendLine("  ${it.name}") }
                        }
                    }
                }
            }
            "chats" -> navController.navigate(Screen.History)
            "favorite" -> navController.navigate(Screen.Favorite)
            "open" -> {
                val id = runCatching { Uuid.parse(command.args.trim()) }.getOrNull()
                if (id == null) {
                    terminalOutput = "usage: /open <conversation-id>\ncurrent: ${conversation.id}"
                } else {
                    navigateToChatPage(navController, chatId = id)
                }
            }
            "close" -> {
                scope.launch {
                    vm.deleteConversation(conversation)
                    navController.popBackStack()
                }
            }
            "fav" -> {
                val message = terminalMessageAt(command.args)
                val node = message?.let { target ->
                    conversation.messageNodes.firstOrNull { node -> node.messages.any { it.id == target.id } }
                }
                if (node == null) {
                    terminalOutput = "message not found: ${command.args}"
                } else {
                    val wasFavorite = node.isFavorite
                    vm.toggleMessageFavorite(node)
                    val label = if (wasFavorite) "unfavorited" else "favorited"
                    terminalOutput = "$label -> [${node.messages.first().id}]"
                }
            }
            "switch" -> {
                val node = conversation.messageNodes.lastOrNull { it.messages.size > 1 }
                val index = command.args.trim().toIntOrNull()
                if (node == null || index == null) {
                    terminalOutput = "usage: /switch <0..${node?.messages?.lastIndex ?: 0}>"
                } else {
                    vm.selectMessageNode(node.id, index.coerceIn(0, node.messages.lastIndex))
                    terminalOutput = "branch -> $index"
                }
            }
            "translate" -> {
                val message = terminalMessageAt(command.args, defaultAssistant = true)
                if (message == null) {
                    terminalOutput = "no assistant message to translate"
                } else {
                    vm.translateMessage(message, Locale.forLanguageTag(setting.translateTargetLanguage))
                }
            }
            "mcp" -> navController.navigate(Screen.SettingMcp)
            "workspace" -> openPane(ChatSidePane.Files)
            "settings" -> navController.navigate(Screen.Setting)
            "terminal" -> {
                when (command.args.lowercase()) {
                    "on", "1", "true" -> vm.updateSettings {
                        it.copy(displaySetting = it.displaySetting.copy(terminalStyle = true))
                    }
                    "off", "0", "false" -> vm.updateSettings {
                        it.copy(displaySetting = it.displaySetting.copy(terminalStyle = false))
                    }
                    else -> terminalOutput = "usage: /terminal on|off"
                }
            }
            "clear" -> inputState.clearInput()
            else -> terminalOutput = context.getString(R.string.chat_page_terminal_unknown_command, command.name)
        }
        if (clearInput && command.name != "clear") {
            inputState.clearInput()
        }
    }

    val completionProviders = remember(assistant.allWorkspaceIds, conversation.workspaceCwds, workspaceRepository) {
        assistant.allWorkspaceIds.map { workspaceId ->
            WorkspaceCompletionProvider(
                workspaceId = workspaceId.toString(),
                repository = workspaceRepository,
                currentCwd = conversation.workspaceCwds[workspaceId.toString()],
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
    ) {
        val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
        ChatInput(
            onStartVoiceMode = onStartVoiceMode,
            voiceState = voiceState,
            onStopVoiceMode = vm.voiceSession::stop,
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
            onUpdateContextMeterStyle = { style ->
                vm.updateSettings { latest ->
                    latest.copy(displaySetting = latest.displaySetting.copy(contextMeterStyle = style))
                }
            },
            injectedLorebookCount = lorebookInjectionCount,
            onShowUsage = { openPane(ChatSidePane.Usage) },
            hazeState = hazeState,
            completionProviders = completionProviders,
            onCancelClick = {
                vm.stopGeneration()
            },
            onSendClick = sendInput,
            onInterjectClick = {
                if (contextLimitReached) {
                    toaster.show(contextLimitMessage, type = ToastType.Error)
                    if (contextLimitBlocksSend) return@ChatInput
                }
                if (vm.handleMessageSend(inputState.getContents(), interject = true)) {
                    inputState.clearInput()
                    scope.launch {
                        delay(100.milliseconds)
                        chatListState.animateScrollToItem(
                            index = (chatListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
                        )
                    }
                }
            },
            onLongSendClick = {
                if (contextLimitReached) {
                    toaster.show(contextLimitMessage, type = ToastType.Error)
                    if (contextLimitBlocksSend) return@ChatInput
                }
                if (inputState.isEditing()) {
                    vm.handleMessageEdit(
                        parts = inputState.getContents(),
                        messageId = inputState.editingMessage!!,
                    )
                    inputState.clearInput()
                } else if (vm.handleMessageSend(content = inputState.getContents(), answer = false)) {
                    inputState.clearInput()
                    scope.launch {
                        delay(100.milliseconds)
                        chatListState.animateScrollToItem(
                            index = (chatListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
                        )
                    }
                }
            },
            onUpdateChatModel = vm::setChatModel,
            onUpdateAssistant = vm::updateAssistant,
            onMoreClick = {
                openPane(ChatSidePane.Files)
            },
            modifier = modifier,
            liftedBy = liftedBy,
            applyWindowInsets = applyWindowInsets,
            // 横屏三栏去掉输入区上方的渐变遮罩
            showScrim = !threePane,
        )
    }

    TerminalTheme(enabled = setting.displaySetting.terminalStyle) {
    Surface(
        color = if (setting.displaySetting.terminalStyle) {
            TerminalPalette.Background
        } else {
            LocalUiSlots.current.pageBackground
        },
        modifier = Modifier.fillMaxSize()
    ) {
        if (!setting.displaySetting.terminalStyle) {
            AssistantBackground(
                setting = setting,
                modifier = Modifier
                    .hazeSource(hazeState)
                    .hazeSource(backgroundHazeState),
                // 横屏三栏不再盖渐变遮罩，背景图原样透出来
                scrim = !threePane,
            )
        }
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
                    if (setting.displaySetting.terminalStyle) {
                        TerminalChatTopBar(
                            title = conversation.title,
                            assistantName = assistant.name,
                            modelName = currentChatModel?.displayName ?: "no-model",
                            contextTokens = contextTokens,
                            maxContextTokens = currentChatModel?.maxContextTokens,
                            compact = threePane,
                            onMenu = onOpenSidebar,
                            onNewChat = { navigateToChatPage(navController) },
                            onTaskTree = { openPane(ChatSidePane.TaskTree) },
                            onUsage = { openPane(ChatSidePane.Usage) },
                            onHelp = { terminalOutput = terminalHelpText { context.getString(it) } },
                        )
                    } else {
                        TopBar(
                            hazeState = backgroundHazeState,
                            settings = setting,
                            assistant = assistant,
                            chatModel = currentChatModel,
                            conversation = conversation,
                            threePane = threePane,
                            sidebarOpen = sidebarOpen,
                            onOpenSidebar = onOpenSidebar,
                            onNewChat = {
                                navigateToChatPage(navController)
                            },
                            onShowTaskTree = { openPane(ChatSidePane.TaskTree) },
                            onShowModelPicker = { openPane(ChatSidePane.ModelPicker) },
                            onShowOverview = {
                                selectionState.selecting = false
                                previewMode = true
                            },
                            onExport = {
                                // 跟消息上的分享一样进入多选，默认全选，用户可以再挑
                                previewMode = false
                                selectionState.start(conversation.messageNodes.map { it.id })
                            },
                            onDelete = { showDeleteConfirm = true },
                            showCapsuleBackground = listScrolledUnderTopBar,
                            // 横屏的详情在右侧栏里，而侧栏是模态的、整高盖住顶栏：
                            // 这个提亮只在竖屏（顶栏底下展开）时看得见
                            detailsExpanded = showDetails,
                            onToggleDetails = { toggleDetailsPane() },
                        )
                    }

                    // 详情面板从顶栏下方展开：高度动画带着聊天内容一起让位，收起时再退回
                    AnimatedVisibility(
                        visible = showDetails && !setting.displaySetting.terminalStyle && !threePane,
                        enter = expandVertically(
                            expandFrom = Alignment.Top,
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        ) + fadeIn(),
                        exit = shrinkVertically(
                            shrinkTowards = Alignment.Top,
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        ) + fadeOut(),
                    ) {
                        ConversationDetailsPanel(
                            assistant = assistant,
                            model = currentChatModel,
                            settings = setting,
                            workspaces = workspaces,
                            folderWorkspaceName = folderWorkspaceName,
                            contextTokens = contextTokens,
                            messageCount = currentMessages.size,
                            lorebookInjectionCount = lorebookInjectionCount,
                            hazeState = backgroundHazeState,
                            onCollapse = { showDetails = false },
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                        )
                    }
                }
            },
            bottomBar = {
                if (currentMessages.isNotEmpty()) {
                    if (setting.displaySetting.terminalStyle) {
                        TerminalChatInput(
                            state = inputState,
                            settings = setting,
                            chatModel = currentChatModel,
                            todos = conversation.todos,
                            contextTokens = contextTokens,
                            tokenSpeed = usageTokenSpeed,
                            loading = loadingJob != null,
                            queuedCount = messageQueue.messages.size,
                            onSubmit = sendInput,
                            onStop = vm::stopGeneration,
                            onFiles = { openPane(ChatSidePane.Files) },
                            onVoice = onStartVoiceMode,
                            onModel = { showTerminalModelPicker = true },
                            onUsage = { openPane(ChatSidePane.Usage) },
                            onCommand = { runTerminalCommand(it) },
                            assistant = assistant,
                            onUpdateAssistant = vm::updateAssistant,
                            onInterject = sendInterject,
                        )
                    } else {
                        ChatInputContent(todos = conversation.todos)
                    }
                }
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            if (currentMessages.isEmpty()) {
                EmptyChatContent(
                    innerPadding = innerPadding,
                    assistant = assistant,
                    slogan = setting.displaySetting.emptyChatSlogan,
                    hazeState = backgroundHazeState,
                    input = {
                        if (setting.displaySetting.terminalStyle) {
                            TerminalChatInput(
                                modifier = Modifier.fillMaxWidth(),
                                state = inputState,
                                settings = setting,
                                chatModel = currentChatModel,
                                todos = conversation.todos,
                                contextTokens = contextTokens,
                                tokenSpeed = usageTokenSpeed,
                                loading = loadingJob != null,
                                queuedCount = messageQueue.messages.size,
                                onSubmit = sendInput,
                                onStop = vm::stopGeneration,
                                onFiles = { openPane(ChatSidePane.Files) },
                                onVoice = onStartVoiceMode,
                                onModel = { showTerminalModelPicker = true },
                                onUsage = { openPane(ChatSidePane.Usage) },
                                onCommand = { runTerminalCommand(it) },
                                assistant = assistant,
                                onUpdateAssistant = vm::updateAssistant,
                                onInterject = sendInterject,
                            )
                        } else {
                            ChatInputContent(
                                todos = conversation.todos,
                                modifier = Modifier.fillMaxWidth(),
                                applyWindowInsets = false,
                            )
                        }
                    },
                )
            } else {
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
                    conversation = conversation,
                    state = chatListState,
                    previewListState = previewListState,
                    selectionState = selectionState,
                    loading = loadingJob != null,
                    onCreateNewChat = onCreateNewChat,
                    processingStatus = processingStatus,
                    previewMode = previewMode,
                    onPreviewModeChange = { previewMode = it },
                    settings = setting,
                    hazeState = hazeState,
                    bubbleHazeState = backgroundHazeState,
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
                    )
            }
        }

        }
        // 横屏三栏：右栏是标签页式的看板，聊天区不再被弹层盖住
        if (threePane) {
            ColumnResizeHandle(onDrag = onChatResize)
            ChatSidePanelColumn(
                panes = openPanes,
                selected = selectedPane,
                onSelect = { selectedPaneName = it.name },
                onOpen = { openTab(it) },
                onClose = { closeTab(it) },
                // 右栏吃掉剩余宽度：左栏收起时中列左移，空出来的位置全给它
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                // 右栏背后是页面背景，取背景那份 Haze 源；聊天内容的源只覆盖左边
                hazeState = backgroundHazeState,
            ) { pane ->
                when (pane) {
                    ChatSidePane.Details -> ConversationDetailsPanel(
                        assistant = assistant,
                        model = currentChatModel,
                        settings = setting,
                        workspaces = workspaces,
                        folderWorkspaceName = folderWorkspaceName,
                        contextTokens = contextTokens,
                        messageCount = currentMessages.size,
                        lorebookInjectionCount = lorebookInjectionCount,
                        hazeState = backgroundHazeState,
                        onCollapse = { closePane() },
                        embedded = true,
                        // 标签条已经有标题，内容里不再重复一行
                        showHeader = false,
                    )

                    ChatSidePane.TaskTree -> SubAgentTaskTree(
                        conversationId = conversation.id,
                        onOpenConversation = { id ->
                            closePane()
                            navigateToChatPage(navController, chatId = id)
                        },
                        modifier = Modifier.fillMaxSize(),
                        showTitle = false,
                    )

                    ChatSidePane.Status -> ConversationStatusPane(
                        todos = conversation.todos,
                        guidances = messageQueue.guidances,
                        assistant = assistant,
                        hideCompletedTodos = setting.displaySetting.hideCompletedTodoCard,
                        onTodosChange = vm::updateTodos,
                        onUpdateAssistant = vm::updateAssistant,
                        onGuidanceToggleMode = vm::toggleGuidanceMode,
                        onGuidanceRemove = vm::removeGuidance,
                        modifier = Modifier.fillMaxSize(),
                    )

                    ChatSidePane.Changes -> TurnChangesPane(
                        parts = lastAssistantMessage?.parts.orEmpty(),
                        assistant = assistant,
                        conversationId = conversation.id,
                        hasChanges = turnEditedFiles.isNotEmpty(),
                        modifier = Modifier.fillMaxSize(),
                    )

                    ChatSidePane.Usage -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ConversationUsageDetails(
                            messages = currentMessages,
                            contextLength = currentChatModel?.effectiveContextWindow,
                        )
                    }

                    ChatSidePane.Files -> ChatFilesPickerContent(
                        inputState = inputState,
                        setting = setting,
                        conversation = conversation,
                        folderWorkspaceName = folderWorkspaceName,
                        assistant = assistant,
                        chatModel = currentChatModel,
                        vm = vm,
                        attachmentPickerActions = attachmentPickerActions,
                        onStartVoiceMode = onStartVoiceMode,
                        onDismiss = { closePane() },
                        modifier = Modifier.fillMaxSize(),
                    )

                    ChatSidePane.Workspace -> WorkspaceFilesPane(
                        workspaceIds = assistant.allWorkspaceIds.map { it.toString() },
                        workspaces = workspaces,
                        modifier = Modifier.fillMaxSize(),
                    )

                    ChatSidePane.ModelPicker -> ModelListContent(
                        state = chatModelListState,
                        onSelect = { model -> vm.setChatModel(model) },
                        onDismiss = { closePane() },
                        modifier = Modifier.fillMaxSize(),
                    )

                    ChatSidePane.BackgroundTasks -> BackgroundTasksContent(
                        tasks = backgroundTasks,
                        onStop = vm::stopBackgroundTask,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                    )
                }
            }
        }
        }
        }

        // 竖屏的后台任务弹层；横屏那份由上面的右侧栏接管
        if (showBackgroundTasks && !threePane) {
            BackgroundTasksSheet(
                tasks = backgroundTasks,
                onStop = vm::stopBackgroundTask,
                onDismiss = { showBackgroundTasks = false },
            )
        }

        if (showCompressAndSendDialog) {
            AnimatedAlertDialog(
                onDismissRequest = { showCompressAndSendDialog = false },
                title = { Text(stringResource(R.string.chat_page_compress_before_send_title)) },
                text = { Text(stringResource(R.string.chat_page_compress_before_send_desc)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showCompressAndSendDialog = false
                            submitInput()
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

        if (showUsageSheet && !threePane) {
            // 默认允许半展开，show() 会先停在半屏，得用手再拉一次；这里只留 Hidden / Expanded
            val usageSheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            )
            ModalBottomSheet(
                onDismissRequest = { showUsageSheet = false },
                sheetState = usageSheetState,
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
                        messages = currentMessages,
                        contextLength = currentChatModel?.effectiveContextWindow,
                    )
                }
            }
        }

        if (showTaskTree && !threePane) {
            SubAgentTaskSheet(
                conversationId = conversation.id,
                hazeState = backgroundHazeState,
                onDismiss = { showTaskTree = false },
                onOpenConversation = { id ->
                    showTaskTree = false
                    navigateToChatPage(navController, chatId = id)
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
                    vm.deleteConversation(conversation).join()
                    navigateToChatPage(navController)
                }
            },
            onDismiss = { showDeleteConfirm = false },
            text = { Text(stringResource(R.string.chat_page_delete_conversation_confirm)) },
        )

        if (showFilesSheet && !threePane) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                folderWorkspaceName = folderWorkspaceName,
                assistant = assistant,
                chatModel = currentChatModel,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = onStartVoiceMode,
                onDismiss = { showFilesSheet = false },
            )
        }

        terminalOutput?.let { output ->
            TerminalOutputDialog(
                title = "OUTPUT",
                content = output,
                onDismiss = { terminalOutput = null },
            )
        }

        if (showTerminalModelPicker) {
            TerminalModelPickerDialog(
                settings = setting,
                current = currentChatModel,
                onSelect = { model ->
                    vm.setChatModel(model)
                    showTerminalModelPicker = false
                    terminalOutput = "model -> ${model.displayName.ifBlank { model.modelId }}"
                },
                onDismiss = { showTerminalModelPicker = false },
            )
        }

        // 横屏用右侧栏里的那份，这里只在竖屏组合
        if (!threePane) {
            ModelListSheet(state = chatModelListState) { model ->
                vm.setChatModel(model)
            }
        }
    }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    folderWorkspaceName: String?,
    assistant: Assistant,
    chatModel: Model?,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = onDismiss,
    ) {
        ChatFilesPickerContent(
            inputState = inputState,
            setting = setting,
            conversation = conversation,
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
private fun ChatFilesPickerContent(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
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

/** 右栏的「本轮改动」标签页：和消息下面那一行同源，只是换了个位置 */
@Composable
private fun TurnChangesPane(
    parts: List<UIMessagePart>,
    assistant: Assistant,
    conversationId: Uuid,
    hasChanges: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!hasChanges) {
        Box(
            modifier = modifier.padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.chat_page_turn_changes_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 16.dp),
    ) {
        EditedFilesList(
            parts = parts,
            assistant = assistant,
            conversationId = conversationId,
            isLastMessage = true,
        )
    }
}

// 顶栏按钮统一尺寸：45dp 的按钮配 22.5dp 的图标（原来是 40dp/20dp，等比放大 12.5%），
// 按钮和图标保持 2:1，圆角、间距关系都不变。
private val TopBarButtonSize = 45.dp
private val TopBarButtonIconSize = 22.5.dp

@Composable
private fun TopBar(
    hazeState: HazeState,
    settings: Settings,
    assistant: Assistant,
    chatModel: Model?,
    conversation: Conversation,
    threePane: Boolean,
    sidebarOpen: Boolean,
    onOpenSidebar: () -> Unit,
    showCapsuleBackground: Boolean,
    onNewChat: () -> Unit,
    onShowTaskTree: () -> Unit,
    onShowModelPicker: () -> Unit,
    onShowOverview: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
) {
    val effect = settings.displaySetting.effectiveBackgroundEffect
    val terminal = settings.displaySetting.terminalStyle
    // 顶栏按钮是「玻璃底」，透明度读玻璃透明度，而不是气泡用的模糊不透明度
    val glassAlpha = settings.displaySetting.glassSurfaceAlpha
    fun effectColor(base: Color): Color = if (terminal) {
        base
    } else {
        when (effect) {
            BackgroundEffectType.OFF -> base
            BackgroundEffectType.BLUR -> base.copy(alpha = glassAlpha)
            BackgroundEffectType.GLASS -> Color.Transparent
        }
    }
    val effectBorder = if (!terminal && effect == BackgroundEffectType.BLUR) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else {
        null
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {},
        navigationIcon = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(
                    // 全宽模式下顶栏两侧不再留白，仍然保留状态栏与刘海的 inset
                    modifier = Modifier.padding(start = adaptiveHorizontalPaddingValue(12.dp)),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val navShape = when {
                        terminal -> RoundedCornerShape(2.dp)
                        LocalFullWidthComponents.current -> RoundedCornerShape(0.dp)
                        else -> CircleShape
                    }
                    val navColor = MaterialTheme.colorScheme.surfaceContainerLow
                    // 横屏三栏里这是左栏的开合，竖屏还是把抽屉拉出来
                    Surface(
                        onClick = onOpenSidebar,
                        modifier = Modifier
                            .size(TopBarButtonSize)
                            .appButtonShadow(navShape, 2.dp)
                            .clip(navShape)
                            .background(effectColor(navColor))
                            .then(
                                if (effectBorder != null) {
                                    Modifier.border(effectBorder, navShape)
                                } else {
                                    Modifier
                                }
                            )
                            .chatInputHaze(
                                hazeState = hazeState,
                                displaySetting = settings.displaySetting,
                                containerColor = navColor,
                                alpha = glassAlpha,
                                shape = navShape,
                            ),
                        shape = navShape,
                        color = Color.Transparent,
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                HugeIcons.Menu03,
                                "Messages",
                                modifier = Modifier.size(TopBarButtonIconSize),
                                // 左栏展开时按钮提亮，和详情按钮同一套反馈
                                tint = if (threePane && sidebarOpen) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        }
                    }

                    // 横屏三栏的顶栏只留菜单、新建和会话菜单：模型、详情、任务树都在右栏的标签页里
                    if (!threePane && chatModel != null) {
                        // 模型 + 供应商两个图标并成一个胶囊，高度和右侧按钮组一致
                        val modelPillShape = if (terminal) {
                            RoundedCornerShape(2.dp)
                        } else if (LocalFullWidthComponents.current) {
                            RoundedCornerShape(0.dp)
                        } else {
                            RoundedCornerShape(50)
                        }
                        val modelPillColor = MaterialTheme.colorScheme.surfaceContainerLow
                        val provider = chatModel.findProvider(settings.providers)
                        Row(
                            modifier = Modifier
                                .height(TopBarButtonSize)
                                .appButtonShadow(modelPillShape, 2.dp)
                                .clip(modelPillShape)
                                .background(effectColor(modelPillColor))
                                .then(
                                    if (effectBorder != null) {
                                        Modifier.border(effectBorder, modelPillShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .chatInputHaze(
                                    hazeState = hazeState,
                                    displaySetting = settings.displaySetting,
                                    containerColor = modelPillColor,
                                    alpha = glassAlpha,
                                    shape = modelPillShape,
                                )
                                .clickable(onClick = onShowModelPicker)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AutoAIIcon(name = chatModel.modelId, color = Color.Transparent)
                            if (provider != null) {
                                AutoAIIcon(name = provider.name, color = Color.Transparent)
                            }
                        }
                    }
                }
            }
        },
        actions = {
            val pillShape = if (terminal) {
                RoundedCornerShape(2.dp)
            } else if (LocalFullWidthComponents.current) {
                RoundedCornerShape(0.dp)
            } else {
                RoundedCornerShape(50)
            }
            val pillColor = MaterialTheme.colorScheme.surfaceContainerLow
            Row(
                modifier = Modifier.padding(end = adaptiveHorizontalPaddingValue(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .appButtonShadow(pillShape, 2.dp)
                        .clip(pillShape)
                        .background(effectColor(pillColor))
                        .then(
                            if (effectBorder != null) {
                                Modifier.border(effectBorder, pillShape)
                            } else {
                                Modifier
                            }
                        )
                        .chatInputHaze(
                            hazeState = hazeState,
                            displaySetting = settings.displaySetting,
                            containerColor = pillColor,
                            alpha = glassAlpha,
                            shape = pillShape,
                        )
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 详情和任务树在横屏是右栏的标签页，顶栏不再重复两个入口
                    if (!threePane) {
                        IconButton(onClick = onToggleDetails, modifier = Modifier.size(TopBarButtonSize)) {
                            Icon(
                                HugeIcons.Layers01,
                                stringResource(R.string.chat_page_details_button),
                                modifier = Modifier.size(TopBarButtonIconSize),
                                // 展开时按钮跟着提亮，顶栏一眼能看出详情面板是开着的
                                tint = if (detailsExpanded) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        }

                        IconButton(onClick = onShowTaskTree, modifier = Modifier.size(TopBarButtonSize)) {
                            Icon(
                                HugeIcons.Flowchart01,
                                stringResource(R.string.chat_task_tree_button),
                                modifier = Modifier.size(TopBarButtonIconSize),
                            )
                        }
                    }

                    IconButton(onClick = { onNewChat() }, modifier = Modifier.size(TopBarButtonSize)) {
                        Icon(HugeIcons.PencilEdit01, "New Message", modifier = Modifier.size(TopBarButtonIconSize))
                    }

                    ChatPageMenu(
                        enabled = conversation.messageNodes.isNotEmpty(),
                        onShowOverview = onShowOverview,
                        onExport = onExport,
                        onDelete = onDelete,
                    )

                }
            }
        },
    )
}


@Composable
private fun EmptyChatContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    slogan: String,
    hazeState: HazeState,
    input: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .navigationBarsPadding()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyChatHero(
                assistant = assistant,
                slogan = slogan,
                hazeState = hazeState,
            )
            Spacer(Modifier.height(16.dp))
            input()
        }
    }
}

@Composable
private fun TerminalEmptyChatHero(assistant: Assistant) {
    val rule = "─".repeat(160)
    val logo = listOf(
        "  _     _____ _   _ _   _ ____  ",
        " | |   |___ /| | | | | | | __ ) ",
        " | |     |_ \\| |_| | |_| |  _ \\ ",
        " | |___ ___) |  _  |  _  | |_) |",
        " |_____|____/|_| |_|_| |_|____/ ",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "┌─[ L3HUB // TERMINAL ]$rule",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalPalette.Green,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        logo.forEach { line ->
            Text(
                text = "│ $line",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                color = TerminalPalette.Green,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            text = "├$rule",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalPalette.Border,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = "│ [ OK ] session ready · ${assistant.name.ifBlank { "assistant" }}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalPalette.GreenDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "│ > waiting for input_",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalPalette.Green,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.chat_page_terminal_hint_commands),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalPalette.GreenDim,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = stringResource(R.string.chat_page_terminal_hint_tools),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalPalette.GreenDim,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = "└$rule",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalPalette.Border,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun EmptyChatHero(
    assistant: Assistant,
    slogan: String,
    hazeState: HazeState,
) {
    val settings = LocalSettings.current
    if (settings.displaySetting.terminalStyle) {
        TerminalEmptyChatHero(assistant = assistant)
        return
    }
    val effect = settings.displaySetting.effectiveBackgroundEffect
    val shape = RoundedCornerShape(28.dp)
    val baseColor = MaterialTheme.colorScheme.surfaceContainerLow
    val containerColor = when (effect) {
        BackgroundEffectType.OFF -> Color.Transparent
        BackgroundEffectType.BLUR -> baseColor.copy(alpha = settings.displaySetting.blurOpacity)
        BackgroundEffectType.GLASS -> Color.Transparent
    }
    Column(
        modifier = Modifier
            .padding(horizontal = 40.dp)
            .clip(shape)
            .background(containerColor)
            .then(
                if (effect == BackgroundEffectType.BLUR) {
                    Modifier.border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shape,
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (effect != BackgroundEffectType.OFF) {
                    Modifier.chatInputHaze(
                        hazeState = hazeState,
                        displaySetting = settings.displaySetting,
                        containerColor = baseColor,
                        shape = shape,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UIAvatar(
            name = assistant.name,
            value = assistant.avatar,
            avatarSize = 88.dp,
            shape = CircleShape,
        )
        Text(
            text = slogan.ifBlank { stringResource(R.string.chat_empty_default_slogan) },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// 顶栏右侧的会话菜单；空对话没有内容可概览、导出或删除，菜单项全部置灰
@Composable
private fun ChatPageMenu(
    enabled: Boolean,
    onShowOverview: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    @Composable
    fun MenuItem(
        text: String,
        icon: ImageVector,
        index: Int,
        count: Int,
        destructive: Boolean = false,
        onClick: () -> Unit,
    ) {
        DropdownMenuItem(
            text = { Text(text) },
            onClick = {
                expanded = false
                onClick()
            },
            shape = MenuDefaults.itemShape(index = index, count = count).shape,
            leadingIcon = { Icon(icon, contentDescription = null) },
            enabled = enabled,
            colors = if (destructive) {
                val errorColor = MaterialTheme.colorScheme.error
                MenuDefaults.itemColors(textColor = errorColor, leadingIconColor = errorColor)
            } else {
                MenuDefaults.itemColors()
            },
        )
    }

    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(TopBarButtonSize)) {
            Icon(
                HugeIcons.MoreVertical,
                stringResource(R.string.more_options),
                modifier = Modifier.size(TopBarButtonIconSize),
            )
        }
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                MenuItem(
                    text = stringResource(R.string.chat_page_conversation_map),
                    icon = HugeIcons.LeftToRightListBullet,
                    index = 0,
                    count = 2,
                    onClick = onShowOverview,
                )
                MenuItem(
                    text = stringResource(R.string.export_title),
                    icon = HugeIcons.Share04,
                    index = 1,
                    count = 2,
                    onClick = onExport,
                )
            }
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 1)) {
                MenuItem(
                    text = stringResource(R.string.delete),
                    icon = HugeIcons.Delete01,
                    index = 0,
                    count = 1,
                    destructive = true,
                    onClick = onDelete,
                )
            }
        }
    }
}

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberDrawerState
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
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.transformers.collectLorebookInjections
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.FloatingBarItem
import me.rerere.rikkahub.data.model.GuidanceMode
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.ai.tools.local.ASK_USER_TOOL_NAME
import me.rerere.rikkahub.data.model.effectiveContextWindow
import me.rerere.rikkahub.data.model.isContextLimitReached
import me.rerere.rikkahub.data.model.lastContextTokens
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.BackgroundShellStatus
import me.rerere.rikkahub.service.SessionActivity
import me.rerere.rikkahub.ui.components.ai.BackgroundTasksSheet
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.ChatQuotaStrip
import me.rerere.rikkahub.ui.components.ai.ChatQuotaStripDashboard
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.rememberChatQuotaStripData
import me.rerere.rikkahub.ui.components.ai.rememberGenerationSpeed
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.message.ConversationUsageDetails
import me.rerere.rikkahub.ui.components.message.AskUserPendingSummary
import me.rerere.rikkahub.ui.components.message.AskUserSheet
import me.rerere.rikkahub.ui.components.message.parseAskUserQuestions
import me.rerere.rikkahub.ui.components.message.LocalConversationStatusInSidePanel
import me.rerere.rikkahub.ui.components.message.tools.LocalToolDetailHost
import me.rerere.rikkahub.ui.components.message.tools.ToolDetailHostState
import me.rerere.rikkahub.ui.components.message.usageTokenSpeed
import me.rerere.rikkahub.ui.components.ui.ColumnResizeHandle
import me.rerere.rikkahub.ui.components.ui.LocalAdvancedAppearanceBusy
import me.rerere.rikkahub.ui.components.ui.LocalGlassHazeState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.modifier.MotionExpandCollapse
import me.rerere.rikkahub.ui.modifier.rememberMotionBlurSteps
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.theme.LocalUiSlots
import me.rerere.rikkahub.ui.theme.rememberSlideSpec
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.components.SaltModalSheet
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

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
    val sessionActivity by vm.sessionActivity.collectAsStateWithLifecycle()
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
    // 竖屏的侧边栏：Material 的模态抽屉，开关、边缘手势与手势仲裁都交给 DrawerState
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val advanced = setting.advancedAppearanceSetting.enableAdvancedAppearance
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

    // 抽屉的开关只走这一条入口。同一个目标在动画途中再点一次，原来的写法会把第二个
    // close / open 排到动画之后再跑一遍空动画；空的这一遍不移动位移，却把 settledValue
    // 与外层读到的 isOpen 错开一帧，之后点外部、返回、拖动都落在一个「已经关掉」的状态上，
    // 抽屉看起来就是卡在原地。
    fun requestDrawer(value: DrawerValue) {
        if (drawerState.targetValue == value) return
        scope.launch {
            if (value == DrawerValue.Open) drawerState.open() else drawerState.close()
        }
    }

    // 抽屉还开着（含打开动画途中、关闭动画途中）就接管返回键：
    // 正在关的时候再发一次 close 只会空转，所以那一下直接把剩下的动画落地，
    // 返回键不至于按下去什么也不发生，也不会漏到导航把页面退掉
    BackHandler(
        enabled = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open,
    ) {
        if (drawerState.targetValue == DrawerValue.Closed) {
            scope.launch { drawerState.snapTo(DrawerValue.Closed) }
        } else {
            requestDrawer(DrawerValue.Closed)
        }
    }

    // 收尾校正：动画结束、状态判定为关闭，位移却还停在打开位置时，说明这一轮 close 没落到
    // Closed 锚点（ModalNavigationDrawer 在布局阶段重算锚点会把进行中的动画改投到最近锚点）。
    // 不补这一下，抽屉从此停在原地，而所有入口都按「已关闭」短路失效
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.isAnimationRunning to drawerState.isOpen }
            .distinctUntilChanged()
            .collect { (running, open) ->
                if (running || open) return@collect
                if (drawerState.currentOffset > -1f) drawerState.snapTo(DrawerValue.Closed)
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
        if (threePaneLayout && drawerState.targetValue != DrawerValue.Closed) {
            drawerState.close()
        }
    }

    val openSettingsFromDrawer: () -> Unit = {
        // 不先收抽屉：设置页整屏盖上来时它跟着一起退场，
        // 先滑回去再推设置页等于两段动画，反而拖
        navController.navigate(Screen.Setting)
    }
    val startNewChat: () -> Unit = {
        // 横屏三栏没有模态抽屉可收；竖屏收一下，免得新会话页还被挡着。
        // 先收再换会话，两段动画分开走，不叠在一起
        if (!threePaneLayout) {
            scope.launch {
                // 已经在收的时候别再排一遍空动画，直接等这一轮收完
                if (drawerState.targetValue != DrawerValue.Closed) drawerState.close()
                navigateToChatPage(navController, Uuid.random())
            }
        } else {
            navigateToChatPage(navController, Uuid.random())
        }
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
    val hazeCapture = rememberChatHazeCaptureState(setting, chatListState, loadingJob != null || sessionActivity != null)
    val backgroundHazeState = rememberHazeState().takeIf { hazeCapture.plan.background }
    val chromeHazeState = rememberHazeState().takeIf { hazeCapture.plan.chrome }
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
    // 页面层的只读上下文与动作收成两个对象：下面两个布局分支只 copy 自己不同的字段，
    // 不再把二十多个实参各写一遍。
    val pageState = ChatPageUiState(
        inputState = inputState,
        loadingJob = loadingJob,
        sessionActivity = sessionActivity,
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
        errors = errors,
        backgroundHazeState = backgroundHazeState,
        chromeHazeState = chromeHazeState,
    )
    val pageActions = ChatPageActions(
        onStartVoiceMode = startVoiceMode,
        onCreateNewChat = startNewChat,
        onDismissError = { vm.dismissError(it) },
        onClearAllErrors = { vm.clearAllErrors() },
    )

    CompositionLocalProvider(
        LocalToolDetailHost provides toolDetailHost,
        LocalGlassHazeState provides backgroundHazeState,
        LocalAdvancedAppearanceBusy provides hazeCapture.busy,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        if (advanced) AssistantBackground(
            setting = setting,
            assistant = assistant,
            modifier = Modifier.chatHazeSources(backgroundHazeState, chromeHazeState),
            scrim = !threePaneLayout,
            animateGradient = loadingJob == null && sessionActivity == null,
        )
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
                        state = pageState.copy(
                            threePane = true,
                            sidebarOpen = sidebarOpen,
                            chatWidth = chatWidth,
                        ),
                        actions = pageActions.copy(
                            onOpenSidebar = { sidebarOpen = !sidebarOpen },
                            onChatResize = { delta ->
                                val maxChat = (totalWidth - (if (sidebarOpen) sidebarWidthDp else 0f).dp - 260.dp)
                                    .coerceAtLeast(320.dp)
                                // 读状态本身，不读这一帧的 chatWidth：一次手势里可能连来好几个事件
                                val current = if (chatWidthDp > 0f) chatWidthDp.dp else (totalWidth * 0.4f)
                                chatWidthDp = (current + delta).coerceIn(320.dp, maxChat).value
                            },
                        ),
                    )
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        currentConversationId = conversationId,
                        vm = vm,
                        settings = setting,
                        onNewChat = startNewChat,
                        onOpenSettings = openSettingsFromDrawer,
                        hazeState = backgroundHazeState,
                    )
                },
            ) {
                ChatPageContent(
                    state = pageState.copy(sidebarOpen = drawerState.isOpen),
                    actions = pageActions.copy(
                        onOpenSidebar = { requestDrawer(DrawerValue.Open) },
                    ),
                )
            }
        }
        ToolDetailSidePanel(host = toolDetailHost, hazeState = chromeHazeState)
        }
    }
}
@Composable
private fun ChatPageContent(
    state: ChatPageUiState,
    actions: ChatPageActions,
) {
    val scope = rememberCoroutineScope()
    var previewMode by rememberSaveable { mutableStateOf(false) }
    val advanced = state.setting.advancedAppearanceSetting.enableAdvancedAppearance
    // 顶栏、配额条/看板、会话详情浮层都压在消息正文上：统一取 chrome 源（背景 + 正文）。
    // 只给 background 源的话，浮层底下滚过去的正文糊不出来，着色一淡就透字。
    // 抽屉不用这一份 —— 它是整块侧栏、背后是页面背景，仍取 state.backgroundHazeState。
    val overlayHazeState = state.chromeHazeState
    // 顶栏胶囊的文件夹选单：按会话自己的助手取，不看抽屉里当前选中的助手
    val conversationFolders by state.vm.folders.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    // 页面这一层不直接读整份会话：流式输出每个 token 都会换一份 Conversation，
    // 直接读会让输入框、顶栏、背景、抽屉整页跟着重组。下面只派生页面真正关心的字段，
    // 结果没变就不会让页面重组；逐 token 的正文留给 ChatList 自己读 State。
    // conversationId / folderId / messageCount 由 ChatPage 算一次传进来，不在这里再挂一份。
    // 本轮生成速度：消息列表下方的加载图标与输入区上方的表盘共用这一份采样
    val generationSpeed = rememberGenerationSpeed(state.conversationState, state.loadingJob != null)
    val hasMessages by remember(state.conversationState) {
        derivedStateOf { state.conversationState.value.messageNodes.isNotEmpty() }
    }
    val messageIds by remember(state.conversationState) {
        derivedStateOf { state.conversationState.value.messageNodes.map { it.id } }
    }
    val todos by remember(state.conversationState) { derivedStateOf { state.conversationState.value.todos } }

    // 待回答的 ask_user 调用：抽屉关闭后显示在任务清单卡里，点击重新打开
    val pendingAskUserTools by remember(state.conversationState) {
        derivedStateOf {
            state.conversationState.value.currentMessages
                .flatMap { it.getTools() }
                .filter { it.isPending && it.toolName == ASK_USER_TOOL_NAME }
        }
    }
    val pendingAskUserSummaries by remember(pendingAskUserTools) {
        derivedStateOf {
            pendingAskUserTools.map { tool ->
                AskUserPendingSummary(
                    toolCallId = tool.toolCallId,
                    title = tool.parseAskUserQuestions().firstOrNull()?.question ?: "...",
                )
            }
        }
    }
    // 抽屉当前显示哪个调用；null 表示关闭。提交后工具不再 pending，抽屉自动收掉
    var askUserSheetCallId by rememberSaveable { mutableStateOf<String?>(null) }
    val askUserSheetTool = remember(pendingAskUserTools, askUserSheetCallId) {
        pendingAskUserTools.firstOrNull { it.toolCallId == askUserSheetCallId }
    }
    val openAskUserSheet: (String) -> Unit = { askUserSheetCallId = it }

    val workspaceCwds by remember(state.conversationState) {
        derivedStateOf { state.conversationState.value.workspaceCwds }
    }
    val customSystemPrompt by remember(state.conversationState) {
        derivedStateOf { state.conversationState.value.customSystemPrompt }
    }
    val newConversation by remember(state.conversationState) {
        derivedStateOf { state.conversationState.value.newConversation }
    }
    val contextTokens by remember(state.conversationState) {
        derivedStateOf { state.conversationState.value.lastContextTokens() }
    }
    val lastUsageMessage by remember(state.conversationState) {
        derivedStateOf { state.conversationState.value.currentMessages.lastOrNull { it.usage != null } }
    }
    val lastAssistantMessageId by remember(state.conversationState) {
        derivedStateOf {
            state.conversationState.value.currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id
        }
    }

    // 新消息入场的跟踪器放在这一层：空会话时 ChatList 还没挂载，
    // 放在列表里的话首条消息会被当成「打开会话时已经存在」而跳过动画。
    val messageEnterTracker = remember(state.conversationId) { MessageEnterTracker() }
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
    val inputLiftSpec = rememberSlideSpec<Float>(animated = !state.setting.displaySetting.disableAnimations)
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
                if (!state.setting.displaySetting.disableAnimations && emptyLayoutReady) {
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
    val contextLimitReached = isContextLimitReached(contextTokens, state.currentChatModel?.maxContextTokens)
    // 模型可以关掉强制拦截，此时只在发送时提醒一次
    val contextLimitBlocksSend = contextLimitReached && state.currentChatModel?.enforceMaxContext != false
    val contextLimitMessage = stringResource(R.string.chat_page_context_limit_reached)
    // 顶栏限额条看的是当前模型所在的渠道，切换模型后跟着换
    val currentProvider = remember(state.currentChatModel, state.setting.providers) {
        state.currentChatModel?.findProvider(state.setting.providers)
    }
    // 限额看板：折叠条留在顶栏，展开的看板叠在内容层上，展开时不改顶栏的布局高度。
    // 轮询只跑一份，折叠条的摘要和看板读同一份数据
    val quotaProvider = currentProvider.takeIf { state.setting.displaySetting.showQuotaBar }
    val quotaData = quotaProvider?.let { rememberChatQuotaStripData(it) }
    var quotaExpanded by rememberSaveable(currentProvider?.id?.toString()) { mutableStateOf(false) }

    // Usage 看板里的最近一次请求速度
    val usageTokenSpeed = remember(lastUsageMessage) { lastUsageMessage?.usageTokenSpeed() }
    // 每个 chunk 都会重建消息列表，世界书匹配要扫最近几轮的全文，不能放在组合里做。
    // 键只取节点数：流式正文每来一个 token 都重启一次扫描，前一次永远跑不完，
    // 派发到 Default 的工作全成了白跑。注入数只在下一轮请求前有意义，条数变化再算就够。
    val lorebookInjectionCount by produceState(
        0,
        state.conversationId,
        state.messageCount,
        state.assistant.lorebookIds,
        state.setting.lorebooks,
    ) {
        value = if (state.assistant.lorebookIds.isEmpty()) {
            0
        } else {
            withContext(Dispatchers.Default) {
                collectLorebookInjections(
                    state.conversationState.value.currentMessages,
                    state.assistant,
                    state.setting.lorebooks,
                ).size
            }
        }
    }
    // 详情面板要显示工作区名字和路径，跟着仓库的列表数据走
    val workspaces by remember(workspaceRepository) { workspaceRepository.listFlow() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    // 悬浮栏「终端」槽位只列当前会话真正绑定、且允许命令行的本地工作区
    val floatingBarTerminalWorkspaces = remember(state.assistant.allWorkspaceIds, workspaces) {
        val boundIds = state.assistant.allWorkspaceIds.map { it.toString() }.toSet()
        workspaces.filter { it.id in boundIds && it.supportsTerminal() }
    }

    // 上下文已经爆了且开了自动压缩：点发送先确认，确认后由 ChatService 先压缩再发送
    var showCompressAndSendDialog by remember { mutableStateOf(false) }
    // 四条发送路径共用的收尾动作（清空 + 100ms 后滚到底），见 ChatSendActions
    val sendActions = ChatSendActions(
        inputState = state.inputState,
        vm = state.vm,
        scope = scope,
        chatListState = state.chatListState,
    )

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
        modelId = state.assistant.chatModelId,
        providers = state.setting.providers,
        type = ModelType.CHAT,
    )
    // 横屏三栏的右栏：一个看板一个标签页，顺序就是标签条上的顺序（状态见 ChatPaneTabs）
    val paneTabs = rememberChatPaneTabs()
    // 后台任务弹层从输入框里提到这一层：横屏要能把它放进右侧栏
    var showBackgroundTasks by remember { mutableStateOf(false) }
    val backgroundTasks by state.vm.backgroundTasks.collectAsStateWithLifecycle()
    val messageQueue by state.vm.messageQueue.collectAsStateWithLifecycle()

    // 会话详情展开时才解析一次请求快照：工具、系统提示词和本轮注入都按当前配置重新算
    val detailsPanelVisible =
        (showDetails && !state.threePane) || (state.threePane && paneTabs.selected == ChatSidePane.Details)
    val requestPreviewState by produceState<RequestPreviewUiState>(
        RequestPreviewUiState.Idle,
        detailsPanelVisible,
        state.assistant,
        state.currentChatModel,
        customSystemPrompt,
        state.messageCount,
        workspaceCwds,
        state.setting.modeInjections,
        state.setting.lorebooks,
        state.setting.mcpServers,
        state.setting.assistants,
    ) {
        if (!detailsPanelVisible) {
            value = RequestPreviewUiState.Idle
            return@produceState
        }
        value = RequestPreviewUiState.Loading
        value = try {
            RequestPreviewUiState.Ready(state.vm.buildRequestPreview())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RequestPreviewUiState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    // 打开某个覆盖层：横屏进右栏的标签页，竖屏还是原来的弹层 / 对话框
    fun openPane(pane: ChatSidePane) {
        if (state.threePane) {
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
            // 清单与引导在竖屏还是输入区上方那张卡片
            ChatSidePane.Status -> Unit
        }
    }

    // 收掉右栏当前的标签页
    fun closePane() {
        if (!state.threePane) return
        paneTabs.closeSelected()
    }

    // 顶栏「详情」：横屏是右栏的标签页，竖屏还是顶栏底下那块的展开
    fun toggleDetailsPane() {
        if (state.threePane) {
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
        if (state.threePane) {
            if (paneTabs.openNames.contains(ChatSidePane.Files.name)) paneTabs.close(ChatSidePane.Files)
        } else {
            showFilesSheet = false
        }
    }

    // 清单与引导：横屏收进右栏，出现时把标签页开出来，不抢当前看板
    val hasStatusContent = todos.isNotEmpty() || messageQueue.guidances.isNotEmpty()
    LaunchedEffect(state.threePane, hasStatusContent) {
        if (!state.threePane || !hasStatusContent) return@LaunchedEffect
        paneTabs.ensureOpen(ChatSidePane.Status)
    }

    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = state.inputState,
        setting = state.setting,
        onAttachmentAdded = { dismissFiles() },
    )
    val allowAudioVideoAttachments =
        state.currentChatModel?.findProvider(state.setting.providers) is ProviderSetting.Google

    // 发送入口：模型、上下文门限检查都走这里，分支见 chatSendGate
    val sendInput: () -> Unit = {
        when (
            chatSendGate(
                hasModel = state.currentChatModel != null,
                contextLimitReached = contextLimitReached,
                contextLimitBlocksSend = contextLimitBlocksSend,
                autoCompress = state.assistant.autoCompress,
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

    val completionProviders = remember(state.assistant.allWorkspaceIds, workspaceCwds, workspaceRepository) {
        state.assistant.allWorkspaceIds.map { workspaceId ->
            WorkspaceCompletionProvider(
                workspaceId = workspaceId.toString(),
                repository = workspaceRepository,
                currentCwd = workspaceCwds[workspaceId.toString()],
            )
        }
    }

    TTSAutoPlay(vm = state.vm, setting = state.setting)

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
        val voiceState by state.vm.voiceSession.state.collectAsStateWithLifecycle()
        ChatInput(
            onStartVoiceMode = actions.onStartVoiceMode,
            voiceState = voiceState,
            onStopVoiceMode = state.vm.voiceSession::stop,
            // 空会话里输入区顶边钉在英雄卡下面，高度变化从顶部往下长；
            // 贴底之后再换回底部对齐
            pinContentToTop = !hasMessages,
            state = state.inputState,
            messageQueue = messageQueue,
            onRemoveQueuedMessage = state.vm::removeQueuedMessage,
            onBeginEditQueuedMessage = state.vm::beginEditQueuedMessage,
            onFinishEditQueuedMessage = state.vm::finishEditQueuedMessage,
            onResumeMessageQueue = state.vm::resumeMessageQueue,
            guideMessages = state.setting.guideMessages,
            // 预设是长期约束，默认多轮；手输的一次性提醒默认单轮，卡片上点一下就能换
            onAddGuidance = { preset ->
                state.vm.addGuidance(preset.title, preset.content, GuidanceMode.REPEAT)
            },
            onSendAsGuidance = {
                val text = state.inputState.textContent.text.toString().trim()
                if (text.isNotEmpty()) {
                    state.vm.addGuidance(title = "", content = text, mode = GuidanceMode.SINGLE)
                    state.inputState.clearText()
                    toaster.show(context.getString(R.string.guidance_added), type = ToastType.Info)
                }
            },
            onGuidanceToggleMode = state.vm::toggleGuidanceMode,
            onGuidanceRemove = state.vm::removeGuidance,
            runningBackgroundTasks = backgroundTasks.count { it.status == BackgroundShellStatus.RUNNING },
            onShowBackgroundTasks = { openPane(ChatSidePane.BackgroundTasks) },
            loading = state.loadingJob != null,
            settings = state.setting,
            assistant = state.assistant,
            chatModel = state.currentChatModel,
            todos = todos,
            onTodosChange = state.vm::updateTodos,
            pendingAskUser = pendingAskUserSummaries,
            onOpenAskUserSheet = openAskUserSheet,
            contextTokens = contextTokens,
            usageTokenSpeed = usageTokenSpeed,
            generationSpeed = generationSpeed,
            onUpdateContextMeterStyle = { style ->
                state.vm.updateSettings { latest ->
                    latest.copy(displaySetting = latest.displaySetting.copy(contextMeterStyle = style))
                }
            },
            injectedLorebookCount = lorebookInjectionCount,
            onShowUsage = { openPane(ChatSidePane.Usage) },
            hazeState = state.chromeHazeState,
            completionProviders = completionProviders,
            onCancelClick = {
                state.vm.stopGeneration()
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
            onUpdateChatModel = state.vm::setChatModel,
            onUpdateAssistant = state.vm::updateAssistant,
            onMoreClick = {
                openPane(ChatSidePane.Files)
            },
            floatingBarItems = state.setting.displaySetting.floatingBarItems,
            onFloatingBarItemClick = { item ->
                when (item) {
                    // 聊天槽位只负责把会话列表拉出来；已经展开时不再点一下关掉
                    FloatingBarItem.CHAT -> if (!state.sidebarOpen) actions.onOpenSidebar()
                    FloatingBarItem.MEDIA_CREATION -> state.navController.navigate(Screen.MediaCreationSessions)
                    FloatingBarItem.TRANSLATOR -> state.navController.navigate(Screen.Translator)
                    FloatingBarItem.ASSISTANT -> state.navController.navigate(Screen.Assistant)
                    FloatingBarItem.FAVORITE -> state.navController.navigate(Screen.Favorite)
                    FloatingBarItem.HISTORY -> state.navController.navigate(Screen.History)
                    FloatingBarItem.STATS -> state.navController.navigate(Screen.Stats)
                    FloatingBarItem.IMAGE_GEN -> state.navController.navigate(Screen.ImageGen)
                    FloatingBarItem.WORKSPACES -> state.navController.navigate(Screen.Workspaces)
                    FloatingBarItem.TERMINAL -> when {
                        floatingBarTerminalWorkspaces.isEmpty() ->
                            state.navController.navigate(Screen.Workspaces)

                        state.threePane -> paneTabs.open(ChatSidePane.Terminal)

                        floatingBarTerminalWorkspaces.size == 1 ->
                            state.navController.navigate(
                                Screen.WorkspaceTerminal(floatingBarTerminalWorkspaces.first().id)
                            )

                        else -> showTerminalPicker = true
                    }
                    FloatingBarItem.SEARCH -> state.navController.navigate(Screen.MessageSearch)
                    FloatingBarItem.SETTINGS -> state.navController.navigate(Screen.Setting)
                }
            },
            modifier = modifier,
            liftedBy = liftedBy,
            applyWindowInsets = applyWindowInsets,
            deferImeInset = deferImeInset,
            // 横屏三栏去掉输入区上方的渐变遮罩
            showScrim = !state.threePane,
            // 绘制阶段才读，滑动时只重画遮罩，不带着整页重组
            scrimAlpha = scrimAlpha,
        )
    }

    Surface(
        color = Color.Transparent,
        contentColor = if (advanced) MaterialTheme.colorScheme.onBackground else contentColorFor(LocalUiSlots.current.pageBackground),
        modifier = Modifier.fillMaxSize()
    ) {
        // The advanced root background spans the three panes. Keep the legacy background inside this surface.
        if (!advanced) AssistantBackground(
            setting = state.setting,
            modifier = Modifier.chatHazeSources(state.backgroundHazeState, state.chromeHazeState),
            scrim = !state.threePane,
            animateGradient = state.loadingJob == null,
        )
        // 横屏时清单与引导收进右栏，输入区不再重复渲染
        CompositionLocalProvider(
            LocalConversationStatusInSidePanel provides state.threePane,
        ) {
        Row(modifier = Modifier.fillMaxSize()) {
        // 横屏中列按定宽排，余量留给右栏；竖屏没有右栏，中列必须吃满整宽
        Box(
            modifier = if (state.chatWidth != null) {
                Modifier.width(state.chatWidth).fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            },
        ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column {
                    TopBar(
                        hazeState = overlayHazeState,
                        settings = state.setting,
                        assistant = state.assistant,
                        chatModel = state.currentChatModel,
                        folderId = state.folderId,
                        folders = conversationFolders,
                        hasMessages = hasMessages,
                        threePane = state.threePane,
                        sidebarOpen = state.sidebarOpen,
                        onOpenSidebar = actions.onOpenSidebar,
                        onNewChat = {
                            navigateToChatPage(state.navController)
                        },
                        onShowTaskTree = { openPane(ChatSidePane.TaskTree) },
                        onShowModelPicker = { openPane(ChatSidePane.ModelPicker) },
                        onSelectFolder = state.vm::updateFolder,
                        onCreateFolder = { showCreateFolderDialog = true },
                        onShowOverview = {
                            selectionState.cancel()
                            previewMode = true
                        },
                        onExport = {
                            // 跟消息上的分享一样进入多选，默认全选，用户可以再挑
                            previewMode = false
                            selectionState.start(state.conversationState.value.messageNodes.map { it.id })
                        },
                        onDelete = { showDeleteConfirm = true },
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
                            hazeState = overlayHazeState,
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
                        if (state.loadingJob != null) {
                            state.vm.showDeleteBlockedWhileGeneratingError()
                        } else {
                            state.vm.deleteMessage(message)
                        }
                    }
                    ChatList(
                        innerPadding = innerPadding,
                        conversationState = state.conversationState,
                        state = state.chatListState,
                        previewListState = previewListState,
                        selectionState = selectionState,
                        loading = state.loadingJob != null,
                        generationSpeed = generationSpeed,
                        compressing = state.sessionActivity == SessionActivity.Compressing,
                        onCreateNewChat = actions.onCreateNewChat,
                        processingStatus = state.processingStatus,
                        previewMode = previewMode, onPreviewModeChange = { previewMode = it },
                        settings = state.setting,
                        bubbleHazeState = state.backgroundHazeState,
                        contentSource = state.chromeHazeState,
                        errors = state.errors,
                        onDismissError = actions.onDismissError,
                        onClearAllErrors = actions.onClearAllErrors,
                        onRegenerate = {
                            state.vm.regenerateAtMessage(it)
                        },
                        onEdit = {
                            state.inputState.editingMessage = it.id
                            state.inputState.setContents(it.parts)
                        },
                        onEditSubAgentNotice = { message, text ->
                            // 子代理通知的正文改写：直接写回会话，不进输入框的编辑状态
                            state.vm.handleMessageEdit(
                                parts = listOf(UIMessagePart.Text(text)),
                                messageId = message.id,
                            )
                        },
                        onForkMessage = {
                            scope.launch {
                                val fork = state.vm.forkMessage(message = it)
                                navigateToChatPage(state.navController, chatId = fork.id)
                            }
                        },
                        onDelete = deleteMessage,
                        onDeleteSubAgentNotice = deleteMessage,
                        onUpdateMessage = { newNode ->
                            state.vm.selectMessageNode(newNode.id, newNode.selectIndex)
                        },
                        onClickSuggestion = { suggestion ->
                            state.inputState.editingMessage = null
                            state.inputState.setMessageText(suggestion)
                        },
                        onTranslate = { message, locale ->
                            state.vm.translateMessage(message, locale)
                        },
                        onClearTranslation = { message ->
                            state.vm.clearTranslationField(message.id)
                        },
                        onJumpToMessage = { index ->
                            previewMode = false
                            scope.launch {
                                // 预览页滑出、主列表重新挂载都在这一帧，等下一次测量再定位，
                                // 现在算距离用的是预览页那份还没挂上的布局
                                state.chatListState.requestScrollToItem(index)
                            }
                        },
                        onToolApproval = { toolCallId, approved, reason ->
                            state.vm.handleToolApproval(toolCallId, approved, reason)
                        },
                        onToolAnswer = { toolCallId, answer ->
                            state.vm.handleToolAnswer(toolCallId, answer)
                        },
                        onOpenAskUserSheet = openAskUserSheet,
                        onToggleFavorite = { node ->
                            state.vm.toggleMessageFavorite(node)
                        },
                        onConversationSystemPromptChange = { newPrompt ->
                            state.vm.updateConversationSystemPrompt(newPrompt)
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
                        assistant = state.assistant,
                        slogan = state.setting.displaySetting.emptyChatSlogan,
                        hazeState = state.backgroundHazeState,
                        visibleState = emptyHeroTransition,
                        contentAlpha = if (hasMessages || emptyLayoutReady) 1f else 0f,
                        scrollEnabled = !hasMessages,
                        onHeroPlaced = { bottom -> emptyHeroBottomPx = bottom },
                    )
                }
                // 叠层浮层的外侧点击：展开状态下点空白处收起，和点标题栏的开关等价。
                // 这一层盖住聊天内容，所以展开期间列表不跟着滚动——浮层是模态的，
                // 否则一下点按会一半落给浮层、一半落给后面的列表。
                if ((showDetails && !state.threePane) || quotaExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(showDetails, quotaExpanded) {
                                detectTapGestures {
                                    if (quotaExpanded) quotaExpanded = false
                                    if (showDetails && !state.threePane) showDetails = false
                                }
                            }
                    )
                }
                // 详情浮层盖在聊天内容上方：不占 Scaffold 的布局，展开和收起都不会推挤聊天区。
                MotionExpandCollapse(
                    visible = showDetails && !state.threePane,
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
                        assistant = state.assistant,
                        model = state.currentChatModel,
                        settings = state.setting,
                        workspaces = workspaces,
                        folderWorkspaceName = state.folderWorkspaceName,
                        contextTokens = contextTokens,
                        messageCount = state.messageCount,
                        previewState = requestPreviewState,
                        hazeState = overlayHazeState,
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
                        running = state.loadingJob != null,
                        displaySetting = state.setting.displaySetting,
                        hazeState = overlayHazeState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = innerPadding.calculateTopPadding()),
                    )
                }
            }
        }

        }
        // 横屏三栏：右栏是标签页式的看板，聊天区不再被弹层盖住
        if (state.threePane) {
            ChatSidePanes(
                panes = paneTabs.openPanes,
                selected = paneTabs.selected,
                onSelect = { paneTabs.select(it) },
                onOpen = { paneTabs.open(it) },
                onClose = { paneTabs.close(it) },
                onClosePane = { closePane() },
                onOpenConversation = { id ->
                    closePane()
                    navigateToChatPage(state.navController, chatId = id)
                },
                onResize = actions.onChatResize,
                // 右栏吃掉剩余宽度：左栏收起时中列左移，空出来的位置全给它
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                // 右栏背后是页面背景，取背景那份 Haze 源；聊天内容的源只覆盖左边
                hazeState = state.backgroundHazeState,
                conversationState = state.conversationState,
                conversationId = state.conversationId,
                assistant = state.assistant,
                chatModel = state.currentChatModel,
                setting = state.setting,
                workspaces = workspaces,
                folderWorkspaceName = state.folderWorkspaceName,
                contextTokens = contextTokens,
                messageCount = state.messageCount,
                requestPreviewState = requestPreviewState,
                todos = todos,
                guidances = messageQueue.guidances,
                pendingAskUser = pendingAskUserSummaries,
                onOpenAskUserSheet = openAskUserSheet,
                hideCompletedTodoCard = state.setting.displaySetting.hideCompletedTodoCard,
                running = state.loadingJob != null,
                inputState = state.inputState,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = actions.onStartVoiceMode,
                chatModelListState = chatModelListState,
                backgroundTasks = backgroundTasks,
                vm = state.vm,
            )
        }
        }
        }

        // 竖屏的后台任务弹层；横屏那份由上面的右侧栏接管
        BackgroundTasksSheet(
            visible = showBackgroundTasks && !state.threePane,
            tasks = backgroundTasks,
            onStop = state.vm::stopBackgroundTask,
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

        SaltModalSheet(
            visible = showUsageSheet && !state.threePane,
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
                    messages = state.conversationState.value.currentMessages,
                    contextLength = state.currentChatModel?.effectiveContextWindow,
                )
            }
        }

        SubAgentTaskSheet(
            visible = showTaskTree && !state.threePane,
            conversationId = state.conversationId,
            onDismiss = { showTaskTree = false },
            onOpenConversation = { id ->
                showTaskTree = false
                navigateToChatPage(state.navController, chatId = id)
            },
        )

        if (showCreateFolderDialog) {
            CreateChatFolderDialog(
                onDismissRequest = { showCreateFolderDialog = false },
                onCreate = { state.vm.createFolder(it) },
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
                    state.vm.deleteConversation(state.conversationState.value).join()
                    navigateToChatPage(state.navController)
                }
            },
            onDismiss = { showDeleteConfirm = false },
            text = { Text(stringResource(R.string.chat_page_delete_conversation_confirm)) },
        )

        ChatFilesPickerSheet(
            visible = showFilesSheet && !state.threePane,
            inputState = state.inputState,
            setting = state.setting,
            conversationState = state.conversationState,
            folderWorkspaceName = state.folderWorkspaceName,
            assistant = state.assistant,
            chatModel = state.currentChatModel,
            vm = state.vm,
            attachmentPickerActions = attachmentPickerActions,
            onStartVoiceMode = actions.onStartVoiceMode,
            onDismiss = { showFilesSheet = false },
        )

        TerminalWorkspacePickerSheet(
            visible = showTerminalPicker && !state.threePane,
            workspaces = floatingBarTerminalWorkspaces,
            onSelect = { workspaceId ->
                showTerminalPicker = false
                state.navController.navigate(Screen.WorkspaceTerminal(workspaceId))
            },
            onDismiss = { showTerminalPicker = false },
        )

        // 横屏用右侧栏里的那份，这里只在竖屏组合
        if (!state.threePane) {
            ModelListSheet(state = chatModelListState) { model ->
                state.vm.setChatModel(model)
            }
        }

        // ask_user 问答抽屉：作答与任务清单提示的入口都汇到这里
        AskUserSheet(
            tool = askUserSheetTool,
            onDismissRequest = { askUserSheetCallId = null },
            onSubmit = { toolCallId, answer ->
                askUserSheetCallId = null
                state.vm.handleToolAnswer(toolCallId, answer)
            },
        )
    }
}

// 横屏三栏的整体缩放：0.75 就是整体缩小 25%
private const val LANDSCAPE_UI_SCALE = 0.75f

// 横屏三栏左栏的宽度（缩放后的 dp）
private val LANDSCAPE_SIDEBAR_WIDTH = 300.dp

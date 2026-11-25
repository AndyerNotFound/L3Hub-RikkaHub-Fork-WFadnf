package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRStatus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.ContextMeterStyle
import me.rerere.rikkahub.data.datastore.InputLayoutStyle
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.data.datastore.getQuickMessagesOfAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.FloatingBarItem
import me.rerere.rikkahub.data.model.Guidance
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.TodoItem
import me.rerere.rikkahub.data.model.TodoStatus
import me.rerere.rikkahub.service.InboxState
import me.rerere.rikkahub.service.QueuedMessage
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.components.message.ConversationTodoCard
import me.rerere.rikkahub.ui.components.message.AskUserPendingSummary
import me.rerere.rikkahub.ui.components.message.LocalConversationStatusInSidePanel
import me.rerere.rikkahub.ui.components.message.TodoReminderUi
import me.rerere.rikkahub.ui.components.ui.KeepScreenOn
import me.rerere.rikkahub.ui.components.ui.advancedMaterial
import me.rerere.rikkahub.ui.components.ui.composerMaterial
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.modifier.motionBlurOnToggle
import me.rerere.rikkahub.ui.pages.chat.VoicePhase
import me.rerere.rikkahub.ui.pages.chat.VoiceSessionState
import me.rerere.rikkahub.ui.theme.LocalUiSlots
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import me.rerere.rikkahub.ui.theme.adaptiveShape
import me.rerere.rikkahub.ui.theme.motionContentSize
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import me.rerere.rikkahub.ui.theme.rememberFixedSpec
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec
import me.rerere.rikkahub.ui.theme.rememberSlideSpec
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.uuid.Uuid

@Composable
fun ChatInput(
    state: ChatInputState,
    loading: Boolean,
    settings: Settings,
    // 会话视角下的助手和模型：会话开始后以会话上固定的配置为准
    assistant: Assistant,
    chatModel: Model?,
    // 会话任务清单, 由 todo_write 工具维护, 显示在输入区最上方
    todos: List<TodoItem> = emptyList(),
    // 用户手动改清单（点完成、加一条、取消一条）; null 表示这张卡片只读
    onTodosChange: ((List<TodoItem>) -> Unit)? = null,
    // 当前会话已占用的上下文 token(最近一次回复的真实用量)
    contextTokens: Int? = null,
    usageTokenSpeed: Float? = null,
    // 本轮生成速度：上下文预警栏右侧的车速仪表盘读它取指针与满刻度
    generationSpeed: GenerationSpeed,
    onUpdateContextMeterStyle: (ContextMeterStyle) -> Unit = {},
    injectedLorebookCount: Int = 0,
    onShowUsage: () -> Unit = {},
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    completionProviders: List<ChatCompletionProvider> = emptyList(),
    onUpdateChatModel: (Model) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onMoreClick: () -> Unit,
    // 输入框下方悬浮栏的槽位与点击回调；空列表时不显示悬浮栏
    floatingBarItems: List<FloatingBarItem> = emptyList(),
    onFloatingBarItemClick: (FloatingBarItem) -> Unit = {},
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
    // 强制插话：打断当前回复，立刻发送输入框里的内容
    onInterjectClick: () -> Unit = {},
    messageQueue: InboxState = InboxState(),
    onRemoveQueuedMessage: (Uuid) -> Unit = {},
    onBeginEditQueuedMessage: (Uuid) -> QueuedMessage? = { null },
    onFinishEditQueuedMessage: (Uuid, List<UIMessagePart>?) -> Unit = { _, _ -> },
    onResumeMessageQueue: () -> Unit = {},
    // 引导提示词预设：点选后加进本轮；长按快捷消息按钮把输入框里的内容直接当引导发出去
    guideMessages: List<QuickMessage> = emptyList(),
    onAddGuidance: (QuickMessage) -> Unit = {},
    onSendAsGuidance: () -> Unit = {},
    onGuidanceToggleMode: (Guidance) -> Unit = {},
    onGuidanceRemove: (Guidance) -> Unit = {},
    // 待回答的 ask_user 问题：在任务清单卡里显示「需回答的问题」，点击重新打开抽屉
    pendingAskUser: List<AskUserPendingSummary> = emptyList(),
    onOpenAskUserSheet: (String) -> Unit = {},
    runningBackgroundTasks: Int = 0,
    onShowBackgroundTasks: () -> Unit = {},
    onStartVoiceMode: (() -> Unit)? = null,
    voiceState: VoiceSessionState = VoiceSessionState(),
    onStopVoiceMode: () -> Unit = {},
    // 新对话界面把输入框整体上移，放到屏幕中心偏下
    liftedBy: androidx.compose.ui.unit.Dp = 0.dp,
    // 输入区顶边被钉住时（新会话里抬到英雄卡下面），高度变化要从顶部往下展开：
    // 底部对齐是给贴屏幕底部的常态用的，容器高度动画追不上卡片时，
    // 卡片顶边会先冲出去、再落回锚点，收起和再展开都能看到闪一下
    pinContentToTop: Boolean = false,
    // 嵌在页面内容里时由外层负责键盘和导航栏 inset，避免重复留白
    applyWindowInsets: Boolean = true,
    // 空会话把键盘位移交给页面层做绘制期平移：底栏不再随输入法每帧改高度，
    // 英雄卡、Scaffold 的底部内边距和整页布局都不会跟着键盘动画重排
    deferImeInset: Boolean = false,
    // 输入区上方的底部渐变遮罩；横屏三栏里消息本来就有边界，不再压这一层
    showScrim: Boolean = true,
    // 遮罩浓淡：空会话里输入框被抬到页面中间，遮罩要跟着淡出，
    // 否则一张卡片周围先多出一圈底色带。用取值函数而不是数值：
    // 滑动那几百毫秒里它每帧都在变，读在绘制阶段，别让整页跟着重组
    scrimAlpha: () -> Float = { 1f },
) {
    val toaster = LocalToaster.current
    val containerColor = LocalUiSlots.current.inputBar
    // 输入卡片的材质：null = 高级外观关着（或没选材质也没强制玻璃），继续走原来的 chatInputHaze。
    // composerMaterial 内部已经过了 enableAdvancedAppearance；设置里没选材质时沿用旧特效，
    // 只有 applyToComposer 把玻璃档打开时才强制 liquid（和上游 chatComposerMaterial 一致）。
    val composerStyle = settings.advancedAppearanceSetting.composerMaterial(settings.displaySetting)

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 全宽模式下输入框也和屏幕两边齐平，圆角收成直角
    val containerShape = adaptiveShape(settings.displaySetting.uiCornerRadius.dp)
    // 内层圆角按固定的 8dp 内缩算，左右内边距怎么调都不会影响上下观感
    val innerShape = adaptiveShape((settings.displaySetting.uiCornerRadius - 8).coerceAtLeast(2).dp)
    var isEditingReasoning by remember { mutableStateOf(false) }
    // 退出编辑态时收起思考编辑器，否则下次编辑另一条带思考的消息会自己弹出来
    LaunchedEffect(state.isEditing()) {
        if (!state.isEditing()) isEditingReasoning = false
    }
    val modelListState = rememberModelListState(
        modelId = chatModel?.id,
        providers = settings.providers,
        type = ModelType.CHAT,
    )
    // 单行布局里模型胶囊兼任思考强度入口，点开的就是这张带模型行的面板
    var showReasoningPicker by remember { mutableStateOf(false) }

    // 单行输入框的展开状态提到卡片外面：空会话里要给卡片预留展开后的高度，
    // 底栏总高度在收放时保持不变，英雄卡和页面其余部分就不用跟着每帧重新量一遍
    val density = LocalDensity.current
    val singleLine = settings.displaySetting.inputLayoutStyle == InputLayoutStyle.SINGLE
    var fieldFocused by remember { mutableStateOf(false) }
    var oneLineHeightPx by remember { mutableIntStateOf(0) }
    val hasContent by remember(state) {
        derivedStateOf { state.textContent.text.isNotEmpty() }
    }
    // 唤起输入法（或框里已经有内容）时从一行展开成两行
    val expanded = singleLine && (fieldFocused || hasContent)
    val expandProgress = animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = rememberFixedSpec(380, ChatInputExpandEasing),
        label = "chatInputExpand",
    )
    // 按钮行固定 48dp：点击区域留够，行高定了上下留白才能算准
    val actionRowHeight = 48.dp
    val oneLineHeight = with(density) { oneLineHeightPx.toDp() }
        .takeIf { it > 0.dp } ?: 56.dp
    // 收起时按钮跟单行文字居中对齐；两种状态共用同一个下边距，
    // 所以容器向上长高时按钮的屏幕坐标保持不变
    val actionRowInset = ((oneLineHeight - actionRowHeight) / 2f)
        .coerceAtLeast(0.dp)
    // 展开后卡片比收起时高出的部分，全部来自文字下边距
    val expansionHeight = if (singleLine) {
        actionRowInset + actionRowHeight + 4.dp
    } else {
        0.dp
    }
    // 收起时的卡片高度：只在完全收起时记录，给空会话预留展开后的卡片槽位
    var collapsedCardHeightPx by remember { mutableIntStateOf(0) }
    // 卡片当前的实际高度：多行文字、附件都会让它比「收起 + 一档展开」更高
    var cardHeightPx by remember { mutableIntStateOf(0) }

    // 悬浮栏：开启后先露出来；输入框拿着焦点时收起，离开再展开；
    // 自动隐藏开启时静默 5 秒收起，上滑输入框重新拉起
    val floatingBarEnabled = settings.displaySetting.enableFloatingBar && floatingBarItems.isNotEmpty()
    val floatingBarAutoHide = settings.displaySetting.floatingBarAutoHide
    // 悬浮栏完全展开时的高度：空会话的卡片槽位也把它算进去，
    // 聚焦收起时底栏总高度不变，英雄卡和输入区不会跟着再动一次
    val floatingBarFullHeight = if (floatingBarEnabled) {
        16.dp + FloatingBarSizing.buttonExtent(settings.displaySetting.floatingBarScale)
    } else {
        0.dp
    }
    var floatingBarVisible by remember { mutableStateOf(false) }
    var floatingBarActivity by remember { mutableIntStateOf(0) }
    // 输入框是否拿着输入法焦点：键盘弹起时悬浮栏要给它让出高度
    var inputFocused by remember { mutableStateOf(false) }
    LaunchedEffect(floatingBarEnabled, state) {
        if (!floatingBarEnabled) {
            floatingBarVisible = false
            return@LaunchedEffect
        }
        // 先按「收起」布局一帧，再拉起，首次进入也走同一条滑动模糊过渡
        floatingBarVisible = false
        withFrameNanos { }
        floatingBarVisible = !inputFocused
    }
    // 焦点进出输入框：进去就收起，把键盘上方的高度留给内容；离开再展开。
    // 首帧的那次由上面的入场动画负责，这里跳过
    LaunchedEffect(floatingBarEnabled) {
        if (!floatingBarEnabled) return@LaunchedEffect
        snapshotFlow { inputFocused }
            .drop(1)
            .collectLatest { focused -> floatingBarVisible = !focused }
    }
    // 自动隐藏：显示期间静默 5 秒收起
    LaunchedEffect(floatingBarEnabled, floatingBarAutoHide) {
        if (!floatingBarEnabled || !floatingBarAutoHide) return@LaunchedEffect
        snapshotFlow {
            Triple(state.textContent.text.length, floatingBarActivity, floatingBarVisible)
        }.collectLatest { (_, _, visible) ->
            if (!visible) return@collectLatest
            delay(5_000)
            floatingBarVisible = false
        }
    }
    val revealFloatingBarModifier = if (floatingBarEnabled && !floatingBarVisible) {
        Modifier.pointerInput(floatingBarEnabled, floatingBarVisible) {
            val threshold = 24.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (dy < -threshold && abs(dy) > abs(dx)) {
                        // 认成上滑后吃掉这一次移动，输入框内部不再跟着滚一截
                        change.consume()
                        floatingBarVisible = true
                        floatingBarActivity++
                        break
                    }
                }
            }
        }
    } else {
        Modifier
    }
    val resetFloatingBarTimerModifier = if (floatingBarEnabled) {
        Modifier.pointerInput(floatingBarEnabled) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                floatingBarActivity++
            }
        }
    } else {
        Modifier
    }

    fun sendMessage() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) onCancelClick() else onSendClick()
    }

    fun sendMessageWithoutAnswer() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) onCancelClick() else onLongSendClick()
    }

    // 键盘动画期间 inset 每帧都在变；这里连带「是否可见」也不读进组合，
    // 只在收起来的那一下交还焦点，输入区不跟着键盘动画重组
    val imeInsets = WindowInsets.ime
    LaunchedEffect(imeInsets, density) {
        snapshotFlow { imeInsets.getBottom(density) > 0 }
            .filter { !it }
            .collect { focusManager.clearFocus() }
    }

    // 关闭消息队列后，插话用的临时项只会停留几毫秒，别让它闪一下「待发送」。
    // 之前就排着的消息和系统通知照常显示，否则它们会变成看不见也撤不掉的残留
    val visibleQueue = remember(messageQueue, settings.enableMessageQueue) {
        if (settings.enableMessageQueue) {
            messageQueue
        } else {
            // 只有「正在过渡到派发」的这一条时不显示队列，免得闪一下「待发送」；
            // 一旦还压着别的消息（旧队列残留、通知）就照常显示，不能让任何一条变得看不见。
            // 暂停时也照常显示：卡住的消息要能继续或撤销。
            val transientOnly = !messageQueue.paused &&
                messageQueue.items.all { (it as? QueuedMessage)?.sentWithoutQueue == true }
            if (transientOnly) messageQueue.copy(items = emptyList()) else messageQueue
        }
    }

    val asr = LocalASRState.current
    val asrState by asr.state.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val soundEffectPlayer: SoundEffectPlayer = koinInject()
    LaunchedEffect(Unit) {
        soundEffectPlayer.preload(R.raw.asr_start, R.raw.asr_stop)
    }
    val asrPermission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permissionState = asrPermission)
    var asrBaseText by remember { mutableStateOf("") }
    LaunchedEffect(asrState.status) {
        when (asrState.status) {
            ASRStatus.Listening -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                soundEffectPlayer.play(R.raw.asr_start)
            }

            ASRStatus.Stopping -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                soundEffectPlayer.play(R.raw.asr_stop)
            }

            else -> {}
        }
    }
    LaunchedEffect(asrState.errorMessage) {
        asrState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            toaster.show(message = message, type = ToastType.Error)
        }
    }

    // 底部渐变遮罩：从输入区顶部一直过渡到屏幕底部（键盘弹出时到键盘上沿），避免消息透到系统导航栏
    val scrimColor = MaterialTheme.colorScheme.background
    // 嵌在空会话页面中间时不需要底部渐变遮罩
    val scrimModifier = if (applyWindowInsets && showScrim) {
        Modifier.drawBehind {
            val endY = if (deferImeInset) {
                // 空态底栏本来就不含键盘 inset，不必再读它，免得跟着键盘每帧重画遮罩
                size.height.toFloat()
            } else {
                (size.height - imeInsets.getBottom(this)).coerceAtLeast(1f)
            }
            val brush = Brush.verticalGradient(
                colors = listOf(
                    scrimColor.copy(alpha = 0f),
                    scrimColor.copy(alpha = scrimAlpha()),
                ),
                startY = 0f,
                endY = endY,
            )
            drawRect(brush)
        }
    } else {
        Modifier
    }
    Surface(
        color = Color.Transparent,
        modifier = scrimModifier,
    ) {
        Column(
            modifier = modifier
                .then(
                    if (applyWindowInsets) {
                        if (deferImeInset) {
                            Modifier.navigationBarsPadding()
                        } else {
                            Modifier.imePadding().navigationBarsPadding()
                        }
                    } else {
                        Modifier
                    },
                )
                // 全宽模式下输入框贴到屏幕左右边，键盘 inset 与导航栏 inset 仍然保留
                .adaptiveHorizontalPadding(8.dp)
                .padding(bottom = 8.dp + liftedBy)
                // 内容贴底对齐：输入框向上长高时按钮的屏幕位置不动。
                // 空会话里输入区是顶部钉住、向下生长的，槽位已经是定值，容器高度不该再跟着
                // 每一次测量走一段动画：animateContentSize 会把槽位里 1px 的抖动抹成一段
                // 300ms 的高矮动画，底栏高度逐帧变，英雄卡和输入区抬升量就跟着逐帧重排。
                // 贴底以后才需要这段平滑：清单、消息队列、编辑提示条进出都靠它。
                .then(
                    if (pinContentToTop) Modifier
                    else Modifier.motionContentSize(alignment = Alignment.BottomStart)
                )
                // 进入/退出编辑时带上模糊：提示条收放和输入区变高变矮是一段动画，
                // 不是提示条先消失、高度再慢慢弹回去
                .motionBlurOnToggle(state.isEditing(), maxRadius = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 清单只在「还没做完」的时候占位置。全部完成后收起来；
            // 想再看回来（比如手动点错了、要取消完成）就关掉设置里的「完成后收起任务清单」。
            val hideCompletedTodos = LocalSettings.current.displaySetting.hideCompletedTodoCard &&
                todos.isNotEmpty() && todos.all { it.status == TodoStatus.COMPLETED }
            val showTodoSection = todos.isNotEmpty() && !hideCompletedTodos
            // 清单和引导共用一张卡片、一次折叠：两样都在时只有一颗展开箭头
            if (!LocalConversationStatusInSidePanel.current &&
                (showTodoSection || visibleQueue.guidances.isNotEmpty() || pendingAskUser.isNotEmpty())
            ) {
                ConversationTodoCard(
                    todos = if (showTodoSection) todos else emptyList(),
                    onTodosChange = onTodosChange,
                    reminder = if (showTodoSection) {
                        TodoReminderUi(
                            enabled = assistant.todoReminderEnabled,
                            interval = assistant.todoReminderInterval,
                        )
                    } else {
                        null
                    },
                    onReminderChange = { next ->
                        onUpdateAssistant(
                            assistant.copy(
                                todoReminderEnabled = next.enabled,
                                todoReminderInterval = next.interval,
                            )
                        )
                    },
                    guidances = visibleQueue.guidances,
                    onGuidanceToggleMode = onGuidanceToggleMode,
                    onGuidanceRemove = onGuidanceRemove,
                    pendingQuestions = pendingAskUser,
                    onPendingQuestionClick = onOpenAskUserSheet,
                    running = loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveHorizontalPadding(12.dp)
                        .padding(vertical = 4.dp),
                )
            }
            MessageQueuePanel(
                state = visibleQueue,
                onRemove = onRemoveQueuedMessage,
                onBeginEdit = onBeginEditQueuedMessage,
                onFinishEdit = onFinishEditQueuedMessage,
                onResume = onResumeMessageQueue,
            )
            if (injectedLorebookCount > 0) {
                Text(
                    text = stringResource(R.string.chat_page_worldbook_injected, injectedLorebookCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            if (chatModel != null && settings.displaySetting.contextMeterStyle != ContextMeterStyle.OFF) {
                ContextMeterSwitcher(
                    tokens = contextTokens,
                    maxTokens = chatModel.maxContextTokens,
                    style = settings.displaySetting.contextMeterStyle,
                    onStyleChange = onUpdateContextMeterStyle,
                    onShowUsage = onShowUsage,
                    running = loading,
                    speedIndicator = {
                        TokenSpeedGauge(
                            generationSpeed = generationSpeed,
                            running = loading,
                            fallbackSpeed = usageTokenSpeed,
                        )
                    },
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(resetFloatingBarTimerModifier),
            ) {
                // 空会话里输入区顶边钉在英雄卡下面，卡片向下展开；槽位不变，底栏总高度就不变，
                // 英雄卡和 Scaffold 的底部内边距也不用跟着每帧重新布局。
                //
                // 槽位 = max(卡片实际高度, 收起高度 + 一档展开) + 悬浮栏：
                // - 「收起高度 + 一档展开」是地板。收放动画全程卡片都不会低于它，
                //   所以这段动画里底栏高度是个定值。
                // - 卡片自己更高的那部分（文字折行、附件、编辑提示条）照样算进来，
                //   否则离开输入框、悬浮栏露出来的那一下，底栏会被顶高一截。
                //
                // 这两个高度都是测量出来的，只能在测量阶段读：在组合阶段读它们，
                // 收放动画的每一帧都会把整个 ChatInput（清单、悬浮栏、按钮行）重组一遍。
                val cardSlotMinPx: () -> Int = {
                    with(density) {
                        val floorPx = if (collapsedCardHeightPx > 0) {
                            collapsedCardHeightPx + (if (singleLine) expansionHeight.roundToPx() else 0)
                        } else {
                            0
                        }
                        val cardPx = maxOf(cardHeightPx, floorPx)
                        if (cardPx > 0) cardPx + floatingBarFullHeight.roundToPx() else 0
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (pinContentToTop) {
                                Modifier.cardSlotMinHeight(cardSlotMinPx)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { size ->
                                    // 只记完全收起时的高度；展开到一半的高度不能当基准
                                    if (expandProgress.value == 0f) collapsedCardHeightPx = size.height
                                    cardHeightPx = size.height
                                }
                                .then(revealFloatingBarModifier)
                                .clip(containerShape)
                                .then(
                                    if (composerStyle != null) {
                                        Modifier.advancedMaterial(
                                            shape = containerShape,
                                            base = containerColor,
                                            material = composerStyle,
                                            hazeState = hazeState,
                                            // 输入框里是要打的字，着色不能薄到认不出边界
                                            readableFloor = .35f,
                                        )
                                    } else {
                                        Modifier.chatInputHaze(
                                            hazeState = hazeState,
                                            displaySetting = settings.displaySetting,
                                            containerColor = containerColor,
                                            shape = containerShape,
                                        )
                                    }
                                ),
                            shape = containerShape,
                            tonalElevation = 0.dp,
                            // 玻璃档由材质层自己画渐变描边，这里再画一道平线就成了双线；
                            // 其余情况保持原来那道 1dp 描边
                            border = if (composerStyle == null || composerStyle.needsFlatOutline()) {
                                BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            } else {
                                null
                            },
                            // 着色由材质层统一叠在模糊之上，这里保持透明，避免叠成两倍浓度
                            color = if (composerStyle != null) {
                                Color.Transparent
                            } else if (settings.displaySetting.effectiveBackgroundEffect == BackgroundEffectType.OFF) {
                                containerColor
                            } else {
                                Color.Transparent
                            },
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = settings.displaySetting.inputPadding.dp,
                                    vertical = 4.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // 内容行（语音 / 附件 / 编辑横幅）的出现与收起：
                                // 旧的向下滑走、新的从下方滑入，fade+scale 一起收，ImageToolbox 同款曲线，
                                // 进出同参同档；卡片整体高度由外层 motionContentSize 负责，这里只做行内动效
                                val rowSlideSpec = rememberSlideSpec<IntOffset>()
                                val rowScaleSpec = rememberSlideSpec<Float>()
                                val rowFadeSpec = rememberEffectsSpec<Float>()
                                val rowEnter: EnterTransition =
                                    slideInVertically(rowSlideSpec) { it / 2 } +
                                        fadeIn(rowFadeSpec) +
                                        scaleIn(rowScaleSpec, initialScale = 0.96f)
                                val rowExit: ExitTransition =
                                    slideOutVertically(rowSlideSpec) { it / 2 } +
                                        fadeOut(rowFadeSpec) +
                                        scaleOut(rowScaleSpec, targetScale = 0.96f)
                                AnimatedVisibility(
                                    visible = voiceState.phase != VoicePhase.Off,
                                    enter = rowEnter,
                                    exit = rowExit,
                                ) {
                                    Column {
                                        VoiceModeRow(
                                            state = voiceState,
                                            onStop = onStopVoiceMode,
                                            onRetry = { onStartVoiceMode?.invoke() },
                                        )
                                        androidx.compose.material3.HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                                AnimatedVisibility(
                                    visible = state.messageContent.isNotEmpty(),
                                    enter = rowEnter,
                                    exit = rowExit,
                                ) {
                                    MediaFileInputRow(state = state)
                                }
                                // 必须在 TextInputRow 外面：单行布局的按钮以 TextInputRow 整体高度居中，
                                // 提示条排在 TextField 之前会把按钮顶到输入框上部
                                AnimatedVisibility(
                                    visible = state.isEditing(),
                                    enter = rowEnter,
                                    exit = rowExit,
                                ) {
                                    EditingBanner(
                                        state = state,
                                        shape = innerShape,
                                        onEditReasoning = { isEditingReasoning = true },
                                    )
                                }

                                val quickMessages = remember(settings.quickMessages, assistant.quickMessageIds) {
                                    settings.getQuickMessagesOfAssistant(assistant)
                                }
                                val asrButton: @Composable () -> Unit = {
                                    if (!voiceState.isActive && (asrState.isAvailable || asrState.isRecording)) {
                                        AsrButton(
                                            state = asrState,
                                            onClick = {
                                                when (asrState.status) {
                                                    ASRStatus.Listening -> asr.stop()
                                                    ASRStatus.Idle, ASRStatus.Error -> {
                                                        if (!asrPermission.allRequiredPermissionsGranted) {
                                                            asrPermission.requestPermissions()
                                                        } else {
                                                            asrBaseText = state.textContent.text.toString()
                                                            asr.start { transcript ->
                                                                val spacer =
                                                                    if (asrBaseText.isBlank() || transcript.isBlank()) "" else " "
                                                                state.setMessageText(asrBaseText + spacer + transcript)
                                                            }
                                                        }
                                                    }

                                                    ASRStatus.Connecting, ASRStatus.Stopping -> {}
                                                }
                                            }
                                        )
                                    }
                                }
                                val interjectButton: @Composable () -> Unit = {
                                    if (loading && settings.enableMessageQueue && !state.isEmpty()) {
                                        ActionIconButton(onClick = onInterjectClick) {
                                            Icon(
                                                imageVector = HugeIcons.MessageAdd01,
                                                contentDescription = stringResource(R.string.chat_page_interject),
                                            )
                                        }
                                    }
                                }
                                val sendButton: @Composable () -> Unit = {
                                    if (loading) {
                                        KeepScreenOn()
                                    }
                                    AnimatedVisibility(
                                        visible = !asrState.isRecording,
                                        enter = fadeIn(rememberEffectsSpec<Float>()) + scaleIn(animationSpec = rememberSpatialSpec<Float>()),
                                        exit = fadeOut(rememberEffectsSpec<Float>()) + scaleOut(animationSpec = rememberSpatialSpec<Float>()),
                                    ) {
                                        SendButton(
                                            loading = loading,
                                            empty = state.isEmpty(),
                                            onClick = { sendMessage() },
                                            onLongClick = { sendMessageWithoutAnswer() },
                                        )
                                    }
                                }
                                val reasoningButton: @Composable () -> Unit = {
                                    if (chatModel?.abilities?.contains(ModelAbility.REASONING) == true) {
                                        ReasoningButton(
                                            reasoningLevel = assistant.reasoningLevel,
                                            onUpdateReasoningLevel = {
                                                onUpdateAssistant(assistant.copy(reasoningLevel = it))
                                            },
                                            onlyIcon = true,
                                            useL3HubStyle = settings.displaySetting.useL3HubReasoningStyle,
                                        )
                                    }
                                }

                                if (singleLine) {
                                    // 文字带升到上一行，下一行的按钮组留在原地不动
                                    // 卡片左右内边距变大时，把输入框自己的内缩减回去，
                                    // 文字可用宽度保持不变，行数就不会因为拖滑块而变
                                    val inputInsetFix = (settings.displaySetting.inputPadding - 8).coerceAtLeast(0)
                                    val collapsedPlaceholder = stringResource(R.string.chat_input_placeholder)
                                    val modelName = chatModel?.displayName
                                    val expandedPlaceholder = if (modelName != null) {
                                        stringResource(R.string.chat_input_placeholder_expanded, modelName)
                                    } else {
                                        collapsedPlaceholder
                                    }
                                    val leadingActions: @Composable () -> Unit = {
                                        ActionIconButton(onClick = onMoreClick) {
                                            Icon(
                                                imageVector = HugeIcons.Add01,
                                                contentDescription = stringResource(R.string.more_options),
                                            )
                                        }
                                        // 一直显示：长按它是把输入框内容当引导发出去的入口，
                                        // 没有预设时也得能按，否则这个手势就藏起来了
                                        QuickMessageButton(
                                            quickMessages = quickMessages,
                                            guideMessages = guideMessages,
                                            state = state,
                                            onAddGuidance = onAddGuidance,
                                            onSendAsGuidance = onSendAsGuidance,
                                        )
                                    }
                                    // 模型与思考强度都收进左侧胶囊，右侧只留输入相关按钮
                                    val reasoningSupported =
                                        chatModel?.abilities?.contains(ModelAbility.REASONING) == true
                                    val trailingActions: @Composable () -> Unit = {
                                        asrButton()
                                        interjectButton()
                                        sendButton()
                                    }
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        TextInputRow(
                                            state = state,
                                            assistant = assistant,
                                            completionProviders = completionProviders,
                                            onSendMessage = { sendMessage() },
                                            modifier = Modifier.fillMaxWidth(),
                                            showQuickMessageButton = false,
                                            fullscreenOnOverflow = true,
                                            guideMessages = guideMessages,
                                            onAddGuidance = onAddGuidance,
                                            onSendAsGuidance = onSendAsGuidance,
                                            singleLineExpansion = SingleLineExpansion(
                                                progress = expandProgress,
                                                collapsedStart = (70 - inputInsetFix).dp,
                                                collapsedEnd = (112 - inputInsetFix).dp,
                                                bottom = expansionHeight,
                                            ),
                                            placeholder = {
                                                MorphingPlaceholder(
                                                    target = if (expanded) {
                                                        expandedPlaceholder
                                                    } else {
                                                        collapsedPlaceholder
                                                    },
                                                )
                                            },
                                            onFocusChange = { focused ->
                                                fieldFocused = focused
                                                inputFocused = focused
                                            },
                                            onMeasure = { _, oneLine -> oneLineHeightPx = oneLine },
                                        )
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .fillMaxWidth()
                                                .padding(bottom = actionRowInset)
                                                .height(actionRowHeight),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            leadingActions()
                                            ChatInputModelPill(
                                                model = chatModel,
                                                reasoningLevel = assistant.reasoningLevel
                                                    .takeIf { reasoningSupported },
                                                useL3HubReasoningStyle = settings.displaySetting.useL3HubReasoningStyle,
                                                visible = expanded,
                                                onClick = {
                                                    // 能思考的模型点开强度面板（面板首行就是模型选择），
                                                    // 不能思考的直接落到模型列表
                                                    if (reasoningSupported) {
                                                        showReasoningPicker = true
                                                    } else {
                                                        modelListState.open()
                                                    }
                                                },
                                            )
                                            Spacer(Modifier.weight(1f))
                                            trailingActions()
                                        }
                                    }
                                } else {
                                    TextInputRow(
                                        state = state,
                                        assistant = assistant,
                                        completionProviders = completionProviders,
                                        onSendMessage = { sendMessage() },
                                        guideMessages = guideMessages,
                                        onAddGuidance = onAddGuidance,
                                        onSendAsGuidance = onSendAsGuidance,
                                        onFocusChange = { inputFocused = it },
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            ModelSelectorButton(
                                                state = modelListState,
                                                onlyIcon = true,
                                                modifier = Modifier,
                                            )
                                            reasoningButton()
                                        }

                                        ActionIconButton(onClick = onMoreClick) {
                                            Icon(
                                                imageVector = HugeIcons.Add01,
                                                contentDescription = stringResource(R.string.more_options),
                                            )
                                        }

                                        asrButton()
                                        interjectButton()
                                        sendButton()
                                    }
                                }
                            }
                        }
                        if (floatingBarEnabled) {
                            ChatFloatingBar(
                                visible = floatingBarVisible,
                                items = floatingBarItems,
                                onItemClick = onFloatingBarItemClick,
                                hazeState = hazeState,
                                displaySetting = settings.displaySetting,
                                // 聊天坞总闸关着时是 null，悬浮栏继续走 chatInputHaze 的老路径
                                material = settings.chatDockMaterial(),
                            )
                        }
                    }
                }
            }

        }
    }

    val editingReasoning = state.editingReasoning
    if (isEditingReasoning && editingReasoning != null) {
        FullScreenEditor(
            textState = editingReasoning,
            placeholder = stringResource(R.string.chat_input_reasoning_placeholder),
        ) {
            isEditingReasoning = false
        }
    }

    ModelListSheet(
        state = modelListState,
        onSelect = onUpdateChatModel,
    )

    ReasoningPicker(
        visible = showReasoningPicker,
        reasoningLevel = assistant.reasoningLevel,
        onDismissRequest = { showReasoningPicker = false },
        onUpdateReasoningLevel = {
            onUpdateAssistant(assistant.copy(reasoningLevel = it))
        },
        modelName = chatModel?.displayName,
        modelContent = { dismiss ->
            ModelListContent(
                state = modelListState,
                onSelect = { model ->
                    onUpdateChatModel(model)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        },
        useL3HubStyle = settings.displaySetting.useL3HubReasoningStyle,
    )
}

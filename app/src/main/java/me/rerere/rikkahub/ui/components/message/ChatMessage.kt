package me.rerere.rikkahub.ui.components.message

import me.rerere.rikkahub.ui.components.ai.chatInputHaze
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.OcrMetadata
import me.rerere.ai.ui.VideoRecognitionMetadata
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.ai.ui.metadataAs
import me.rerere.hugeicons.HugeIcons
import coil3.compose.AsyncImage
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Video01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.SelectableContent
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.components.richtext.buildMarkdownPreviewHtml
import me.rerere.rikkahub.ui.components.ui.ChainOfThought
import me.rerere.rikkahub.ui.components.ui.Favicon
import me.rerere.rikkahub.ui.components.ui.FaviconRow
import me.rerere.rikkahub.ui.components.ui.effectSurfaceAlpha
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalWorkspaceId
import me.rerere.rikkahub.ui.theme.LocalUiSlots
import me.rerere.rikkahub.ui.theme.LocalChatFontFamily
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.adaptiveCornerRadius
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPaddingValue
import me.rerere.rikkahub.ui.theme.adaptiveShape
import me.rerere.rikkahub.ui.theme.rememberChatFontFamily
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.urlDecode
import me.rerere.ui.charts.ChartCard
import me.rerere.ui.charts.ChartSpec
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.webview.WebViewContentCache
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

// 一条消息内部各块（思考卡、正文气泡、附件、引用）之间的垂直间距。
// 这些块直接作为父 Column 的子项发射，缺了它卡片和气泡会像素级贴死。
private val MessageBlockGap = 4.dp

@Composable
fun ChatMessage(
    node: MessageNode,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    model: Model? = null,
    assistant: Assistant? = null,
    lastMessage: Boolean = false,
    // 与上一条消息同组（同角色连续）：气泡上沿收紧，头像/模型名只在组首出现
    joinedToPrevious: Boolean = false,
    // 与下一条消息同组：气泡下沿收紧
    joinedToNext: Boolean = false,
    onFork: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (MessageNode) -> Unit,
    // 子代理回传通知的正文改写与删除：它不是用户气泡，编辑走自己的弹窗，不占聊天输入框。
    // 两个回调都不传时这条通知是只读的（子会话预览、提示词预览），长按不弹菜单
    onEditSubAgentNotice: ((UIMessage, String) -> Unit)? = null,
    onDeleteSubAgentNotice: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    hazeState: HazeState? = null,
    conversationId: Uuid? = null,
) {
    val message = node.messages[node.selectIndex]
    if (message.isContextCheckpoint) {
        ChatMessageContextCheckpoint(
            message = message,
            onEdit = onEdit,
            onDelete = onDelete,
            modifier = modifier,
        )
        return
    }
    if (message.isSubAgentNotice) {
        ChatMessageSubAgentNotice(
            message = message,
            modifier = modifier,
            onEdit = onEditSubAgentNotice?.let { edit -> { text: String -> edit(message, text) } },
            onDelete = onDeleteSubAgentNotice,
        )
        return
    }
    // 助手消息在第一个 token 之前也可能只有 Notice，那时要走下面的流程才有加载状态和操作栏
    if (message.role == MessageRole.USER && message.isNoticeOnly) {
        ChatMessageNotices(
            message = message,
            // 回复失败时这是最后一个节点，没有助手消息可以点重新生成
            onRetry = onRegenerate.takeIf { lastMessage && !loading },
            onDelete = onDelete,
            modifier = modifier,
        )
        return
    }
    val settings = LocalSettings.current.displaySetting
    val chatFontFamily = LocalChatFontFamily.current ?: rememberChatFontFamily(settings)
    val textStyle = LocalTextStyle.current.copy(
        fontSize = LocalTextStyle.current.fontSize * settings.fontSizeRatio,
        lineHeight = LocalTextStyle.current.lineHeight * settings.fontSizeRatio,
        fontFamily = chatFontFamily
    )
    var showActionsSheet by remember { mutableStateOf(false) }
    var showSelectCopySheet by remember { mutableStateOf(false) }
    // 重新生成要确认一次；这个状态放在菜单外面，确认框不能挂在 Popup 里
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    // 用户消息不再在气泡下面常驻一排操作按钮：长按气泡把菜单拉起来，原来那排按钮都收进菜单。
    // 助手消息的按钮行保持不变。
    val onUserMessageLongClick: (() -> Unit)? = if (message.role == MessageRole.USER) {
        {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            showActionsSheet = true
        }
    } else {
        null
    }
    val navController = LocalNavController.current
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    // 用户消息的长按菜单贴着气泡原地开
    val inPlaceMenu = message.role == MessageRole.USER
    val onSelectAndCopy: () -> Unit = { showSelectCopySheet = true }
    val onWebViewPreview: () -> Unit = {
        val textContent = message.parts
            .filterIsInstance<UIMessagePart.Text>()
            .joinToString("\n\n") { it.text }
            .trim()
        if (textContent.isNotBlank()) {
            val htmlContent = buildMarkdownPreviewHtml(
                context = context,
                markdown = textContent,
                colorScheme = colorScheme
            )
            val contentId = WebViewContentCache.store(context.cacheDir, htmlContent)
            navController.navigate(Screen.WebView(contentId = contentId))
        }
    }
    // 原来挂在气泡下面的那排按钮，现在作为菜单顶部的一条胶囊，两条路径共用
    val userMenuQuickActions: (@Composable () -> Unit) = {
        UserMessageMenuQuickActions(
            message = message,
            node = node,
            onRegenerateRequest = {
                // 先收菜单再问：确认框弹在 Popup 上面时，落点算「点到菜单外」，
                // 菜单会连同框一起被关掉，那一下点击也就丢了
                showActionsSheet = false
                showRegenerateConfirm = true
            },
            onUpdate = onUpdate,
        )
    }
    // 菜单作为气泡的子节点渲染，锚点就是气泡本身
    val userMessageMenu: (@Composable () -> Unit)? = if (inPlaceMenu) {
        {
            if (showActionsSheet) {
                UserMessageActionsMenu(
                    message = message,
                    model = model,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onShare = onShare,
                    onFork = onFork,
                    onSelectAndCopy = onSelectAndCopy,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onWebViewPreview = onWebViewPreview,
                    quickActions = userMenuQuickActions,
                    onDismissRequest = { showActionsSheet = false },
                )
            }
        }
    } else {
        null
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (message.role == MessageRole.USER) {
            Alignment.End
        } else {
            Alignment.Start
        },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 同组后续消息不再重复头像/模型名，否则气泡之间会被头像行顶开，接不到一起。
        // 想让每条消息都显示头像，把 joinedToPrevious 从这个判断里去掉即可。
        val showMessageHeader = !message.parts.isEmptyUIMessage() && !joinedToPrevious
        if (showMessageHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                ChatMessageAssistantAvatar(
                    message = message,
                    model = model,
                    assistant = assistant,
                    loading = loading,
                    modifier = Modifier.weight(1f)
                )
                ChatMessageUserAvatar(
                    message = message,
                    avatar = settings.userAvatar,
                    nickname = settings.userNickname,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        val appSettings = LocalSettings.current
        val translation = message.translation
        var showTranslationPage by remember(message.id) { mutableStateOf(false) }
        var translating by remember(message.id) { mutableStateOf(false) }
        // 只有本次会话里从“无译文”变成“有译文”时才自动翻到译文页；
        // 从别的页面回来时消息本身就带着译文，应该留在原文
        var previousTranslation by remember(message.id) { mutableStateOf(translation) }
        LaunchedEffect(translation) {
            if (translation != null && translation != previousTranslation) {
                translating = false
                showTranslationPage = true
            }
            previousTranslation = translation
        }
        // 只负责查看已有译文，不靠横滑触发翻译。
        // 必须用 draggable(Horizontal)：detectDragGestures 会先吃掉触摸阈值事件，
        // 纵向滑动被它抢走后列表就滚不动了（译文消息划不动的根因）。
        var swipeX by remember(message.id) { mutableFloatStateOf(0f) }
        val triggerDistance = with(LocalDensity.current) { 96.dp.toPx() }
        val swipeModifier = if (translation == null) {
            Modifier
        } else {
            Modifier.draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> swipeX += delta },
                onDragStarted = { swipeX = 0f },
                onDragStopped = {
                    if (abs(swipeX) >= triggerDistance) {
                        showTranslationPage = swipeX < 0
                    }
                },
            )
        }
        CompositionLocalProvider(
            LocalTextStyle provides textStyle,
            LocalWorkspaceId provides assistant?.primaryWorkspaceId?.toString(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(swipeModifier)
            ) {
                AnimatedContent(
                    targetState = showTranslationPage && translation != null,
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "messageTranslationPage",
                ) { translated ->
                    if (translated && translation != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            MarkdownBlock(
                                content = translation,
                                onClickCitation = {},
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(MessageBlockGap),
                            horizontalAlignment = if (message.role == MessageRole.USER) {
                                Alignment.End
                            } else {
                                Alignment.Start
                            },
                        ) {
                            MessagePartsBlock(
                                assistant = assistant,
                                role = message.role,
                                parts = message.parts,
                                annotations = message.annotations,
                                loading = loading,
                                model = model,
                                onToolApproval = onToolApproval,
                                onToolAnswer = onToolAnswer,
                                onUserMessageClick = if (message.role == MessageRole.USER) onEdit else null,
                                onUserMessageLongClick = onUserMessageLongClick,
                                userMessageMenu = userMessageMenu,
                                hazeState = hazeState,
                                joinedToPrevious = joinedToPrevious,
                                joinedToNext = joinedToNext,
                            )
                        }
                    }
                }
            }
            if (translation != null || translating) {
            Text(
                text = stringResource(
                    when {
                        translating -> R.string.chat_message_translation_translating
                        showTranslationPage -> R.string.chat_message_translation_show_original
                        else -> R.string.chat_message_translation_show_translation
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {
                        when {
                            translation != null -> showTranslationPage = !showTranslationPage
                            onTranslate != null && !translating -> {
                                translating = true
                                onTranslate(
                                    message,
                                    Locale.forLanguageTag(appSettings.translateTargetLanguage),
                                )
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            )
            }
        }

        val showActions = if (lastMessage) {
            !loading
        } else {
            message.parts.isEmptyUIMessage().not()
        }
        // 普通气泡里的用户消息把操作收进长按菜单，气泡下面不再挂按钮
        val showActionRow = showActions && message.role != MessageRole.USER

        val motionScheme = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = showActionRow,
            enter = slideInVertically(motionScheme.defaultSpatialSpec()) { it / 2 } +
                fadeIn(motionScheme.defaultEffectsSpec()),
            exit = slideOutVertically(motionScheme.fastSpatialSpec()) { it / 2 } +
                fadeOut(motionScheme.fastEffectsSpec())
        ) {
            Column(
                modifier = Modifier.animateContentSize()
            ) {
                ChatMessageActionButtons(
                    message = message,
                    onRegenerate = onRegenerate,
                    node = node,
                    onUpdate = onUpdate,
                    onOpenActionSheet = {
                        showActionsSheet = true
                    },
                    onTranslate = onTranslate,
                    onClearTranslation = onClearTranslation
                )
            }
        }

        // 横屏三栏时「本轮改动」收进右侧栏，消息下面不再重复挂一行
        if (!(lastMessage && LocalTurnChangesInSidePanel.current)) {
            EditedFilesList(
                parts = message.parts,
                assistant = assistant,
                conversationId = conversationId,
                isLastMessage = lastMessage,
            )
        }

        ProvideTextStyle(textStyle) {
            ChatMessageNerdLine(message = message)
        }

    }
    // 用户消息的菜单在气泡里原地弹，这里只剩助手消息的底部弹窗
    if (showActionsSheet && !inPlaceMenu) {
        ChatMessageActionsSheet(
            message = message,
            onEdit = onEdit,
            onDelete = onDelete,
            onShare = onShare,
            onFork = onFork,
            model = model,
            onSelectAndCopy = onSelectAndCopy,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onWebViewPreview = onWebViewPreview,
            quickActions = if (message.role == MessageRole.USER) userMenuQuickActions else null,
            onDismissRequest = {
                showActionsSheet = false
            }
        )
    }

    RikkaConfirmDialog(
        show = showRegenerateConfirm,
        title = stringResource(R.string.regenerate),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showRegenerateConfirm = false
            onRegenerate()
        },
        onDismiss = { showRegenerateConfirm = false },
        text = { Text(stringResource(R.string.regenerate_confirm_message)) },
    )

    if (showSelectCopySheet) {
        ChatMessageCopySheet(
            message = message,
            hazeState = hazeState,
            onDismissRequest = {
                showSelectCopySheet = false
            }
        )
    }
}

// 最后一段正文之前的步骤整体折叠时显示的开关
@Composable
private fun EarlierStepsToggle(
    collapsed: Boolean,
    count: Int,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (collapsed) HugeIcons.ArrowDown01 else HugeIcons.ArrowUp01,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                if (collapsed) {
                    R.string.chat_message_expand_earlier_steps
                } else {
                    R.string.chat_message_collapse_earlier_steps
                },
                count,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// 思考链折叠时默认保留的尾部步骤数
private const val COLLAPSED_VISIBLE_STEPS = 2

@Composable
private fun MessagePartsBlock(
    assistant: Assistant?,
    role: MessageRole,
    model: Model?,
    parts: List<UIMessagePart>,
    annotations: List<UIMessageAnnotation>,
    loading: Boolean,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onUserMessageClick: (() -> Unit)? = null,
    onUserMessageLongClick: (() -> Unit)? = null,
    userMessageMenu: (@Composable () -> Unit)? = null,
    hazeState: HazeState? = null,
    joinedToPrevious: Boolean = false,
    joinedToNext: Boolean = false,
) {
    val context = LocalContext.current
    // 消息输出HapticFeedback
    val hapticFeedback = LocalHapticFeedback.current
    val settings = LocalSettings.current
    val partsState by rememberUpdatedState(parts)

    val handleClickCitation: (String) -> Unit = remember {
        handler@{ citationId ->
            partsState.forEach { part ->
                if (part is UIMessagePart.Tool && part.toolName == "search_web" && part.isExecuted) {
                    val outputText = part.output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                    val items =
                        runCatching { JsonInstant.parseToJsonElement(outputText).jsonObject["items"]?.jsonArray }.getOrNull()
                            ?: return@forEach
                    items.forEach { item ->
                        val id = item.jsonObject["id"]?.jsonPrimitive?.content ?: return@forEach
                        val url = item.jsonObject["url"]?.jsonPrimitive?.content ?: return@forEach
                        if (citationId == id) {
                            context.openUrl(url)
                            return@handler
                        }
                    }
                }
            }
        }
    }
    // 生成时的触感按「新起一行」触发：不设时间窗，也不跟着每个 chunk 抖一次
    LaunchedEffect(loading, settings.displaySetting.enableMessageGenerationHapticEffect) {
        if (!loading || !settings.displaySetting.enableMessageGenerationHapticEffect) return@LaunchedEffect
        fun lineCount(parts: List<UIMessagePart>): Int = parts.sumOf { part ->
            (part as? UIMessagePart.Text)?.text?.count { it == '\n' } ?: 0
        }
        var lastLines = lineCount(partsState)
        snapshotFlow { lineCount(partsState) }.collect { lines ->
            if (lines > lastLines) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
            }
            lastLines = lines
        }
    }

    // 用户消息里的图片/文件统一收进气泡里的矩形卡片，文本再长也不会把它们挤没
    if (role == MessageRole.USER) {
        UserMessageContent(
            parts = parts,
            assistant = assistant,
            onClick = onUserMessageClick,
            onLongClick = onUserMessageLongClick,
            menu = userMessageMenu,
            onClickCitation = handleClickCitation,
            hazeState = hazeState,
            joinedToPrevious = joinedToPrevious,
            joinedToNext = joinedToNext,
        )
        return
    }

    // Render parts in original order (group thinking/tool as chain-of-thought)
    val groupedParts = remember(parts) { parts.groupMessageParts() }
    // 最后一段正文之前的内容整体再折叠一层；展开后思考和工具仍按原来的方式折叠
    val lastBodyIndex = groupedParts.indexOfLast { block ->
        block is MessagePartBlock.ContentBlock && (block.part as? UIMessagePart.Text)?.text?.isNotBlank() == true
    }
    // 流式过程中也参与折叠：正在输出的正文留在外面，之前的思考和工具先收起来
    val blocksBeforeBody = if (role == MessageRole.ASSISTANT) {
        when {
            lastBodyIndex > 0 -> groupedParts.subList(0, lastBodyIndex)
            // 还没有正文（只有一段思考或一次工具调用）时整段内容都参与折叠
            lastBodyIndex <= 0 && groupedParts.isNotEmpty() &&
                groupedParts.none { it is MessagePartBlock.ContentBlock } -> groupedParts
            else -> emptyList()
        }
    } else {
        emptyList()
    }
    // 正文之前的思考/工具内容整体再折叠一层；单个纯思考链或单张工具卡也走这层折叠
    val hiddenBlocks = blocksBeforeBody
    // 等待审批的工具不能藏起来，否则用户看不到审批按钮
    val hiddenPendingTool = hiddenBlocks.any { block ->
        block is MessagePartBlock.ThinkingBlock && block.steps.any { it is ThinkingStep.ToolStep && it.tool.isPending }
    }
    val collapsibleCount = if (hiddenPendingTool) 0 else hiddenBlocks.size
    // 不能按 parts 记住：流式时每个 chunk 都会重建列表，展开状态会被重置
    var showEarlierSteps by remember { mutableStateOf(false) }

    if (collapsibleCount > 0) {
        EarlierStepsToggle(
            collapsed = !showEarlierSteps,
            count = collapsibleCount,
            onToggle = { showEarlierSteps = !showEarlierSteps },
        )
    }
    groupedParts.fastForEachIndexed { index, block ->
        if (collapsibleCount > 0 && !showEarlierSteps && index < collapsibleCount) {
            return@fastForEachIndexed
        }
        when (block) {
            is MessagePartBlock.ThinkingBlock -> {
                if (block.steps.isNotEmpty()) {
                    // 等待审批的工具不能折叠起来，否则用户看不到审批按钮，这一轮会一直卡在等待审批
                    val firstPendingIndex = block.steps.indexOfFirst { it is ThinkingStep.ToolStep && it.tool.isPending }
                    val collapsedVisibleCount = if (firstPendingIndex == -1) {
                        COLLAPSED_VISIBLE_STEPS
                    } else {
                        maxOf(COLLAPSED_VISIBLE_STEPS, block.steps.size - firstPendingIndex)
                    }
                    val chainEffect = settings.displaySetting.effectiveBackgroundEffect
                    val chainShape = adaptiveShape(20.dp)
                    val chainBaseColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    // 有源时着色交给 chatInputHaze；没有源时自己留一层半透明底，避免整块透明
                    val chainHasEffect = chainEffect != BackgroundEffectType.OFF && hazeState != null
                    ChainOfThought(
                        modifier = Modifier
                            .then(if (loading) Modifier else Modifier.animateContentSize())
                            .then(
                                if (chainHasEffect) {
                                    Modifier
                                        .clip(chainShape)
                                        .chatInputHaze(
                                            hazeState = hazeState,
                                            displaySetting = settings.displaySetting,
                                            containerColor = chainBaseColor,
                                            shape = chainShape,
                                        )
                                } else {
                                    Modifier
                                }
                            ),
                        steps = block.steps,
                        collapsedVisibleCount = collapsedVisibleCount,
                        border = if (chainEffect == BackgroundEffectType.BLUR) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        } else {
                            null
                        },
                        cardColors = CardDefaults.cardColors(
                            containerColor = if (chainHasEffect) {
                                Color.Transparent
                            } else {
                                chainBaseColor.copy(
                                    alpha = if (chainEffect == BackgroundEffectType.OFF) {
                                        settings.displaySetting.bubbleOpacity
                                    } else {
                                        settings.displaySetting.effectSurfaceAlpha
                                    },
                                )
                            },
                        ),
                    ) { step ->
                        when (step) {
                            is ThinkingStep.ReasoningStep -> {
                                key(step.reasoning.createdAt) {
                                    ChatMessageReasoningStep(
                                        reasoning = step.reasoning,
                                        model = model,
                                        assistant = assistant,
                                        generating = loading,
                                    )
                                }
                            }

                            is ThinkingStep.ToolStep -> {
                                key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                    ChatMessageToolStep(
                                        tool = step.tool,
                                        loading = loading && !step.tool.isExecuted,
                                        assistant = assistant,
                                        onToolApproval = onToolApproval,
                                        onToolAnswer = onToolAnswer,
                                    )
                                }
                            }

                            is ThinkingStep.ServerToolStep -> {
                                key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                    ChatMessageServerToolStep(tool = step.tool)
                                }
                            }

                            is ThinkingStep.NoticeStep -> ChatMessageNoticeStep(notice = step.notice)
                        }
                    }
                }
            }

            is MessagePartBlock.ChartBlock -> key(block.index) {
                val spec = remember(block.tool.input) { ChartSpec.fromJson(block.tool.inputAsJson()) }
                spec?.let { ChartCard(spec = it) }
            }

            is MessagePartBlock.ContentBlock -> key(block.index) {
                when (val part = block.part) {
                    is UIMessagePart.Text -> {
                        // 只有正在生成的最后一段文字可能停在没写完的语法上
                        val streamingText = loading && block.index == parts.lastIndex
                        // 正则替换只取决于正文和助手配置：本帧没有变化的旧消息不必再扫一遍全文
                        val renderedText = if (role == MessageRole.USER) {
                            part.text
                        } else {
                            remember(part.text, assistant?.regexes) {
                                part.text.replaceRegexes(
                                    assistant = assistant,
                                    scope = AssistantAffectScope.ASSISTANT,
                                    visual = true,
                                )
                            }
                        }
                        val textContent = @Composable {
                            if (role == MessageRole.USER) {
                                ChatMessageBubble(
                                    role = role,
                                    color = LocalUiSlots.current.userBubble.copy(alpha = settings.displaySetting.bubbleOpacity),
                                    hazeState = hazeState,
                                    joinedToPrevious = joinedToPrevious,
                                    joinedToNext = joinedToNext,
                                    onClick = { onUserMessageClick?.invoke() },
                                    // 用户气泡不占满整行，和助手的回复错开；全宽模式下贴到边
                                    modifier = Modifier
                                        .padding(start = adaptiveHorizontalPaddingValue(24.dp))
                                        .animateContentSize(),
                                ) {
                                    CollapsibleUserMessageText(
                                        text = part.text,
                                        assistant = assistant,
                                        onClickCitation = handleClickCitation,
                                    )
                                }
                            } else {
                                if (settings.displaySetting.showAssistantBubble) {
                                    ChatMessageBubble(
                                        role = role,
                                        color = LocalUiSlots.current.aiBubble.copy(alpha = settings.displaySetting.bubbleOpacity),
                                        hazeState = hazeState,
                                        joinedToPrevious = joinedToPrevious,
                                        joinedToNext = joinedToNext,
                                        modifier = if (streamingText) Modifier else Modifier.animateContentSize(),
                                    ) {
                                        MarkdownBlock(
                                            content = renderedText,
                                            streaming = streamingText,
                                            onClickCitation = handleClickCitation,
                                        )
                                    }
                                } else {
                                    MarkdownBlock(
                                        content = renderedText,
                                        streaming = streamingText,
                                        onClickCitation = handleClickCitation,
                                        modifier = if (streamingText) {
                                            Modifier
                                        } else {
                                            Modifier.animateContentSize()
                                        }
                                    )
                                }
                            }
                        }

                        // Markdown 由 BetterText 渲染，要用它自己的选择容器；生成中也可以选择
                        SelectableContent {
                            textContent()
                        }
                    }

                    is UIMessagePart.Video -> {
                        ChatMessageMediaTile(
                            icon = HugeIcons.Video01,
                            onClick = { context.openLocalFile(part.url) },
                        )
                    }

                    is UIMessagePart.Audio -> {
                        ChatMessageMediaTile(
                            icon = HugeIcons.MusicNote03,
                            onClick = { context.openLocalFile(part.url) },
                        )
                    }

                    is UIMessagePart.Image -> {
                        val isImageLoading =
                            part.url.isBlank() || part.url.matches(Regex("^data:image/[^;]*;base64,\\s*$"))
                        if (isImageLoading) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .shimmer(isLoading = true)
                            )
                        } else {
                            ZoomableAsyncImage(
                                model = part.url,
                                contentDescription = null,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.large)
                                    .height(72.dp)
                            )
                        }
                    }

                    is UIMessagePart.Document -> {
                        ChatMessageFileChip(
                            text = part.fileName,
                            onClick = { context.openLocalFile(part.url) },
                            icon = {
                                when (part.mime) {
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                                        Icon(
                                            painter = painterResource(R.drawable.docx),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    "application/pdf" -> {
                                        Icon(
                                            painter = painterResource(R.drawable.pdf),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    else -> {
                                        Icon(
                                            imageVector = HugeIcons.File02,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                        )
                    }

                    else -> {
                        // Skip unknown part types (e.g., deprecated ToolCall, ToolResult, Search)
                    }
                }
            }
        }
    }

    // Annotations (always rendered at the end)
    if (annotations.isNotEmpty()) {
        ChatMessageCitations(annotations = annotations)
    }
}

private const val USER_MESSAGE_COLLAPSED_LINES = 3

@Composable
private fun CollapsibleUserMessageText(
    text: String,
    assistant: Assistant?,
    onClickCitation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current
    val markdownPaddingPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    // 行高从实际排版量出来：fontSizeRatio、自定义字体、未指定 lineHeight 时都能对上，
    // 不能再拿 LocalTextStyle 的 lineHeight 猜，猜小了会把三行的话也算成需要折叠
    val lineHeightPx = remember(measurer, style) {
        measurer.measure(
            text = AnnotatedString("M"),
            style = style,
            maxLines = 1,
        ).size.height.toFloat().coerceAtLeast(1f)
    }
    val collapsedHeightPx = (lineHeightPx * USER_MESSAGE_COLLAPSED_LINES).roundToInt()

    val collapseProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = rememberSpatialSpec(),
        label = "userMessageCollapse",
    )

    BoxWithConstraints(modifier = modifier) {
        // 按正文本身的排版行数判断，量的是 MarkdownBlock 拿到的宽度（它自带左右各 4dp 内边距）
        val availableWidthPx = constraints.maxWidth - markdownPaddingPx
        val collapsible = remember(text, availableWidthPx, style, measurer) {
            availableWidthPx > 0 && measurer.measure(
                text = AnnotatedString(text.userMessageLineSample()),
                style = style,
                maxLines = USER_MESSAGE_COLLAPSED_LINES,
                constraints = Constraints(maxWidth = availableWidthPx),
            ).didOverflowHeight
        }

        Column {
            Box(
                modifier = Modifier
                    .clipToBounds()
                    .collapsibleContentHeight(
                        collapsedHeightPx = collapsedHeightPx,
                        collapsible = collapsible,
                        progress = { collapseProgress },
                    ),
            ) {
                MarkdownBlock(
                    content = text.replaceRegexes(
                        assistant = assistant,
                        scope = AssistantAffectScope.USER,
                        visual = true,
                    ),
                    onClickCitation = onClickCitation,
                )
            }
            if (collapsible) {
                Text(
                    text = stringResource(
                        if (expanded) {
                            R.string.chat_message_collapse_content
                        } else {
                            R.string.chat_message_expand_content
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// 段落之间的空行在渲染里只是间距，不会多占一行；量行数前先收掉，
// 否则两三个短段落会被算成五六行，三行以内也冒出展开按钮
private val UserMessageBlankLines = Regex("\\n{2,}")

private fun String.userMessageLineSample(): String =
    replace("\r\n", "\n").replace('\r', '\n').trimEnd().replace(UserMessageBlankLines, "\n")

/**
 * 正文始终按完整高度排版，只把可视区域的高度从三行渐开到全文。
 *
 * 让布局先量一遍不受限的内容，收起/展开只改裁切高度；文字不会先跳到三行、
 * 再等气泡慢慢缩，收起和展开都是一整块跟着边缘走的过渡。
 */
private fun Modifier.collapsibleContentHeight(
    collapsedHeightPx: Int,
    collapsible: Boolean,
    progress: () -> Float,
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
    )
    val fullHeight = placeable.height
    val height = if (collapsible && fullHeight > collapsedHeightPx) {
        (collapsedHeightPx + (fullHeight - collapsedHeightPx) * progress())
            .roundToInt()
            .coerceIn(collapsedHeightPx, fullHeight)
    } else {
        fullHeight
    }
    // 量的时候放开了高度限制，报出去还得守住父容器的上限
    val bounded = if (constraints.hasBoundedHeight) height.coerceAtMost(constraints.maxHeight) else height
    layout(placeable.width, bounded) {
        placeable.place(0, 0)
    }
}

private val BubbleCorner = 24.dp
private val BubbleTailCorner = 6.dp
private val BubblePressedCorner = 12.dp

// 同组相邻气泡接缝处的圆角：上一条的下圆角与下一条的上圆角都收成这个值，两条气泡看着是连着的
private val BubbleJoinedCorner = 8.dp

/**
 * 消息气泡：大圆角，靠头像一侧的顶角收紧；可点击时按下其余圆角也跟着收紧。
 * 与同组相邻气泡相接的那条边统一收成 [BubbleJoinedCorner]，看上去是一条连线。
 */
@Composable
private fun ChatMessageBubble(
    role: MessageRole,
    color: Color,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    popup: (@Composable () -> Unit)? = null,
    joinedToPrevious: Boolean = false,
    joinedToNext: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressProgress by animatePressProgress(interactionSource)
    val fullWidth = LocalFullWidthComponents.current
    val radius = adaptiveCornerRadius(LocalSettings.current.displaySetting.uiCornerRadius.dp)
    val corner = lerp(radius, minOf(radius, BubblePressedCorner), pressProgress)
    val tail = minOf(BubbleTailCorner, radius * 0.25f)
    // 接缝那一侧不能再留小尾巴，否则两条气泡之间会露一个豁口
    val join = minOf(BubbleJoinedCorner, corner)
    val shape = if (role == MessageRole.USER) {
        RoundedCornerShape(
            topStart = if (joinedToPrevious) join else corner,
            topEnd = if (joinedToPrevious) join else tail,
            bottomEnd = if (joinedToNext) join else corner,
            bottomStart = if (joinedToNext) join else corner,
        )
    } else {
        RoundedCornerShape(
            topStart = if (joinedToPrevious) join else tail,
            topEnd = if (joinedToPrevious) join else corner,
            bottomEnd = if (joinedToNext) join else corner,
            bottomStart = if (joinedToNext) join else corner,
        )
    }
    val displaySetting = LocalSettings.current.displaySetting
    val effect = displaySetting.effectiveBackgroundEffect
    val useEffect = hazeState != null && effect != BackgroundEffectType.OFF
    val hazeModifier = if (useEffect && hazeState != null) {
        Modifier.chatInputHaze(
            hazeState = hazeState,
            displaySetting = displaySetting,
            containerColor = color,
            shape = shape,
        )
    } else {
        Modifier
    }
    val contentColor = contentColorFor(color)
    Box(
        modifier = modifier
            // 全宽模式下气泡不再按内容收缩，左右边缘对齐父容器
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .then(if (useEffect) Modifier.graphicsLayer(shape = shape, clip = true) else Modifier)
            .clipDrawing(shape)
            .then(hazeModifier)
            // 特效开启时着色层由 chatInputHaze 叠在模糊之上，这里不再铺第二层
            .then(if (useEffect) Modifier else Modifier.background(color))
            .then(
                if (effect == BackgroundEffectType.BLUR) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .then(
                // 长按（用户消息的操作菜单）和点击（编辑）共用同一个交互源，
                // 按下时圆角收缩的反馈对两种手势都在
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onLongClickLabel = onLongClickLabel,
                        onLongClick = onLongClick,
                        onClick = { onClick?.invoke() },
                    )
                } else {
                    Modifier
                },
            )
            // MarkdownBlock 自带 4dp 的水平内边距
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
        // 长按菜单挂在气泡的布局节点里，弹窗以气泡为锚点定位
        popup?.invoke()
    }
}

/**
 * 只裁剪绘制内容，不像 [clip] 那样把形状设成图层轮廓。
 *
 * 四个角半径不同时图层轮廓是 Path，Compose 判断触摸点是否落在轮廓内时用 Path 求交，
 * 坐标超过 8192px 后 float 精度不够，求交结果为空，长消息下半部分（例如展开的长代码块）就收不到触摸了。
 */
private fun Modifier.clipDrawing(shape: Shape): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    onDrawWithContent {
        clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}

@Composable
private fun ChatMessageCitations(annotations: List<UIMessageAnnotation>) {
    var expand by remember { mutableStateOf(false) }
    val urls = remember(annotations) {
        annotations.map { annotation ->
            when (annotation) {
                is UIMessageAnnotation.UrlCitation -> annotation.url
            }
        }
    }
    Column(
        modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChatMessageFileChip(
            text = stringResource(R.string.citations_count, annotations.size),
            onClick = { expand = !expand },
            color = LocalUiSlots.current.aiBubble,
            icon = { FaviconRow(urls = urls, size = 18.dp) },
        )
        if (expand) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        annotations.fastForEachIndexed { index, annotation ->
                            when (annotation) {
                                is UIMessageAnnotation.UrlCitation -> {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Favicon(annotation.url, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = buildAnnotatedString {
                                                append("${index + 1}. ")
                                                withLink(LinkAnnotation.Url(annotation.url)) {
                                                    append(annotation.title.urlDecode())
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 用户消息：附件和文本放在同一个气泡里，附件用和输入框预览一样的矩形卡片。
 * 超过三个时只画前三个，第三个后面标出还剩几个。
 */
@Composable
private fun UserMessageContent(
    parts: List<UIMessagePart>,
    assistant: Assistant?,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    menu: (@Composable () -> Unit)?,
    onClickCitation: (String) -> Unit,
    hazeState: HazeState?,
    joinedToPrevious: Boolean = false,
    joinedToNext: Boolean = false,
) {
    val settings = LocalSettings.current.displaySetting
    val attachments = remember(parts) {
        parts.filter {
            it is UIMessagePart.Image ||
                it is UIMessagePart.Document ||
                it is UIMessagePart.Video ||
                it is UIMessagePart.Audio
        }
    }
    val texts = remember(parts) {
        parts.filterIsInstance<UIMessagePart.Text>().filter { it.text.isNotBlank() }
    }
    if (attachments.isEmpty() && texts.isEmpty()) return

    ChatMessageBubble(
        role = MessageRole.USER,
        color = LocalUiSlots.current.userBubble.copy(alpha = settings.bubbleOpacity),
        hazeState = hazeState,
        joinedToPrevious = joinedToPrevious,
        joinedToNext = joinedToNext,
        onClick = { onClick?.invoke() },
        onLongClick = onLongClick,
        onLongClickLabel = stringResource(R.string.chat_message_long_press_actions),
        popup = menu,
        modifier = Modifier
            .padding(start = adaptiveHorizontalPaddingValue(24.dp)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (attachments.isNotEmpty()) {
                // 卡片自带点击，会先把 down 事件消费掉，气泡外层就收不到长按了；
                // 长按一并挂在卡片上，点图片消息也能拉出菜单
                UserAttachmentTiles(attachments = attachments, onLongClick = onLongClick)
            }
            UserMessageOcrResults(parts = parts)
            texts.forEach { text ->
                CollapsibleUserMessageText(
                    text = text.text,
                    assistant = assistant,
                    onClickCitation = onClickCitation,
                )
            }
        }
    }
}

/**
 * 辅助 OCR 读出来的文字：挂在图片下面，让用户看到模型实际收到的内容。
 * 卡片外观和附件卡片一致；长文本沿用用户消息的折叠规则。
 */
/** 模型实际读到的附件内容：图片 OCR 文字，以及视频识别结果（失败时也留一张红字卡片）。 */
private data class MessageRecognitionText(
    val text: String,
    val video: Boolean,
    val error: Boolean,
)

@Composable
private fun UserMessageOcrResults(parts: List<UIMessagePart>) {
    val items = remember(parts) {
        parts.mapNotNull { part ->
            when (part) {
                is UIMessagePart.Image -> part.metadataAs<OcrMetadata>()?.text
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { MessageRecognitionText(text = it, video = false, error = false) }

                is UIMessagePart.Video -> part.metadataAs<VideoRecognitionMetadata>()
                    ?.let { metadata ->
                        metadata.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            MessageRecognitionText(text = it, video = true, error = metadata.error)
                        }
                    }

                else -> null
            }
        }
    }
    if (items.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(
                    if (items.any { it.video }) {
                        R.string.chat_message_recognition_result
                    } else {
                        R.string.chat_message_ocr_result
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            items.forEachIndexed { index, item ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val label = when {
                        item.video && item.error -> R.string.chat_message_video_recognition_failed
                        item.video -> R.string.chat_message_video_recognition_result
                        items.size > 1 -> R.string.chat_message_ocr_result_image
                        else -> null
                    }
                    if (label != null) {
                        Text(
                            text = if (label == R.string.chat_message_ocr_result_image) {
                                stringResource(label, index + 1)
                            } else {
                                stringResource(label)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.error) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            },
                        )
                    }
                    CollapsibleUserMessageText(
                        text = item.text,
                        assistant = null,
                        onClickCitation = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun UserAttachmentTiles(
    attachments: List<UIMessagePart>,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val visible = attachments.take(3)
    val hiddenCount = (attachments.size - visible.size).coerceAtLeast(0)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visible.forEachIndexed { index, part ->
            val more = if (index == visible.lastIndex) hiddenCount else 0
            UserAttachmentTile(
                part = part,
                moreCount = more,
                onClick = {
                    val url = when (part) {
                        is UIMessagePart.Image -> part.url
                        is UIMessagePart.Document -> part.url
                        is UIMessagePart.Video -> part.url
                        is UIMessagePart.Audio -> part.url
                        else -> null
                    }
                    if (!url.isNullOrBlank()) context.openLocalFile(url)
                },
                onLongClick = onLongClick,
            )
        }
    }
}

@Composable
private fun UserAttachmentTile(
    part: UIMessagePart,
    moreCount: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val partUrl = when (part) {
        is UIMessagePart.Image -> part.url
        is UIMessagePart.Document -> part.url
        is UIMessagePart.Video -> part.url
        is UIMessagePart.Audio -> part.url
        else -> ""
    }
    val title = when (part) {
        is UIMessagePart.Document -> part.fileName.ifBlank { partUrl.substringAfterLast('/') }
        else -> partUrl.substringAfterLast('/').ifBlank {
            when (part) {
                is UIMessagePart.Image -> "image"
                is UIMessagePart.Video -> "video"
                is UIMessagePart.Audio -> "audio"
                else -> "file"
            }
        }
    }
    Surface(
        // 用带 onClick 的 Surface 时它自带 48dp 最小触控尺寸，换成分开挂手势后要自己补回来
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                when (part) {
                    is UIMessagePart.Image -> AsyncImage(
                        model = part.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    is UIMessagePart.Video -> Icon(HugeIcons.Video01, null, Modifier.size(18.dp))
                    is UIMessagePart.Audio -> Icon(HugeIcons.MusicNote03, null, Modifier.size(18.dp))
                    else -> Icon(HugeIcons.Files02, null, Modifier.size(18.dp))
                }
                if (moreCount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+$moreCount",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                            ),
                            color = Color.White,
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
        }
    }
}

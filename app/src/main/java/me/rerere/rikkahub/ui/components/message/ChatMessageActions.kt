package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.FavouriteCircle
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.TextSelection
import me.rerere.hugeicons.stroke.Translate
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.hugeicons.stroke.WebDesign01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.theme.rememberFastSpatialSpec
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.removeBracketedContent
import me.rerere.rikkahub.utils.toLocalString
import me.rerere.rikkahub.utils.toMessageTimeString
import me.rerere.ui.components.RikkaConfirmDialog
import java.util.Locale

@Composable
fun ColumnScope.ChatMessageActionButtons(
    message: UIMessage,
    node: MessageNode,
    onUpdate: (MessageNode) -> Unit,
    onRegenerate: () -> Unit,
    onOpenActionSheet: () -> Unit,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    var isPendingDelete by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(isPendingDelete) {
        if (isPendingDelete) {
            delay(3000) // 3秒后自动取消
            isPendingDelete = false
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ChatMessageActionButton(
            icon = HugeIcons.Copy01,
            contentDescription = stringResource(R.string.copy),
            onClick = { context.copyMessageToClipboard(message) },
        )

        ChatMessageActionButton(
            icon = HugeIcons.Refresh03,
            contentDescription = stringResource(R.string.regenerate),
            onClick = {
                if (message.role == MessageRole.USER) {
                    showRegenerateConfirm = true
                } else {
                    onRegenerate()
                }
            },
        )

        if (message.role == MessageRole.ASSISTANT) {
            val tts = LocalTTSState.current
            val isSpeaking by tts.isSpeaking.collectAsState()
            val isAvailable by tts.isAvailable.collectAsState()
            // 朗读中换成带底色的方角按钮
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                IconToggleButton(
                    checked = isSpeaking,
                    onCheckedChange = { speak ->
                        if (speak) {
                            val text = message.toText()
                            var textToSpeak = text
                            if (settings.displaySetting.ttsOnlyReadQuoted) {
                                textToSpeak = textToSpeak.extractQuotedContentAsText() ?: textToSpeak
                            }
                            if (settings.displaySetting.ttsOnlyReadOutsideBrackets) {
                                textToSpeak = textToSpeak.removeBracketedContent() ?: textToSpeak
                            }
                            tts.speak(textToSpeak)
                        } else {
                            tts.stop()
                        }
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(IconButtonDefaults.extraSmallContainerSize()),
                    enabled = isAvailable,
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = if (isSpeaking) HugeIcons.StopCircle else HugeIcons.VolumeHigh,
                        contentDescription = stringResource(R.string.tts),
                        modifier = Modifier.size(ActionIconSize),
                    )
                }
            }

            // Translation button
            if (onTranslate != null) {
                ChatMessageActionButton(
                    icon = HugeIcons.Translate,
                    contentDescription = stringResource(R.string.translate),
                    onClick = { showTranslateDialog = true },
                )
            }
        }

        ChatMessageActionButton(
            icon = HugeIcons.MoreVertical,
            contentDescription = stringResource(R.string.more_options),
            onClick = onOpenActionSheet,
        )

        ChatMessageBranchSelector(
            node = node,
            onUpdate = onUpdate,
        )

        if (settings.displaySetting.showDateTimeInMessage) {
            Text(
                text = message.createdAt.toJavaLocalDateTime().toMessageTimeString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    // Translation dialog
    if (showTranslateDialog && onTranslate != null) {
        LanguageSelectionDialog(
            onLanguageSelected = { language ->
                showTranslateDialog = false
                onTranslate(message, language)
            },
            onClearTranslation = {
                showTranslateDialog = false
                onClearTranslation(message)
            },
            onDismissRequest = {
                showTranslateDialog = false
            },
        )
    }

    // Regenerate confirmation dialog
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
        text = { Text(stringResource(R.string.regenerate_confirm_message)) }
    )
}

private val ActionIconSize = 18.dp

// 消息下方的小号图标按钮，按下时圆形收成方角
@Composable
internal fun ChatMessageActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // 按钮排得密，不让 48dp 的最小触控尺寸把间距撑开
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier.size(IconButtonDefaults.extraSmallContainerSize()),
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ActionIconSize),
            )
        }
    }
}

private class MessageSheetAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ChatMessageActionsSheet(
    message: UIMessage,
    model: Model?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onFork: () -> Unit,
    onSelectAndCopy: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onWebViewPreview: () -> Unit,
    onDismissRequest: () -> Unit,
    // 弹窗顶部的一排快捷操作
    quickActions: (@Composable () -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        ChatMessageActionList(
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
            // 弹窗是另一层窗口，和菜单一样：先关再由动作打开下一个界面
            onRunAction = { action ->
                onDismissRequest()
                action()
            },
            quickActions = quickActions,
        )
    }
}

/**
 * 消息操作的内容：助手消息的「更多」弹窗和用户消息的长按菜单共用同一份，
 * 两边的入口不同，条目和顺序不要分叉。
 */
@Composable
private fun ChatMessageActionList(
    message: UIMessage,
    model: Model?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onFork: () -> Unit,
    onSelectAndCopy: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onWebViewPreview: () -> Unit,
    onRunAction: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    quickActions: (@Composable () -> Unit)? = null,
) {
    val hasTextContent = message.parts.filterIsInstance<UIMessagePart.Text>()
        .any { it.text.isNotBlank() }
    val actions = buildList {
        add(MessageSheetAction(HugeIcons.TextSelection, stringResource(R.string.select_and_copy), onClick = onSelectAndCopy))
        if (hasTextContent) {
            add(MessageSheetAction(HugeIcons.WebDesign01, stringResource(R.string.render_with_webview), onClick = onWebViewPreview))
        }
        add(MessageSheetAction(HugeIcons.Edit01, stringResource(R.string.edit), onClick = onEdit))
        add(MessageSheetAction(HugeIcons.Share04, stringResource(R.string.share), onClick = onShare))
        add(MessageSheetAction(HugeIcons.GitFork, stringResource(R.string.create_fork), onClick = onFork))
        if (onToggleFavorite != null) {
            add(
                MessageSheetAction(
                    icon = HugeIcons.FavouriteCircle,
                    label = stringResource(
                        if (isFavorite) R.string.chat_message_remove_favorite
                        else R.string.chat_message_add_favorite
                    ),
                    onClick = onToggleFavorite,
                )
            )
        }
        add(MessageSheetAction(HugeIcons.Delete01, stringResource(R.string.delete), destructive = true, onClick = onDelete))
    }

    Column(
        // 顺序不能反：内边距要在滚动里面，否则条目会滚到圆角外面被裁掉
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (quickActions != null) {
            quickActions()
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            actions.fastForEachIndexed { index, action ->
                SegmentedListItem(
                    onClick = { onRunAction(action.onClick) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = actions.size),
                    colors = if (action.destructive) {
                        ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            leadingContentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else {
                        ListItemDefaults.segmentedColors()
                    },
                    leadingContent = {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                        )
                    },
                ) {
                    Text(action.label)
                }
            }
        }

        // Message Info
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                Text(message.createdAt.toJavaLocalDateTime().toLocalString())
                if (model != null) {
                    Text(model.displayName)
                }
            }
        }
    }
}

/**
 * 用户消息长按菜单顶部那排快捷操作。
 *
 * 这几个按钮原来常驻在气泡下方，收进菜单之后气泡只剩内容，同一条消息也不再占两行高度；
 * 分支切换和时间跟按钮排在同一颗胶囊里，宽度够时一行放得下。
 *
 * 重新生成只把「要确认」这件事交出去，确认框由调用方在菜单外渲染：
 * 菜单本身是个 Popup 窗口，框弹在它上面时这一下算点到菜单外，菜单会先把自己关掉。
 */
@Composable
internal fun UserMessageMenuQuickActions(
    message: UIMessage,
    node: MessageNode,
    onRegenerateRequest: () -> Unit,
    onUpdate: (MessageNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ChatMessageActionButton(
                icon = HugeIcons.Copy01,
                contentDescription = stringResource(R.string.copy),
                onClick = { context.copyMessageToClipboard(message) },
            )
            ChatMessageActionButton(
                icon = HugeIcons.Refresh03,
                contentDescription = stringResource(R.string.regenerate),
                onClick = onRegenerateRequest,
            )
            ChatMessageBranchSelector(
                node = node,
                onUpdate = onUpdate,
            )
            if (settings.displaySetting.showDateTimeInMessage) {
                Text(
                    text = message.createdAt.toJavaLocalDateTime().toMessageTimeString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

private val MessageMenuMaxWidth = 300.dp

// 菜单与气泡之间的间隙；同时当作阴影的绘制余量，popup 窗口不会给边界外的阴影留空间
private val MessageMenuMargin = 8.dp

// 展开动画的起始缩放：从气泡那个角开始长，不从 0 开始，否则像是凭空冒出来
private const val MenuExpandFromScale = 0.86f

/**
 * 用户消息的长按菜单：贴着气泡原地打开，不再从屏幕底部滑上来。
 *
 * 位置由 [BubbleMenuPositionProvider] 计算：优先气泡下方，放不下翻到上方，
 * 水平方向与气泡右缘对齐并收进窗口。窗口太矮时菜单自己滚动，不会顶出屏幕。
 *
 * 展开时从贴着气泡的那个角长出来，收回沿同一条路退回去；反向动画演完才真正移除窗口。
 */
@Composable
internal fun UserMessageActionsMenu(
    message: UIMessage,
    model: Model?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onFork: () -> Unit,
    onSelectAndCopy: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onWebViewPreview: () -> Unit,
    quickActions: (@Composable () -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    val enterSpec = rememberSpatialSpec<Float>()
    val exitSpec = rememberFastSpatialSpec<Float>()
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerDpSize
    val marginPx = with(density) { MessageMenuMargin.roundToPx() }
    val progress = remember { Animatable(0f) }
    // 展开原点跟着菜单位置走：贴在气泡下方从右上角长出来，翻到上方从右下角长回去
    var originY by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    val positionProvider = remember(density, marginPx) {
        BubbleMenuPositionProvider(marginPx) { placedBelow ->
            originY = if (placedBelow) 0f else 1f
        }
    }
    // 收回分两步：反向动画演完，再让外层把菜单从组合里拿掉，否则会瞬间消失
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val dismiss: () -> Unit = { closing = true }
    // 条目上的动作要等菜单收回去再执行：动画还在演就把下一个窗口翻上来，两个窗口会叠着，
    // 那一下点击也会被还活着的 popup 吃掉
    val dismissAfter: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            pendingAction = action
            closing = true
        }
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, enterSpec)
    }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        progress.animateTo(0f, exitSpec)
        val action = pendingAction
        pendingAction = null
        action?.invoke()
        onDismissRequest()
    }
    // 宽度取「窗口能给的」与「菜单想要的」里小的那个。下限不能用绝对 dp：
    // 分屏小窗比 200dp 还窄时，写死的下限会把菜单顶出窗口，右边被裁掉
    val menuWidth = minOf(MessageMenuMaxWidth, windowSize.width - MessageMenuMargin * 2)
    // 高度只占窗口六成：菜单再高就会盖住它对着的那条气泡，看不清在操作哪条消息。
    // 放不下的条目走内部滚动，这与 Material 菜单的常态一致
    val maxHeight = windowSize.height * 0.6f

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = dismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        // 外面这层留白是给阴影的：popup 窗口会把边界外的绘制裁掉，不留白就没有阴影
        Box(
            modifier = Modifier
                .padding(MessageMenuMargin)
                // 只缩放绘制，不动布局：popup 的测量尺寸始终是最终大小，位置不会跟着抖
                .graphicsLayer {
                    val shown = progress.value
                    // 弹簧会略微过冲，透明度夹回 0..1，缩放那点回弹留着
                    alpha = shown.coerceIn(0f, 1f)
                    val scale = MenuExpandFromScale + (1f - MenuExpandFromScale) * shown
                    scaleX = scale
                    scaleY = scale
                    // 贴着气泡的那个角不动，菜单从那里长出来
                    transformOrigin = TransformOrigin(1f, originY)
                },
        ) {
            Surface(
                modifier = Modifier
                    .width(menuWidth)
                    .heightIn(max = maxHeight),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                ChatMessageActionList(
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
                    onRunAction = dismissAfter,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    quickActions = quickActions,
                )
            }
        }
    }
}

/**
 * 把菜单贴在气泡旁边：先试气泡下方，放不下翻到上方，都放不下就贴住窗口下沿；
 * 水平方向让菜单右缘对齐气泡右缘（用户消息都靠右），再整体收进窗口。
 */
private class BubbleMenuPositionProvider(
    private val marginPx: Int,
    // 告诉菜单这次落在气泡的哪一侧，展开动画的原点据此选上边还是下边
    private val onPlacedBelow: (Boolean) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // popupContentSize 里已经含了外层那圈留给阴影的边距，这里再加一次
        // 会让气泡和菜单之间的实际间隙变成两倍
        val below = anchorBounds.bottom
        val above = anchorBounds.top - popupContentSize.height
        val bottomLimit = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
        val y = when {
            below + popupContentSize.height <= windowSize.height - marginPx -> below
            above >= marginPx -> above
            // 两边都放不下时往空间大的一侧贴边，不要一律往下压，否则菜单会整块压住气泡
            anchorBounds.center.y < windowSize.height / 2 -> bottomLimit
            else -> marginPx
        }
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceAtMost(windowSize.width - popupContentSize.width - marginPx)
            .coerceAtLeast(marginPx)
        // 按菜单中心落在气泡中心的哪一侧判断，贴边压缩的情况也算得对
        onPlacedBelow(y + popupContentSize.height / 2 >= anchorBounds.center.y)
        return IntOffset(x, y)
    }
}

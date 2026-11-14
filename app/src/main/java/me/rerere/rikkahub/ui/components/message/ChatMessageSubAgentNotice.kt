package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.modifier.MotionExpandCollapse
import me.rerere.rikkahub.ui.theme.rememberFastSpatialSpec
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec
import me.rerere.ui.components.AnimatedAlertDialog

/**
 * 后台子代理跑完后自动回投的结果。
 *
 * 它进请求时用的是 USER 角色（各家 provider 都能带过去），但这句话不是用户说的，
 * 所以这里按系统通知渲染：标题标明来源，正文用低对比度的卡片承载，不套用户气泡。
 * 正文可能很长，点标题行可以折叠。
 *
 * 长按标题行可以把这条通知收掉或改写：回传结果偶尔会带着不需要的内容，
 * 留在上下文里既占 token 又会影响后面几轮，得让用户能就地处理。
 */
@Composable
fun ChatMessageSubAgentNotice(
    message: UIMessage,
    modifier: Modifier = Modifier,
    onEdit: ((String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    // 回传结果通常很长，默认收起，点标题行再展开
    var expanded by remember(message.createdAt) { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    // 两样都没有时这条通知是只读的：不给长按手势，也不弹空菜单
    val hasActions = onEdit != null || onDelete != null
    val openMenu: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuExpanded = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { expanded = !expanded },
                        onLongClick = if (hasActions) openMenu else null,
                    )
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.AiBrain01,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.chat_message_sub_agent_notice_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.chat_message_sub_agent_notice_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.onSurfaceVariant,
                )
            }
            // 贴着标题行开，锚点就是这一行；菜单项最多两条，单独做一套带进退场的小菜单。
            // 只有收起动画演完（onDismissRequest）才会把 menuExpanded 置 false，从这里整块离开组合
            if (hasActions && menuExpanded) {
                NoticeActionMenu(
                    onDismissRequest = { menuExpanded = false },
                    onEdit = onEdit?.let {
                        {
                            draft = message.toText().trim()
                            editing = true
                        }
                    },
                    onDelete = onDelete,
                )
            }
        }
        MotionExpandCollapse(visible = expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    MarkdownBlock(
                        content = message.toText().trim(),
                        onClickCitation = {},
                    )
                }
            }
        }
    }

    if (editing) {
        AnimatedAlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.chat_message_sub_agent_notice_edit_title)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 320.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editing = false
                        onEdit?.invoke(draft.trim())
                    },
                    enabled = draft.isNotBlank(),
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private val NoticeMenuMargin = 8.dp

// 展开动画的起始缩放：从贴着标题行的那个角长出来，不从 0 开始
private const val NoticeMenuExpandFromScale = 0.88f

/**
 * 子代理通知的长按菜单：贴着标题行原地展开。
 *
 * 展开时从贴住标题行的那个角长出来，收起沿原路缩回去；反向动画演完才让外层把菜单拿掉，
 * 否则点一下窗口就没了，看不出收起的过渡。
 */
@Composable
private fun NoticeActionMenu(
    onDismissRequest: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val enterSpec = rememberSpatialSpec<Float>()
    val exitSpec = rememberFastSpatialSpec<Float>()
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 展开原点跟着菜单位置走：落在标题行下方从左上角长出来，翻到上方从左下角长回去
    var originY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val marginPx = with(density) { NoticeMenuMargin.roundToPx() }
    val positionProvider = remember(marginPx) {
        NoticeMenuPositionProvider(marginPx) { placedBelow ->
            originY = if (placedBelow) 0f else 1f
        }
    }
    // 条目上的动作等菜单收回去再执行，避免点完立刻换界面把收起动画截断
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

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = { closing = true },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        // 外面这层留白是给阴影的：popup 窗口会把边界外的绘制裁掉，不留白就看不到阴影
        Box(modifier = Modifier.padding(NoticeMenuMargin)) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 172.dp, max = 220.dp)
                    .graphicsLayer {
                        val shown = progress.value
                        // 弹簧会过冲，透明度夹回 0..1，缩放那点回弹留着
                        alpha = shown.coerceIn(0f, 1f)
                        val scale = NoticeMenuExpandFromScale +
                            (1f - NoticeMenuExpandFromScale) * shown
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, originY)
                    },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    onEdit?.let { action ->
                        NoticeMenuItem(
                            icon = HugeIcons.Edit01,
                            label = stringResource(R.string.chat_message_sub_agent_notice_edit),
                            onClick = { dismissAfter(action) },
                        )
                    }
                    onDelete?.let { action ->
                        NoticeMenuItem(
                            icon = HugeIcons.Delete01,
                            label = stringResource(R.string.delete),
                            destructive = true,
                            onClick = { dismissAfter(action) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeMenuItem(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = contentColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}

/**
 * 菜单优先落在标题行下方，放不下翻到上方，水平方向从标题行左缘展开并收进窗口。
 * [onPlacedBelow] 告诉菜单这次落在哪一侧，决定展开动画的原点。
 */
private class NoticeMenuPositionProvider(
    private val marginPx: Int,
    private val onPlacedBelow: (Boolean) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val below = anchorBounds.bottom
        val above = anchorBounds.top - popupContentSize.height
        val bottomLimit = (windowSize.height - popupContentSize.height - marginPx)
            .coerceAtLeast(marginPx)
        val y = when {
            below + popupContentSize.height <= windowSize.height - marginPx -> below
            above >= marginPx -> above
            // 两边都放不下时往空间大的一侧贴边
            anchorBounds.center.y < windowSize.height / 2 -> bottomLimit
            else -> marginPx
        }
        val x = anchorBounds.left
            .coerceAtMost(windowSize.width - popupContentSize.width - marginPx)
            .coerceAtLeast(marginPx)
        onPlacedBelow(y >= anchorBounds.bottom)
        return IntOffset(x, y)
    }
}

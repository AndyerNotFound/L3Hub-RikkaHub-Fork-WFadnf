package me.rerere.rikkahub.ui.pages.chat

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.canGlassPane
import me.rerere.rikkahub.ui.components.ui.glassSurface
import me.rerere.rikkahub.ui.theme.LocalTerminalMode
import me.rerere.rikkahub.ui.theme.TerminalPalette
import me.rerere.rikkahub.ui.theme.appButtonShadow

/**
 * 横屏右侧栏能承载的面板。
 *
 * 这些内容原来都是盖在屏幕下半截的 [androidx.compose.material3.ModalBottomSheet]，
 * 或者顶栏底下拉开的详情页。横屏时屏幕只有 400dp 出头的高度，弹层一开聊天区就没了，
 * 所以改成贴右边的一栏：宽度够放下内容，聊天区也还留着。
 */
internal enum class ChatSidePane(
    // 面板占聊天区宽度的比例，第二个值是上限：模型列表和任务树本身是双栏布局，要更宽
    val widthFraction: Float,
    val maxWidth: Dp,
    // 面板自己画标题和关闭按钮；null 表示内容自带标题行（会话详情、模型列表）
    @StringRes val titleRes: Int? = null,
) {
    Details(0.38f, 420.dp, null),
    Usage(0.38f, 420.dp, R.string.chat_message_usage_title),
    Files(0.42f, 460.dp, R.string.chat_page_side_panel_files),
    TaskTree(0.52f, 560.dp, R.string.task_tree_title),
    ModelPicker(0.54f, 580.dp, null),
    BackgroundTasks(0.42f, 480.dp, R.string.background_tasks_title),
}

/**
 * 右侧栏的宿主：底板、进出场动画、点外部关闭、返回键关闭。
 *
 * 面板是模态的，和它替掉的那些弹层一样——点聊天区任何位置都会收起来。
 * 内容由调用方按 [ChatSidePane] 给出，宿主不管里面是什么。
 */
@Composable
internal fun ChatSidePanelHost(
    pane: ChatSidePane?,
    onDismiss: () -> Unit,
    hazeState: HazeState?,
    content: @Composable ColumnScope.(ChatSidePane) -> Unit,
) {
    // 退场动画期间 pane 已经是 null，留一份最后的值：否则面板往外滑的时候里面先空掉，
    // 看起来像闪了一下
    var lastPane by remember { mutableStateOf(pane) }
    SideEffect {
        if (pane != null) lastPane = pane
    }
    val shown = pane ?: lastPane

    val terminal = LocalTerminalMode.current
    val shape = if (terminal) {
        RectangleShape
    } else {
        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
    }
    val containerColor = if (terminal) {
        TerminalPalette.Background
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val scrimColor = if (terminal) {
        Color.Black.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.scrim.copy(alpha = 0.18f)
    }

    BackHandler(enabled = pane != null, onBack = onDismiss)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val available = maxWidth
        val fraction = shown?.widthFraction ?: 0.4f
        val cap = shown?.maxWidth ?: 460.dp
        // 先按比例算，再夹住：不低于 300dp（再窄放不下两栏内容），不超过每种面板自己的上限，
        // 也不能超过可用宽度。窗口宽到 800dp 以上时比例才真正起作用
        val panelWidth = (available * fraction)
            .coerceIn(minOf(300.dp, available), available)
            .coerceAtMost(cap)

        AnimatedVisibility(
            visible = pane != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = pane != null,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        ) {
            Surface(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight()
                    .appButtonShadow(shape, 6.dp)
                    .glassSurface(
                        shape = shape,
                        baseColor = containerColor,
                        hazeState = hazeState,
                        radius = 16.dp,
                        // 大块浮层：只糊注册过的源，正文不在源里，透明度留低了文字会透上来
                        minAlpha = 0.95f,
                    ),
                shape = shape,
                color = if (canGlassPane(hazeState)) Color.Transparent else containerColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    val titleRes = shown?.titleRes
                    if (titleRes != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, top = 8.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = HugeIcons.Cancel01,
                                    contentDescription = stringResource(R.string.close),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    if (shown != null) {
                        content(shown)
                    }
                }
            }
        }
    }
}

package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.appButtonShadow

/**
 * 空会话输入区的槽位最小高度。
 *
 * [minHeightPx] 会读到卡片和输入框的实测高度，这些值都是在测量阶段写回来的：放在组合阶段读，
 * 收放动画的每一帧都会让整个 [ChatInput] 重组一次（清单、悬浮栏、按钮行全在内）；
 * 放到测量阶段读，最多多跑一次测量，组合不参与。
 *
 * 子项按原约束量，不把槽位当 minHeight 传下去：卡片顶边钉在槽位顶部，
 * 高出来的部分留在它下面，槽位高度不会反过来改变子项自己的测量结果。
 */
internal fun Modifier.cardSlotMinHeight(minHeightPx: () -> Int): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        val height = placeable.height
            .coerceAtLeast(minHeightPx())
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(placeable.width, height) {
            placeable.place(0, 0)
        }
    }

@Composable
internal fun SendButton(
    loading: Boolean,
    empty: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showStop = loading && empty
    val shape = when {
        LocalFullWidthComponents.current -> RoundedCornerShape(0.dp)
        else -> CircleShape
    }
    val containerColor = when {
        showStop -> MaterialTheme.colorScheme.errorContainer
        empty -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when {
        showStop -> MaterialTheme.colorScheme.onErrorContainer
        empty -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(30.dp)
            .testTag("chat_send_button")
            .clip(shape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                // 图标用 tint 单独设色，不会更新 LocalContentColor；涟漪也要跟随按钮前景色。
                indication = ripple(color = contentColor),
                enabled = showStop || !empty,
                onClick = onClick,
                onLongClick = onLongClick,
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .appButtonShadow(shape, 2.dp),
            shape = shape,
            color = containerColor,
            content = {},
        )
        Icon(
            imageVector = if (showStop) HugeIcons.Cancel01 else HugeIcons.ArrowUp02,
            contentDescription = stringResource(if (showStop) R.string.stop else R.string.send),
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun BackgroundTasksButton(
    running: Int,
    onClick: () -> Unit,
) {
    BadgedBox(
        modifier = Modifier.padding(horizontal = 5.dp),
        badge = {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text(running.toString()) }
        },
    ) {
        ActionIconButton(onClick = onClick) {
            Icon(
                imageVector = HugeIcons.ComputerTerminal01,
                contentDescription = stringResource(R.string.background_tasks_running, running),
            )
        }
    }
}

@Composable
internal fun ActionIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = when {
        LocalFullWidthComponents.current -> RoundedCornerShape(0.dp)
        else -> CircleShape
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(30.dp)
            .appButtonShadow(shape, 2.dp),
        shape = shape,
        tonalElevation = 0.dp,
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * 编辑旧消息时的提示条。
 *
 * 单独放在 [TextInputRow] 外面：单行布局的两组按钮是相对整个 [TextInputRow] 居中的，
 * 提示条一旦排在 TextField 之前就会被算进高度，输入内容被删空、按钮切回居中时就会偏到输入框上方。
 */
@Composable
internal fun EditingBanner(
    state: ChatInputState,
    shape: Shape,
    onEditReasoning: () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.editing))
            Spacer(Modifier.weight(1f))
            if (state.editingReasoning != null) {
                TextButton(onClick = onEditReasoning) {
                    Text(stringResource(R.string.chat_input_edit_reasoning))
                }
            }
            Icon(
                imageVector = HugeIcons.Cancel01,
                contentDescription = stringResource(R.string.cancel_edit),
                modifier = Modifier.clickable { state.clearInput() }
            )
        }
    }
}

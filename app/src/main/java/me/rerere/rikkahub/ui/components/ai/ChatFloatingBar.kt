package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.FolderCode
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.ImageToVideo
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.data.model.FloatingBarItem
import me.rerere.rikkahub.ui.modifier.rememberMotionBlurSteps
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.LocalUiSlots
import me.rerere.rikkahub.ui.theme.adaptiveShape
import me.rerere.ui.components.Tooltip
import kotlin.math.roundToInt

/** 悬浮栏大小的换算。 */
internal object FloatingBarSizing {
    const val MIN_SCALE = 0.8f
    const val MAX_SCALE = 1.4f

    private const val BASE_BUTTON_DP = 44
    private const val BASE_ICON_DP = 20

    /** 比例与设置页滑块的上下限对齐；读盘拿到坏值时回到默认大小。 */
    fun clampScale(value: Float): Float =
        if (value.isNaN()) 1f else value.coerceIn(MIN_SCALE, MAX_SCALE)

    fun buttonExtent(scale: Float): Dp = (BASE_BUTTON_DP * clampScale(scale)).dp

    fun iconExtent(scale: Float): Dp = (BASE_ICON_DP * clampScale(scale)).dp

    fun percentLabel(scale: Float): String = "${(clampScale(scale) * 100).roundToInt()}%"
}

/** 悬浮栏上每个槽位的图标。 */
internal val FloatingBarItem.icon: ImageVector
    get() = when (this) {
        FloatingBarItem.CHAT -> HugeIcons.BubbleChat
        FloatingBarItem.MEDIA_CREATION -> HugeIcons.ImageToVideo
        FloatingBarItem.TRANSLATOR -> HugeIcons.LanguageCircle
        FloatingBarItem.ASSISTANT -> HugeIcons.LookTop
        FloatingBarItem.FAVORITE -> HugeIcons.InLove
        FloatingBarItem.HISTORY -> HugeIcons.TransactionHistory
        FloatingBarItem.STATS -> HugeIcons.ChartColumn
        FloatingBarItem.IMAGE_GEN -> HugeIcons.Image02
        FloatingBarItem.WORKSPACES -> HugeIcons.FolderCode
        FloatingBarItem.TERMINAL -> HugeIcons.ComputerTerminal01
        FloatingBarItem.SEARCH -> HugeIcons.Search01
        FloatingBarItem.SETTINGS -> HugeIcons.Settings03
    }

/** 悬浮栏上每个槽位的文案。 */
@Composable
internal fun FloatingBarItem.label(): String = stringResource(
    when (this) {
        FloatingBarItem.CHAT -> R.string.floating_bar_item_chat
        FloatingBarItem.MEDIA_CREATION -> R.string.floating_bar_item_media_creation
        FloatingBarItem.TRANSLATOR -> R.string.floating_bar_item_translator
        FloatingBarItem.ASSISTANT -> R.string.floating_bar_item_assistant
        FloatingBarItem.FAVORITE -> R.string.floating_bar_item_favorite
        FloatingBarItem.HISTORY -> R.string.floating_bar_item_history
        FloatingBarItem.STATS -> R.string.floating_bar_item_stats
        FloatingBarItem.IMAGE_GEN -> R.string.floating_bar_item_image_gen
        FloatingBarItem.WORKSPACES -> R.string.floating_bar_item_workspaces
        FloatingBarItem.TERMINAL -> R.string.floating_bar_item_terminal
        FloatingBarItem.SEARCH -> R.string.floating_bar_item_search
        FloatingBarItem.SETTINGS -> R.string.floating_bar_item_settings
    }
)

/**
 * 输入框下方的悬浮栏。
 *
 * 拉起和隐藏都走同一条过渡：高度从 0 展开或收回，内容同时做竖向位移与模糊淡入淡出。
 * 背景和输入框共用 [chatInputHaze]，模糊/玻璃档位与透明度都跟随输入框的全局特效设置。
 */
@Composable
internal fun ChatFloatingBar(
    visible: Boolean,
    items: List<FloatingBarItem>,
    onItemClick: (FloatingBarItem) -> Unit,
    hazeState: HazeState,
    displaySetting: DisplaySetting,
    modifier: Modifier = Modifier,
) {
    val fullWidth = LocalFullWidthComponents.current
    val containerColor = LocalUiSlots.current.inputBar
    val shape = adaptiveShape(displaySetting.uiCornerRadius.dp)
    val effect = displaySetting.effectiveBackgroundEffect
    val density = LocalDensity.current
    val slideDistance = with(density) { 18.dp.toPx() }
    val transition = updateTransition(targetState = visible, label = "chatFloatingBar")
    val progress by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) 320 else 240,
                easing = FastOutSlowInEasing,
            )
        },
        label = "chatFloatingBarProgress",
    ) { shown -> if (shown) 1f else 0f }
    val blur = rememberMotionBlurSteps(maxRadius = 6.dp)
    val itemExtent = FloatingBarSizing.buttonExtent(displaySetting.floatingBarScale)
    val iconExtent = FloatingBarSizing.iconExtent(displaySetting.floatingBarScale)

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top,
        ),
        exit = shrinkVertically(
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = progress
                    translationY = slideDistance * (1f - progress)
                    renderEffect = blur.at(1f - progress)
                }
                .padding(top = 8.dp)
                .padding(horizontal = if (fullWidth) 0.dp else 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                    .clip(shape)
                    .chatInputHaze(
                        hazeState = hazeState,
                        displaySetting = displaySetting,
                        containerColor = containerColor,
                        shape = shape,
                    ),
                shape = shape,
                color = if (effect == BackgroundEffectType.OFF) {
                    containerColor
                } else {
                    Color.Transparent
                },
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (fullWidth) {
                        Arrangement.SpaceEvenly
                    } else {
                        Arrangement.spacedBy(2.dp)
                    },
                ) {
                    items.forEach { item ->
                        FloatingBarItemButton(
                            item = item,
                            onClick = { onItemClick(item) },
                            fullWidth = fullWidth,
                            itemExtent = itemExtent,
                            iconExtent = iconExtent,
                            modifier = if (fullWidth) Modifier.weight(1f) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingBarItemButton(
    item: FloatingBarItem,
    onClick: () -> Unit,
    fullWidth: Boolean,
    itemExtent: Dp,
    iconExtent: Dp,
    modifier: Modifier = Modifier,
) {
    val label = item.label()
    Tooltip(
        modifier = modifier.then(if (fullWidth) Modifier else Modifier.width(itemExtent)),
        tooltip = { Text(label) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemExtent)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = label,
                modifier = Modifier.size(iconExtent),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

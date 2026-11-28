package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.model.FloatingBarItem
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
 * 输入框下方的悬浮工具栏。
 *
 * 用 M3 的 [HorizontalFloatingToolbar]：容器宽度跟着内容走、四周留出
 * [FloatingToolbarDefaults.ScreenOffset] 的外距、圆角来自
 * [FloatingToolbarDefaults.ContainerShape]（全圆角胶囊），elevation 也由它给。
 *
 * 这里不再自己画胶囊：原来的实现是通栏贴底 + 手画投影 + 玻璃底板，
 * 换成 M3 之后材质统一由 [FloatingToolbarDefaults.standardFloatingToolbarColors] 决定，
 * 不再接 advancedMaterial / haze。
 *
 * 关键一点：它是**浮层**，不占流内高度。调用方把它放在底栏的 Box 里叠加，
 * 显隐不会改动输入框的位置——原来它参与布局，一露出来就把卡片往上顶。
 */
@Composable
internal fun ChatFloatingBar(
    visible: Boolean,
    items: List<FloatingBarItem>,
    onItemClick: (FloatingBarItem) -> Unit,
    displaySetting: DisplaySetting,
    modifier: Modifier = Modifier,
) {
    val iconExtent = FloatingBarSizing.iconExtent(displaySetting.floatingBarScale)
    val itemExtent = FloatingBarSizing.buttonExtent(displaySetting.floatingBarScale)

    // 显隐走 M3 自己的展开/收起曲线，不再自己算位移和模糊：
    // 容器高度、透明度、圆角都由 FloatingToolbar 内部处理。
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            // 容器高度跟着设置里的缩放走，但只给下限：M3 的容器自带 8dp 内边距，
            // 硬写 height 会把图标压扁。关掉 IconButton 的 48dp 最小触控，
            // 否则它会撑破容器（图标本身 36dp 左右，触控区由容器高度兜住）。
            modifier = Modifier.heightIn(min = itemExtent),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                items.forEach { item ->
                    val label = item.label()
                    Tooltip(tooltip = { Text(label) }) {
                        IconButton(
                            onClick = { onItemClick(item) },
                            modifier = Modifier.size(itemExtent),
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = label,
                                modifier = Modifier.size(iconExtent),
                            )
                        }
                    }
                }
            }
        }
    }
}

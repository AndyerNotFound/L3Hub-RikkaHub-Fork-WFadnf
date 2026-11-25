package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.FloatingBarItem
import me.rerere.rikkahub.ui.components.ui.AppearanceMaterial
import me.rerere.rikkahub.ui.modifier.rememberMotionBlurSteps
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.rememberFixedSpec
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
 * 输入框下方聊天坞的材质。
 *
 * 显式开启后，悬浮工具栏沿用聊天背景源，单独应用聊天坞自己的材质：
 * 材质档读 `chatDockSurfaceStyle`（默认液态玻璃），着色读 `chatDockGlassOpacity`，
 * 模糊半径读 `chatDockGlassBlurRadius`，性能开关读 `enableChatDockPerformanceEffects`。
 *
 * 返回 null 时容器退回一道实色底：高级外观关着，或者聊天坞总闸
 * （`enableChatDockGlass`）关着，两种情况都不该借顶栏的材质顶上。
 */
internal fun Settings.chatDockMaterial(): AppearanceMaterial? {
    val appearance = advancedAppearanceSetting
    if (!appearance.enableAdvancedAppearance || !appearance.enableChatDockGlass) {
        return null
    }
    return AppearanceMaterial(
        style = appearance.chatDockSurfaceStyle,
        opacity = appearance.chatDockGlassOpacity,
        radius = appearance.chatDockGlassBlurRadius,
        effects = appearance.enableChatDockPerformanceEffects,
    )
}

/**
 * 输入框下方的悬浮栏。
 *
 * 容器直接用顶栏那颗胶囊（[ChatCapsule]）：同底色、同投影、同模糊与描边，
 * 按钮按胶囊的切面平均分段，段与段之间用 [ChatCapsuleDivider] 隔开。
 *
 * 拉起和隐藏都走同一条过渡：高度从 0 展开或收回，内容同时做竖向位移与模糊淡入淡出。
 * 材质与透明度跟随聊天坞自己的高级外观设置。
 *
 * 高级外观开启并且打开了聊天坞（`enableChatDockGlass`）时，
 * 容器按 `chatDockSurfaceStyle` 取材质，着色读 `chatDockGlassOpacity`，
 * 性能开关读 `enableChatDockPerformanceEffects`；Haze 源仍然是页面那一个，不在这里新开。
 * [material] 留空则整条栏保持一道实色底。
 */
@Composable
internal fun ChatFloatingBar(
    visible: Boolean,
    items: List<FloatingBarItem>,
    onItemClick: (FloatingBarItem) -> Unit,
    hazeState: HazeState?,
    displaySetting: DisplaySetting,
    modifier: Modifier = Modifier,
    material: AppearanceMaterial? = null,
) {
    val fullWidth = LocalFullWidthComponents.current
    val density = LocalDensity.current
    val slideDistance = with(density) { 18.dp.toPx() }
    // 显隐共用一组参数：收起不再比展开快一截，高度、位移、透明度走同一条时间线
    val barTransitionMillis = 320
    val barEasing = FastOutSlowInEasing
    // 节奏保持不变，只接通「关闭动效」与「动画速度」两档开关
    val barSpec = rememberFixedSpec<Float>(barTransitionMillis, barEasing)
    val barSizeSpec = rememberFixedSpec<IntSize>(barTransitionMillis, barEasing)
    val transition = updateTransition(targetState = visible, label = "chatFloatingBar")
    val progress by transition.animateFloat(
        transitionSpec = { barSpec },
        label = "chatFloatingBarProgress",
    ) { shown -> if (shown) 1f else 0f }
    val blur = rememberMotionBlurSteps(maxRadius = 6.dp)
    val itemExtent = FloatingBarSizing.buttonExtent(displaySetting.floatingBarScale)
    val iconExtent = FloatingBarSizing.iconExtent(displaySetting.floatingBarScale)

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(animationSpec = barSizeSpec, expandFrom = Alignment.Top),
        exit = shrinkVertically(animationSpec = barSizeSpec, shrinkTowards = Alignment.Top),
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
            ChatCapsule(
                hazeState = hazeState,
                material = material,
                modifier = Modifier
                    .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                    .height(itemExtent),
            ) {
                items.forEachIndexed { index, item ->
                    // 分隔线只切中间一段，胶囊仍是一整块；高度跟着缩放走，比例和顶栏一致
                    if (index > 0) {
                        ChatCapsuleDivider(height = itemExtent * 0.4f)
                    }
                    FloatingBarItemButton(
                        item = item,
                        onClick = { onItemClick(item) },
                        shape = segmentShape(
                            index = index,
                            lastIndex = items.lastIndex,
                            radius = itemExtent / 2,
                            fullWidth = fullWidth,
                        ),
                        iconExtent = iconExtent,
                        modifier = Modifier
                            .then(if (fullWidth) Modifier.weight(1f) else Modifier.width(itemExtent))
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * 一段按钮的背景形状：就是胶囊在这一段上的切面。
 *
 * 两头的段跟着胶囊收圆，中间的段是直角。按下/长按的反馈照这个形状裁，
 * 每一段的底色都是切开的区域，而不是各自画一颗圆。
 */
private fun segmentShape(
    index: Int,
    lastIndex: Int,
    radius: Dp,
    fullWidth: Boolean,
): Shape = when {
    fullWidth -> RectangleShape
    lastIndex <= 0 -> RoundedCornerShape(radius)
    index == 0 -> RoundedCornerShape(topStart = radius, bottomStart = radius)
    index == lastIndex -> RoundedCornerShape(topEnd = radius, bottomEnd = radius)
    else -> RectangleShape
}

@Composable
private fun FloatingBarItemButton(
    item: FloatingBarItem,
    onClick: () -> Unit,
    shape: Shape,
    iconExtent: Dp,
    modifier: Modifier = Modifier,
) {
    val label = item.label()
    Tooltip(modifier = modifier, tooltip = { Text(label) }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
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

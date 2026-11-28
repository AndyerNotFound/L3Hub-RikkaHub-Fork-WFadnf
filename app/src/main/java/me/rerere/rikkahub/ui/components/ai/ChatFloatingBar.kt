package me.rerere.rikkahub.ui.components.ai

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.model.FloatingBarItem
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec
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

/** 菜单一行的列数。7 个槽位排成两行。 */
private const val MENU_COLUMNS = 4

/** 按钮与面板共用的圆角：容器变换时形状不跳。 */
private val MenuShape = RoundedCornerShape(16.dp)

/** 两个状态共用的共享元素 key。 */
private const val MENU_KEY = "l3_floating_menu"

/**
 * 输入卡右上角的悬浮菜单。
 *
 * 原来是一条横着铺在输入卡上方的工具栏：在 Column 里占一整行流式高度，再用 offset
 * 往上挪去叠卡片，结果既挡输入框又把底栏撑高。
 *
 * 现在只剩一颗贴角的小按钮，点开时菜单从按钮那一小块长成整张面板。两者都贴右下角
 * 对齐，右边界与底边钉住，视觉上就是往左上角推开。
 *
 * 容器变换走 [SharedTransitionScope.sharedBounds]。两个状态各是一个
 * [AnimatedVisibility]，共用同一个 key —— 这是官方 nested shared bounds 的写法：
 * 同时只有一个在场，bounds 从退出者的位置插值到进入者的位置。
 *
 * 位置必须是流式布局、不能用 offset 或负偏移的 layout 挪出去：
 * Compose 的命中测试用 `isPointerInBounds`（见 `NodeCoordinator.kt`），
 * 画到父节点边界外的内容收不到触摸。所以底栏跟着菜单一起往上长——
 * 底栏是 bottomBar，底边固定，长高只会往聊天区里长，输入卡的位置不动。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ChatFloatingBar(
    items: List<FloatingBarItem>,
    onItemClick: (FloatingBarItem) -> Unit,
    displaySetting: DisplaySetting,
    modifier: Modifier = Modifier,
    autoHide: Boolean = true,
) {
    if (items.isEmpty()) return

    val iconExtent = FloatingBarSizing.iconExtent(displaySetting.floatingBarScale)
    val itemExtent = FloatingBarSizing.buttonExtent(displaySetting.floatingBarScale)

    var expanded by remember { mutableStateOf(false) }
    // 静默计时：每次点按算一次活动，一段时间没再动就自动收起
    var activity by remember { mutableIntStateOf(0) }

    // 只在菜单开着时计时，收起态不占一个协程
    LaunchedEffect(expanded, autoHide, activity) {
        if (!expanded || !autoHide) return@LaunchedEffect
        delay(5_000)
        expanded = false
    }
    // 菜单是浮层，返回键先收菜单，而不是退出会话
    BackHandler(enabled = expanded) { expanded = false }

    val fadeSpec = rememberEffectsSpec<Float>()
    val boundsSpec = rememberSpatialSpec<Rect>()

    SharedTransitionLayout(modifier = modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.BottomEnd) {
            AnimatedVisibility(
                visible = !expanded,
                // 进出场一律交给 sharedBounds 演：这里再淡一次就是双份
                enter = EnterTransition.None,
                exit = ExitTransition.None,
            ) {
                FloatingMenuButton(
                    iconExtent = iconExtent,
                    itemExtent = itemExtent,
                    onClick = {
                        activity++
                        expanded = true
                    },
                    modifier = Modifier.sharedBounds(
                        rememberSharedContentState(key = MENU_KEY),
                        this@AnimatedVisibility,
                        enter = fadeIn(fadeSpec),
                        exit = fadeOut(fadeSpec),
                        boundsTransform = { _, _ -> boundsSpec },
                        // 让外层跟着动画尺寸重新量，面板是往上长的，
                        // 不跟着长就会被底栏的边界裁掉
                        placeholderSize = SharedTransitionScope.PlaceholderSize.AnimatedSize,
                    ),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
            ) {
                FloatingMenuPanel(
                    items = items,
                    iconExtent = iconExtent,
                    itemExtent = itemExtent,
                    onItemClick = { item ->
                        activity++
                        expanded = false
                        onItemClick(item)
                    },
                    modifier = Modifier.sharedBounds(
                        rememberSharedContentState(key = MENU_KEY),
                        this@AnimatedVisibility,
                        enter = fadeIn(fadeSpec),
                        exit = fadeOut(fadeSpec),
                        boundsTransform = { _, _ -> boundsSpec },
                        // 面板的圆角、描边、底色都画在容器自身上，
                        // 只有真的按动画尺寸重新量一遍，这些才不会跟着被拉伸
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                        placeholderSize = SharedTransitionScope.PlaceholderSize.AnimatedSize,
                    ),
                )
            }
        }
    }
}

/** 收起态：贴角的一颗按钮。 */
@Composable
private fun FloatingMenuButton(
    iconExtent: Dp,
    itemExtent: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(itemExtent)
            .clickable(onClick = onClick),
        shape = MenuShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = HugeIcons.Menu03,
                contentDescription = stringResource(R.string.floating_bar_open_menu),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconExtent),
            )
        }
    }
}

/** 展开态：按钮长成的那张面板。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloatingMenuPanel(
    items: List<FloatingBarItem>,
    iconExtent: Dp,
    itemExtent: Dp,
    onItemClick: (FloatingBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 240.dp),
        shape = MenuShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            FlowRow(
                modifier = Modifier.padding(6.dp),
                maxItemsInEachRow = MENU_COLUMNS,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(iconExtent),
                            )
                        }
                    }
                }
            }
        }
    }
}

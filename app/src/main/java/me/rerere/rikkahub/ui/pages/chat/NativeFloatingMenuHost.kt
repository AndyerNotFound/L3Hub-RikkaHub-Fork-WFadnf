package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.rikkahub.data.model.FloatingBarItem
import me.rerere.rikkahub.ui.components.ai.FloatingBarSizing
import me.rerere.rikkahub.ui.components.ai.icon
import me.rerere.rikkahub.ui.components.ai.label
import me.rerere.rikkahub.ui.theme.LocalAnimationsEnabled
import me.rerere.rikkahub.ui.theme.slideDurationMillis
import me.rerere.rikkahub.ui.views.chat.NativeFloatingMenu
import me.rerere.rikkahub.ui.views.chat.rememberNativeChatContext

/**
 * 快捷菜单的 Compose 桥接层。
 *
 * 容器与容器变换归 Java（[NativeFloatingMenu]），图标归 Compose —— HugeIcons 是
 * `ImageVector` 的 Kotlin 代码，没有 vector drawable，Java 侧取不到。
 * 所以每个槽位里塞一个 [ComposeView]，只画那一颗图标。
 *
 * 这不影响容器变换：`MaterialContainerTransform` 是对整块 View 截图后缩放，
 * 里面嵌的是 ComposeView 还是普通 View 都一样。
 *
 * @param visible 键盘弹起时传 false：收起并把上方那一条留给内容
 * @param expanded 由页面持有 —— 菜单展开时宿主需要知道（比如收起输入卡的其他浮层）
 * @param onExpandedChange 展开 / 收起时回调
 */
@Composable
internal fun NativeFloatingMenuHost(
    items: List<FloatingBarItem>,
    scale: Float,
    visible: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onItemClick: (FloatingBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val context = rememberNativeChatContext()
    val animationsEnabled = LocalAnimationsEnabled.current
    val iconExtent = FloatingBarSizing.iconExtent(scale)
    // 档位在组合层读，Java 侧读不到 motionScheme。容器变换与淡入淡出共用这一档。
    val durationMillis = slideDurationMillis()

    // 回调每帧都是新闭包，但装在 View 上的 listener 只建一次 —— 通过这个读最新那份
    val latest = rememberUpdatedState(
        FloatingMenuHandler(
            items = items,
            onExpandedChange = onExpandedChange,
            onItemClick = onItemClick,
        )
    )

    val iconBinder = remember(context, items, iconExtent) {
        NativeFloatingMenu.IconBinder { container, index ->
            val item = if (index < 0) null else items.getOrNull(index)
            container.addView(
                ComposeView(container.context).apply {
                    setContent {
                        Icon(
                            imageVector = item?.icon ?: HugeIcons.Menu03,
                            contentDescription = item?.label(),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp),
                        )
                    }
                }
            )
        }
    }

    val listener = remember {
        object : NativeFloatingMenu.Listener {
            override fun onSlotClick(index: Int) {
                latest.value.onSlotClick(index)
            }

            override fun onExpandChange(expanded: Boolean) {
                latest.value.onExpandedChange(expanded)
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { NativeFloatingMenu(context) },
        onRelease = { menu -> menu.setListener(null) },
        update = { menu ->
            menu.setAnimationsEnabled(animationsEnabled)
            menu.setTransformDuration(durationMillis)
            menu.setListener(listener)
            menu.setIconBinder(iconBinder)
            menu.setSlotCount(items.size)
            // 页面可能从别处把它收起来（比如键盘弹起），这里对齐一次
            if (menu.isExpanded != expanded) menu.setExpanded(expanded, true)
            menu.setVisible(visible)
        },
    )
}

/**
 * 菜单回调的持有者。
 *
 * 只读最新那份，避免 Java 侧的 listener 拿到两次重组之前捕获的 items。
 * 槽位序号在这里翻译成枚举 —— Java 不认识 Kotlin 的 enum，也不需要认识。
 */
private class FloatingMenuHandler(
    private val items: List<FloatingBarItem>,
    val onExpandedChange: (Boolean) -> Unit,
    val onItemClick: (FloatingBarItem) -> Unit,
) {
    fun onSlotClick(index: Int) {
        items.getOrNull(index)?.let(onItemClick)
    }
}

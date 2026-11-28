package me.rerere.rikkahub.ui.pages.chat

import android.graphics.Rect
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import me.rerere.rikkahub.data.model.FloatingBarItem
import me.rerere.rikkahub.ui.components.ai.icon
import me.rerere.rikkahub.ui.components.ai.label
import me.rerere.rikkahub.ui.theme.LocalAnimationsEnabled
import me.rerere.rikkahub.ui.theme.slideDurationMillis
import me.rerere.rikkahub.ui.views.chat.NativeFloatingMenu
import me.rerere.rikkahub.ui.views.chat.rememberNativeChatContext

/**
 * 快捷入口：一颗 M3 FAB，点开是通用选择菜单。
 *
 * ## 为什么不自己画面板
 *
 * 早先这里有一份横向槽位面板 + 自绘容器变换，与 [rememberNativeSelectMenu] 那套
 * 是两份实现。结果是同一屏里「点会话」和「点快捷入口」弹出两种不同的菜单 ——
 * 圆角、间距、项高都对不上，而且快捷入口只有图标没有文字。
 *
 * 现在按钮归 Java（[NativeFloatingMenu]，只有那颗 FAB），面板归通用菜单，
 * 容器变换也归它：从按钮的窗口矩形长成菜单卡，与文件夹、排序、会话操作同一套观感。
 *
 * ## 状态流向
 *
 * 菜单的显隐由这里持有（`expanded`），Java 侧只负责「按钮被点了」这一个通知。
 * 不反向下发，免得两边各推一次打架。
 *
 * @param visible 键盘弹起时传 false：收起按钮，把上方那一条留给内容
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
    val context = rememberNativeChatContext()
    val animationsEnabled = LocalAnimationsEnabled.current
    val duration = slideDurationMillis()

    // 菜单的锚点：FAB 的窗口矩形。按钮被点时才填。
    // 窗口 token 由 rememberNativeSelectMenu 内部处理（它拿 LocalView），
    // 这里只管起点矩形。
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }

    // 文案与图标在组合里读好：菜单的 content lambda 在 LaunchedEffect 里跑，
    // 那里没有 Composition，直接调 stringResource 会崩
    val labels = items.map { it.label() }
    val icons: List<ImageVector> = items.map { it.icon }

    val latestOnItemClick = rememberUpdatedState(onItemClick)
    val latestOnExpandedChange = rememberUpdatedState(onExpandedChange)

    // 菜单本体：通用选择菜单，容器变换从 FAB 长出来
    rememberNativeSelectMenu(
        expanded = expanded,
        onDismiss = { latestOnExpandedChange.value(false) },
        anchorBounds = anchorBounds,
        icons = icons,
    ) {
        items.forEachIndexed { index, item ->
            addItem(labels[index], null, selected = false) {
                latestOnItemClick.value(item)
            }
        }
    }

    // 一个入口都没配时连按钮都不画。
    //
    // 但先把菜单收掉：用户可能是在菜单开着的时候去设置里把入口全关了，
    // 那时 expanded 还是 true，而 NativeSelectMenu.show() 对空列表直接 return，
    // 已经弹出来的那份会留在屏幕上关不掉。
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { latestOnExpandedChange.value(false) }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { NativeFloatingMenu(context) },
        onRelease = { menu -> menu.setListener(null) },
        update = { menu ->
            menu.setAnimationsEnabled(animationsEnabled)
            menu.setFadeDuration(duration)
            menu.setScale(scale)
            menu.setVisible(visible)
            menu.setListener(object : NativeFloatingMenu.Listener {
                override fun onOpenRequested(anchor: View) {
                    anchorBounds = anchor.windowRect()
                    latestOnExpandedChange.value(true)
                }
            })
        },
    )
}

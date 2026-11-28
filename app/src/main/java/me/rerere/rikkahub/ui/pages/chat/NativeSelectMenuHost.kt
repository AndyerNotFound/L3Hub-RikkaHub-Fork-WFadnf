package me.rerere.rikkahub.ui.pages.chat

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import me.rerere.rikkahub.ui.theme.LocalAnimationsEnabled
import me.rerere.rikkahub.ui.theme.slideDurationMillis
import me.rerere.rikkahub.ui.views.chat.NativeSelectMenu
import me.rerere.rikkahub.ui.views.chat.rememberNativeChatContext

/**
 * 公用选择菜单的 Compose 入口。
 *
 * 菜单本体是 Java（[NativeSelectMenu]），展开 / 收起走 `MaterialContainerTransform`：
 * 从触发按钮那一小块原地长成整张卡，不是淡入淡出，也不是缩放。
 *
 * ## 为什么不用 M3 的 DropdownMenu
 *
 * 它内部的进出场是 `DropdownMenuContent` 里写死的 scale + alpha
 * （`ExpandedScaleTarget` / `ClosedScaleTarget`），公开签名里没有 enter/exit 参数，
 * 也拿不到它内部那两个 View —— 换不了。
 *
 * ## 用法
 *
 * ```kotlin
 * var expanded by remember { mutableStateOf(false) }
 * val anchor = LocalView.current
 * rememberNativeSelectMenu(
 *     expanded = expanded,
 *     onDismiss = { expanded = false },
 *     anchorView = anchor,
 * ) {
 *     addHeader("会话文件夹")
 *     addItem("未归类", null, selected = folderId == null) { onSelect(null) }
 *     addDivider()
 *     addItem("新建文件夹", R.drawable.l3_native_ic_folder) { onCreateFolder() }
 * }
 * ```
 *
 * @param expanded 显隐。true 展开，false 收起
 * @param onDismiss 菜单自己关掉时回调（点外面、按返回键、点中某项之后）
 * @param anchorView 容器变换的起点 View —— 从它的屏幕矩形长出来。不传就用当前 Compose 视图
 * @param content 往菜单里加项，只在展开时求值一次
 */
@Composable
internal fun rememberNativeSelectMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    anchorView: View? = null,
    content: NativeSelectMenu.() -> Unit,
): NativeSelectMenu {
    val context = rememberNativeChatContext()
    val animationsEnabled = LocalAnimationsEnabled.current
    val duration = slideDurationMillis()
    val fallbackAnchor = LocalView.current
    val latestDismiss = rememberUpdatedState(onDismiss)
    val latestContent = rememberUpdatedState(content)

    val menu = remember(context) { NativeSelectMenu(context) }

    // 点中一项：Java 侧会先播收起动画再执行那一项自己的 action，
    // 这里只负责把 expanded 同步回 false，让下一帧不再要求它展开
    DisposableEffect(menu) {
        menu.setOnItemClickListener { latestDismiss.value() }
        onDispose {
            menu.setOnItemClickListener(null)
            menu.dismiss()
        }
    }

    // 展开时重建内容再打开。放在 LaunchedEffect 里而不是 remember 里：
    // remember 的 lambda 在组合阶段执行，而 show() 会动窗口，属于副作用。
    LaunchedEffect(menu, expanded, anchorView, fallbackAnchor, animationsEnabled, duration) {
        menu.setAnimationsEnabled(animationsEnabled)
        menu.setTransformDuration(duration)
        if (expanded) {
            menu.clearItems()
            latestContent.value.invoke(menu)
            menu.show(anchorView ?: fallbackAnchor)
        } else {
            menu.dismiss()
        }
    }

    return menu
}

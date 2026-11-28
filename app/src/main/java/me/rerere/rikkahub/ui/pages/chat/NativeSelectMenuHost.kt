package me.rerere.rikkahub.ui.pages.chat

import android.graphics.Rect
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import me.rerere.rikkahub.ui.theme.LocalAnimationsEnabled
import me.rerere.rikkahub.ui.theme.slideDurationMillis
import me.rerere.rikkahub.ui.views.chat.NativeSelectMenu
import me.rerere.rikkahub.ui.views.chat.rememberNativeChatContext

/**
 * 记住一个组件的窗口矩形，喂给 [rememberNativeSelectMenu] 当容器变换的起点。
 *
 * ```kotlin
 * val anchor = rememberSelectMenuAnchor()
 * Box(Modifier.then(anchor.modifier).clickable { expanded = true }) { ... }
 * ```
 *
 * 为什么不直接用 `LocalView.current`：那是整个 Compose 宿主的 View，矩形是全屏的，
 * 容器变换会从整块屏幕缩到菜单 —— 不是「按钮长成菜单」。
 */
internal class SelectMenuAnchor {
    private val bounds = mutableStateOf<Rect?>(null)

    val modifier: Modifier = Modifier.onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInWindow()
        bounds.value = Rect(
            rect.left.toInt(),
            rect.top.toInt(),
            rect.right.toInt(),
            rect.bottom.toInt(),
        )
    }

    internal val value: State<Rect?> get() = bounds
}

@Composable
internal fun rememberSelectMenuAnchor(): SelectMenuAnchor {
    return remember { SelectMenuAnchor() }
}

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
 * val anchor = rememberSelectMenuAnchor()
 *
 * Box(Modifier.then(anchor.modifier).clickable { expanded = true }) { ... }
 *
 * rememberNativeSelectMenu(
 *     expanded = expanded,
 *     onDismiss = { expanded = false },
 *     anchorBounds = anchor.value.value,
 * ) {
 *     addHeader("会话文件夹")
 *     addItem("未归类", null, selected = folderId == null) { onSelect(null) }
 *     addDivider()
 *     addItem("新建文件夹", R.drawable.l3_native_ic_folder) { onCreateFolder() }
 * }
 * ```
 *
 * ## 内容 lambda 里不能调 Composable
 *
 * 它在 `LaunchedEffect` 里执行，那里没有 Composition。`stringResource()` 之类
 * 必须在外面读好再传进来，否则崩。
 *
 * @param expanded 显隐。true 展开，false 收起
 * @param onDismiss 菜单自己关掉时回调（点外面、按返回键、点中某项之后）
 * @param anchorBounds 容器变换的起点矩形（窗口坐标）。为 null 时退化成菜单原地淡入
 * @param icons 每项的图标，按 [NativeSelectMenu.addItem] 的调用顺序。null 表示该项没有图标。
 *   图标必须是 Compose 的 [ImageVector] —— HugeIcons 的 pathData 在 Java 侧取不到
 * @param content 往菜单里加项
 */
@Composable
internal fun rememberNativeSelectMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    anchorBounds: Rect? = null,
    icons: List<ImageVector?> = emptyList(),
    content: NativeSelectMenu.() -> Unit,
): NativeSelectMenu {
    val context = rememberNativeChatContext()
    val animationsEnabled = LocalAnimationsEnabled.current
    val duration = slideDurationMillis()
    val latestDismiss = rememberUpdatedState(onDismiss)
    val latestContent = rememberUpdatedState(content)

    val menu = remember(context) { NativeSelectMenu(context) }

    // 图标走 ComposeView 嵌进 Java 的菜单行：HugeIcons 的 pathData 在 Java 侧取不到
    //
    // 这里必须把宿主的 ViewTree 所有者搬进 Popup：Popup 有自己的 Window，
    // 不继承 Compose 的 ViewTree。缺了 lifecycle owner，ComposeView.setContent()
    // 会抛 "ViewTreeLifecycleOwner not found from ..."（WindowRecomposer.android.kt）。
    val hostView = LocalView.current
    val hostLifecycleOwner = hostView.findViewTreeLifecycleOwner()
    val hostSavedStateOwner = hostView.findViewTreeSavedStateRegistryOwner()
    val hostViewModelStoreOwner = hostView.findViewTreeViewModelStoreOwner()

    DisposableEffect(menu, icons) {
        menu.setIconBinder(object : NativeSelectMenu.IconBinder {
            override fun hasIcon(index: Int): Boolean = icons.getOrNull(index) != null

            override fun bindIcon(container: ViewGroup, index: Int) {
                val imageVector = icons.getOrNull(index) ?: return
                container.addView(
                    ComposeView(container.context).apply {
                        if (hostLifecycleOwner != null) setViewTreeLifecycleOwner(hostLifecycleOwner)
                        if (hostSavedStateOwner != null) setViewTreeSavedStateRegistryOwner(hostSavedStateOwner)
                        if (hostViewModelStoreOwner != null) setViewTreeViewModelStoreOwner(hostViewModelStoreOwner)
                        setContent {
                            Icon(
                                imageVector = imageVector,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(1.dp),
                            )
                        }
                    }
                )
            }
        })
        onDispose { menu.setIconBinder(null) }
    }

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
    LaunchedEffect(menu, expanded, anchorBounds, animationsEnabled, duration) {
        menu.setAnimationsEnabled(animationsEnabled)
        menu.setTransformDuration(duration)
        if (expanded) {
            menu.clearItems()
            latestContent.value.invoke(menu)
            menu.show(anchorBounds)
        } else {
            menu.dismiss()
        }
    }

    return menu
}

package me.rerere.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.sheet.Sheet
import com.moriafly.salt.ui.sheet.SheetDetent
import com.moriafly.salt.ui.sheet.SheetProperties

/**
 * sheet 内容里要主动关窗时走这里：先播退场，演完再执行动作。
 *
 * 内容里的按钮直接改外面的布尔，会把窗口从组合里拿掉，退场没有机会播。
 * 这一条和 [LocalDialogDismiss] 是同一个约定，只是对象换成了底部抽屉。
 * 没有 sheet 包着时返回 null，调用方退回直接执行。
 */
val LocalSheetDismiss = compositionLocalOf<((() -> Unit) -> Unit)?> { null }

/** 取当前 sheet 的「播完退场再执行」入口；不在 sheet 里时直接执行。 */
@Composable
fun rememberSheetDismiss(): (() -> Unit) -> Unit {
    val dismiss = LocalSheetDismiss.current
    return dismiss ?: { action -> action() }
}

/**
 * 底部抽屉，引擎是 SaltUI 的 [Sheet]；全站的抽屉都从这一个入口进。
 *
 * 与旧的 M3 / EnhancedModalBottomSheet 实现相比，能交给 Salt 的都交出去了：
 * - 底板、圆角、scrim、拖拽把手、标题栏都归 Salt 管，调用方不再传 shape / containerColor /
 *   contentColor / dragHandle / contentWindowInsets；
 * - 高度从「贴底一层」变成 Salt 的档位（[SheetDetent]），默认一档 `Content`：内容多高就多高；
 * - 窗口宽 ≥600dp 且高 ≥480dp 时 Salt 自己把抽屉变成居中浮层，手机上仍是贴底抽屉；
 * - 关窗入口拆开：[dismissible] 管点外部 / 返回键 / 下拉，[closeButton] 管标题栏右上角的
 *   关闭按钮，[dragEnabled] 管把手与拖动调高。
 *
 * [visible] 仍然是唯一开关：调用点要保持自己在组合里，退场才演得完（Salt 在 visible 翻成
 * false 之后仍留在组合里，把退场播完再拆窗口）。[onDismissRequest] 只上报用户的关闭意图，
 * 不改 [visible]。
 */
@OptIn(UnstableSaltUiApi::class)
@Composable
fun SaltModalSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    dismissible: Boolean = true,
    closeButton: Boolean = dismissible,
    dragEnabled: Boolean = true,
    detents: List<SheetDetent> = listOf(SheetDetent.Content),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    // 动作即退场：内容里的按钮把 visible 翻回 false，Salt 接着把退场演完
    val dismissThen: (() -> Unit) -> Unit = remember { { action -> action() } }

    CompositionLocalProvider(LocalSheetDismiss provides dismissThen) {
        Sheet(
            visible = visible,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            title = title,
            properties = SheetProperties(
                dismissOnClickOutside = dismissible,
                dismissOnBackPress = dismissible,
                dismissOnCloseButtonClick = closeButton,
                dragEnabled = dragEnabled,
                // 下拉关窗跟着把手走：没有把手就不留「拖一下就关」这条路
                dismissOnDrag = dragEnabled && dismissible,
                detents = detents,
            ),
        ) {
            content()
        }
    }
}

package me.rerere.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
 * sheet 内容里要主动关窗时走这里。
 *
 * 这里只把动作执行掉，退场由 Salt 自己演：翻成 `visible = false` 之后窗口仍留在组合里，
 * 播完退场才拆。反过来，把 Sheet 从组合里拿掉等于退场没有机会播。
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
 * - 关窗入口拆开：[dismissible] 管点外部，[dismissOnBackPress] 管返回键与返回手势，
 *   [closeButton] 管标题栏右上角的关闭按钮，[dragEnabled] 管把手与拖动调高。
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
    // 返回键 / 返回手势单独一段：它和「点外部」「下拉」不是同一种误触。把返回也一起关掉，
    // 用户按下去会觉得整个抽屉没反应，所以默认跟随 [dismissible]，需要防误关的编辑表单
    // 只关点外部，仍然留一条返回可关的路
    dismissOnBackPress: Boolean = dismissible,
    closeButton: Boolean = dismissible,
    dragEnabled: Boolean = true,
    detents: List<SheetDetent> = listOf(SheetDetent.Content),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    // 动作即退场：内容里的按钮把 visible 翻回 false，Salt 接着把退场演完
    val dismissThen: (() -> Unit) -> Unit = remember { { action -> action() } }

    CompositionLocalProvider(LocalSheetDismiss provides dismissThen) { SheetWindowTheme {
        Sheet(
            visible = visible,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            title = title,
            actions = actions,
            properties = SheetProperties(
                // 退场结束那一刻 Salt 会重建内容、清空几何并重新量一次。量出来的锚点一换，
                // 进行中的偏移会按旧进度被映射回去，退场看着是「收完又弹回原位」；而此时
                // 状态已经判成隐藏，拖动、返回、点外部全部短路，抽屉就卡在那里。
                // 保留内容与几何，退场走完不再重来
                retainStateWhenHidden = true,
                dismissOnClickOutside = dismissible,
                dismissOnBackPress = dismissOnBackPress,
                dismissOnCloseButtonClick = closeButton,
                dragEnabled = dragEnabled,
                // 下拉关窗跟着把手走：没有把手就不留「拖一下就关」这条路
                dismissOnDrag = dragEnabled && dismissible,
                detents = detents,
            ),
        ) {
            // Salt 和 M3 的 LocalContentColor 独立，M3 正文不能继承打开 Sheet 的组件颜色。
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                content()
            }
        }
    } }
}

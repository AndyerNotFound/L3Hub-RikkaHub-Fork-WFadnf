package me.rerere.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
 * M3 [ModalBottomSheet] 的「进出场对称」版本：把可见性交给这里，关闭动作怎么写都有退场。
 *
 * 原生弹窗只在自己收场时才会播退场——点遮罩、按返回键，库先 `hide()` 再回调；
 * 调用方拿着布尔直接 `if (show) { ModalBottomSheet(...) }` 的时候，一旦把布尔翻回 false，
 * 窗口跟着组合一起销毁，退场根本没有机会播，观感就是「打开有动画、关闭一帧消失」。
 *
 * 这里把窗口的存活和可见性拆开：`visible` 变 false 时先 `hide()`，播完才把窗口从组合里拿掉。
 * 调用方不必再自己 `rememberBottomSheetState` / `hide()`，关窗怎么写都行——点按钮、选中一项、
 * 点外部，全部走同一条退场。
 *
 * 档位（进入 / 退出曲线）由主题的 motionScheme 决定，动画速度设置一改就跟着变。
 */
@Composable
fun AnimatedModalBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    // 窗口比可见性多活一段：关闭时先把 sheet 收回去，收完再拆窗口。
    // 打开晚一帧挂上，看不出差别，但避免了在组合期写状态
    var mounted by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) mounted = true
    }
    if (!mounted) return

    // 每次挂上都用一份新的状态：库自己的 show() 挂在 sheetState 上，复用旧实例时不会再跑一遍，
    // 第二次打开会停在屏幕外
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    LaunchedEffect(visible) {
        if (!visible) {
            // hide() 被取消（同一帧又被打开）时 mounted 也得留在 true，交给 finally 收尾
            try {
                sheetState.hide()
            } finally {
                mounted = false
            }
        }
    }

    val scope = rememberCoroutineScope()
    // 内容里的按钮走这条：先把 sheet 收回去，再执行动作（通常是关状态 + 跳页面）
    val dismissThen: (() -> Unit) -> Unit = { action ->
        scope.launch {
            sheetState.hide()
            action()
        }
    }

    CompositionLocalProvider(LocalSheetDismiss provides dismissThen) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            sheetGesturesEnabled = sheetGesturesEnabled,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            scrimColor = scrimColor,
            dragHandle = dragHandle,
            contentWindowInsets = contentWindowInsets,
            content = content,
        )
    }
}

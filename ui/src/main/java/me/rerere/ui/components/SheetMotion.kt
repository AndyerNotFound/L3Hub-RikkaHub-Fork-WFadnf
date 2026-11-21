package me.rerere.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedModalBottomSheet

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
 * M3 [androidx.compose.material3.ModalBottomSheet] 的「进出场对称」版本，引擎换成
 * [EnhancedModalBottomSheet]。参数与语义对调用方不变。
 *
 * 退场为什么还是完整的：原生弹窗只在自己收场时才会播退场——点遮罩、按返回键，库先 `hide()`
 * 再回调；调用方拿着布尔直接 `if (show) { ModalBottomSheet(...) }` 的时候，一旦把布尔翻回 false，
 * 窗口跟着组合一起销毁，退场根本没有机会播，观感就是「打开有动画、关闭一帧消失」。
 *
 * 以前的实现自己记了 `mounted`、自己调 `hide()` 来兜这一下。现在交给引擎：
 * [EnhancedModalBottomSheet] 把「窗口存活」和「visible」拆开——visible 变 false 时先收回去，
 * 收完才把窗口从组合里拿掉；手势下拉、点遮罩、返回键也都走同一条退场。
 *
 * 所以 [LocalSheetDismiss] 现在只需把动作交出去：动作自己会把 visible 翻回 false，引擎接着演完退场。
 * 换句话说是「动作触发退场，窗口等退场演完再拆」，用户看到的依然是抽屉滑出去而不是当场消失。
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
    // 动作即退场：内容里的按钮走这条（往常见的是「关状态 + 选一项 + 跳页面」）
    val dismissThen: (() -> Unit) -> Unit = remember { { action -> action() } }

    CompositionLocalProvider(LocalSheetDismiss provides dismissThen) {
        EnhancedModalBottomSheet(
            visible = visible,
            // 引擎的 onDismiss 是「可见性变了」，true 不是请求关闭，忽略掉：
            // 调用方自己握着那个布尔，这里只把关闭请求转回去
            onDismiss = { shown -> if (!shown) onDismissRequest() },
            containerColor = containerColor,
            modifier = modifier,
            // sheetGesturesEnabled=false 时引擎会拒掉手势与遮罩的隐藏请求，只剩代码里翻布尔这一条路
            cancelable = sheetGesturesEnabled,
            // dragHandle 可空：传空时画成空的拖拽条，与原来 `dragHandle = null` 的观感一致
            dragHandle = { dragHandle?.invoke() },
            shape = shape,
            contentColor = contentColor,
            scrimColor = scrimColor,
            // 内容自己补 inset：引擎的抽屉贴在屏幕最底，只给确认栏加了 navigationBarsPadding，
            // 内容那一层不带；M3 的 ModalBottomSheet 是靠 contentWindowInsets 把内容顶到导航栏之上的。
            // 顶端不补：引擎已经在抽屉表面加了 statusBarsPadding，再补一次会叠出双倍空隙
            sheetContent = {
                Column(
                    modifier = Modifier.windowInsetsPadding(
                        contentWindowInsets().only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                        )
                    )
                ) {
                    content()
                }
            },
        )
    }
}

/*
 * 这里只登一个没映射过来的参数：
 * tonalElevation —— 引擎内部固定 elevation = 0.dp，抽屉靠描边与底色分层，不吃点阴影。
 * 本仓库的调用点没有传过它，签名保留是为了调用方零改动。
 * contentWindowInsets 有映射，见上面 content 那层 Column：底与水平方向补 inset，顶端交给引擎的 statusBarsPadding。
 */

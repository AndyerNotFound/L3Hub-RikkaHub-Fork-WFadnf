package me.rerere.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 弹窗进场与退场用的曲线。
 *
 * 默认取 Material 自己的 motion scheme——expressive 下那是一根带阻尼的弹簧，不是线性曲线。
 * 主题里会按用户的「动画速度」设置覆盖掉：页面转场、列表展开、菜单都走同一套档位，
 * 弹窗不该是唯一还按默认时长慢慢走的那一个。
 * 关掉动效时 motion scheme 本身已经是 snap，这里不用再单独判断。
 */
val LocalDialogEnterSpec = compositionLocalOf<FiniteAnimationSpec<Float>?> { null }
val LocalDialogExitSpec = compositionLocalOf<FiniteAnimationSpec<Float>?> { null }

/** 卡片从下方这么远滑上来。再远就像从屏幕外飞进来，不像原地弹出来。 */
private val DialogSlideDistance = 24.dp

/**
 * 一个弹窗的进场/退场状态。
 *
 * 进场在第一次组合时自己跑完，不要调用方传可见性——`if (show) AlertDialog(...)` 这种写法里，
 * 弹窗一被组合就该出现，这里接住那一下。
 *
 * 退场只能拦住走 [requestDismiss] 的那条路（点外部、返回键、点取消）：反向动画演完再真的关掉。
 * 调用方自己把状态置 false、直接把窗口从组合里拿掉的那种，这里接不住——
 * Compose 的 Dialog 没有「正在退场」这种中间态，除非调用方改成把可见性传进来。
 */
private class DialogMotion(
    val progress: () -> Float,
    val requestDismiss: () -> Unit,
)

@Composable
private fun rememberDialogMotion(onDismissRequest: () -> Unit): DialogMotion {
    val enterSpec = LocalDialogEnterSpec.current ?: MaterialTheme.motionScheme.defaultSpatialSpec()
    val exitSpec = LocalDialogExitSpec.current ?: MaterialTheme.motionScheme.fastSpatialSpec()
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    // 动画演完再回调，那会儿拿到的必须是最后一次传进来的 onDismissRequest
    val dismiss = rememberUpdatedState(onDismissRequest)

    LaunchedEffect(Unit) {
        progress.animateTo(1f, enterSpec)
    }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        progress.animateTo(0f, exitSpec)
        dismiss.value()
    }

    return DialogMotion(progress = { progress.value }, requestDismiss = { closing = true })
}

/**
 * 只动绘制、不动测量：弹窗的窗口尺寸一开始就是最终大小，滑入过程中不会把布局重算一遍。
 * 弹簧会略微过冲，透明度夹回 0..1，位移那点回弹留着。
 */
private fun Modifier.dialogSlideLayer(motion: DialogMotion): Modifier = graphicsLayer {
    val shown = motion.progress()
    alpha = shown.coerceIn(0f, 1f)
    translationY = (1f - shown) * DialogSlideDistance.toPx()
}

/**
 * M3 [AlertDialog] 的滑入滑出版本，参数与原来一一对应，调用处改一下函数名和 import 就行。
 *
 * [modifier] 会落到 AlertDialog 内部那层整屏的 Box 上（M3 的约定），
 * 因此位移作用的是整块内容而不是背景遮罩——遮罩仍然由窗口自己淡入。
 */
@Composable
fun AnimatedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val motion = rememberDialogMotion(onDismissRequest)
    AlertDialog(
        onDismissRequest = motion.requestDismiss,
        confirmButton = confirmButton,
        modifier = modifier.dialogSlideLayer(motion),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        properties = properties,
    )
}

/**
 * M3 [BasicAlertDialog] 的滑入滑出版本，给自带容器、只借一个窗口的那些弹窗用。
 * 参数与 [BasicAlertDialog] 一致。
 */
@Composable
fun AnimatedBasicAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val motion = rememberDialogMotion(onDismissRequest)
    BasicAlertDialog(
        onDismissRequest = motion.requestDismiss,
        modifier = modifier.dialogSlideLayer(motion),
        properties = properties,
        content = content,
    )
}

/**
 * 原生 [Dialog] 的滑入滑出版本。
 *
 * 内容自己画、可能撑满整屏的那些（图片预览、扫码、终端）走这里；
 * 包一层 Box 只为挂动画，不参与测量，窗口大小仍旧由内容决定。
 */
@Composable
fun AnimatedDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = rememberDialogMotion(onDismissRequest)
    Dialog(
        onDismissRequest = motion.requestDismiss,
        properties = properties,
    ) {
        Box(modifier = modifier.dialogSlideLayer(motion)) {
            content()
        }
    }
}

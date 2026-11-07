package me.rerere.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun RikkaConfirmDialog(
    show: Boolean,
    title: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: @Composable () -> Unit,
) {
    if (!show) {
        return
    }

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text,
        // 两个按钮都先让弹窗滑出去，演完再改状态。直接调 onConfirm 的话窗口当场消失，
        // 退场动画根本来不及播。读取必须放在按钮 lambda 里：这个 CompositionLocal
        // 是 AnimatedAlertDialog 在内部提供的，在它外面读只会拿到默认的 null
        confirmButton = {
            val dismissThen = LocalDialogDismiss.current
            TextButton(onClick = { dismissThen?.invoke(onConfirm) ?: onConfirm() }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            val dismissThen = LocalDialogDismiss.current
            TextButton(onClick = { dismissThen?.invoke(onDismiss) ?: onDismiss() }) {
                Text(dismissText)
            }
        }
    )
}

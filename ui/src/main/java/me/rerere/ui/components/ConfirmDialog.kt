package me.rerere.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

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

    val tint = LocalSeparateWindowColors.current?.dialog
    SeparateWindowContent { AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text,
        containerColor = tint ?: AlertDialogDefaults.containerColor,
        tonalElevation = if (tint == null) AlertDialogDefaults.TonalElevation else 0.dp,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    ) }
}

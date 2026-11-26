package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Remove01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.shadow.FilledTonalIconButton
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.components.LocalDialogDismiss

@Composable
fun CompressContextDialog(
    onDismiss: () -> Unit,
    onConfirm: (additionalPrompt: String, keepRecentMessages: Int) -> Unit
) {
    var additionalPrompt by remember { mutableStateOf("") }
    // 默认全部压缩，请求前缀与对话一致，缓存命中最好
    var keepRecentText by remember { mutableStateOf("0") }
    val keepRecentMessages = keepRecentText.toIntOrNull() ?: 0

    // 确认后压缩在对话里后台进行，弹窗直接关掉：聊天页显示压缩中，停止生成可以取消
    val dialogDismiss = LocalDialogDismiss.current
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.chat_page_compress_context_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.chat_page_compress_context_desc))

                // Additional context input
                OutlinedTextField(
                    value = additionalPrompt,
                    onValueChange = { additionalPrompt = it },
                    label = {
                        Text(stringResource(R.string.chat_page_compress_additional_prompt))
                    },
                    placeholder = {
                        Text(stringResource(R.string.chat_page_compress_additional_prompt_hint))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_page_compress_keep_recent),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(
                            onClick = { keepRecentText = (keepRecentMessages - 1).toString() },
                            enabled = keepRecentMessages > 0,
                        ) {
                            Icon(HugeIcons.Remove01, contentDescription = "Decrease")
                        }
                        BasicTextField(
                            value = keepRecentText,
                            onValueChange = { input ->
                                val digits = input.filter { it.isDigit() }.take(4)
                                keepRecentText = digits.trimStart('0').ifEmpty { digits.take(1) }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.titleMediumEmphasized.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(56.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .padding(vertical = 8.dp),
                        )
                        FilledTonalIconButton(onClick = { keepRecentText = (keepRecentMessages + 1).toString() }) {
                            Icon(HugeIcons.Add01, contentDescription = "Increase")
                        }
                    }
                    Text(
                        text = stringResource(R.string.chat_page_compress_keep_recent_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(additionalPrompt, keepRecentMessages)
                onDismiss()
            }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { dialogDismiss?.invoke(onDismiss) ?: onDismiss() }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

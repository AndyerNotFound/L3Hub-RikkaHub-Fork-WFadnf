package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import dev.chrisbanes.haze.HazeState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.glassSurface
import me.rerere.rikkahub.ui.style.IconButton
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.ui.components.SaltModalSheet

@Composable
fun ChatMessageCopySheet(
    visible: Boolean,
    message: UIMessage,
    onDismissRequest: () -> Unit,
    // 底板玻璃的取样源；选字要读得清，没有源时老实保持实色
    hazeState: HazeState? = null,
) {
    val context = LocalContext.current
    SaltModalSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.select_and_copy),
        // 选字时要能上下滚，也常误触边缘：点外部与下拉都不关窗，只留内容里的 X 与返回键
        dismissible = false,
        dismissOnBackPress = true,
        closeButton = false,
        dragEnabled = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        onDismissRequest()
                    }
                ) {
                    Icon(HugeIcons.Cancel01, null)
                }

                Text(
                    text = stringResource(R.string.select_and_copy),
                    style = MaterialTheme.typography.headlineSmall,
                )

                TextButton(
                    onClick = {
                        context.copyMessageToClipboard(message)
                        onDismissRequest()
                    }
                ) {
                    Icon(
                        imageVector = HugeIcons.Copy01,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.copy_all))
                }
            }

            // Content
            val textParts =
                message.parts.filterIsInstance<UIMessagePart.Text>().filter { it.text.isNotBlank() }

            if (textParts.isEmpty()) {
                // No text content available
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_text_content_to_copy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        textParts.fastForEach { textPart ->
                            Text(
                                text = textPart.text,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

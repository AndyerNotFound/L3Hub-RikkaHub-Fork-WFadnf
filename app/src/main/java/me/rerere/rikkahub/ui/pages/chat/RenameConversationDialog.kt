package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.adaptiveShape
import me.rerere.ui.components.AnimatedBasicAlertDialog

/**
 * 会话标题的重命名对话框。
 *
 * 版式是「多行 Outline 输入框 + 两个全宽按钮」，不是 M3 AlertDialog 默认那套
 * 「标题 + 正文 + 右下角两颗小按钮」：
 * - 标题可能很长（自动生成的标题动辄一整句），单行输入框看不全，所以给多行；
 * - 两个动作都占满整宽、上下排列，拇指更容易点中，
 *   也和设置里那批全宽操作项的观感一致。
 *
 * 用 [AnimatedBasicAlertDialog] 而不是 `AnimatedAlertDialog`：后者固定了 M3 的
 * 标题/正文/按钮三段布局，塞不进这种版式；前者只借一个窗口，卡片由这里自己画。
 * 进退场动画仍是项目统一的那一套（滑入 + 淡入）。
 *
 * @param initialTitle 当前标题，进来就填好、光标在末尾，改一个字就能存
 * @param onConfirm 保存；传出去的是 trim 过的标题
 */
@Composable
internal fun RenameConversationDialog(
    initialTitle: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    val canSave = title.isNotBlank()

    AnimatedBasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = adaptiveShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_page_edit_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // 多行 Outline：长标题能折行看全。minLines 给 3 行，
                // 超过就自己长高（maxLines 不设上限，标题本来就可能有换行）。
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.chat_page_edit_title_placeholder),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    // 多行输入框上回车要能换行，不能直接提交
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                )

                // 两个全宽动作：保存是主操作放上面，取消在下面。
                // 顺序按「拇指够得到的那一侧放主操作」——对话框贴在键盘上方时，
                // 越靠上的按钮越远离拇指。
                Button(
                    onClick = {
                        onConfirm(title.trim())
                        onDismissRequest()
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        }
    }
}

package me.rerere.rikkahub.ui.pages.backup.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.sync.RestoreItem
import me.rerere.rikkahub.data.sync.RestoreMode
import me.rerere.rikkahub.data.sync.RestoreOptions
import me.rerere.rikkahub.data.sync.RestoreStageResult
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.ConnectedMultiChoiceButtons
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.ui.components.AnimatedAlertDialog

// 对话框里的顺序按使用频率排：聊天记录、资源文件、设置
private val RESTORE_ITEM_ORDER = listOf(
    RestoreItem.CHAT_RECORDS,
    RestoreItem.FILES,
    RestoreItem.SETTINGS,
)

/** 恢复前的选项弹窗：选合并还是覆盖，以及要恢复哪些数据。 */
@Composable
internal fun RestoreOptionsDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (RestoreOptions) -> Unit,
) {
    // 默认合并：不破坏当前数据，误点也不会丢东西
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    var items by remember { mutableStateOf(RESTORE_ITEM_ORDER.toSet()) }

    AnimatedAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.backup_page_restore_options)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.backup_page_restore_mode),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
                ConnectedChoiceButtons(
                    options = RestoreMode.entries,
                    selected = mode,
                    onSelect = { mode = it },
                ) { option, selected ->
                    ConnectedChoiceLabel(
                        text = stringResource(
                            when (option) {
                                RestoreMode.MERGE -> R.string.backup_page_restore_mode_merge
                                RestoreMode.OVERWRITE -> R.string.backup_page_restore_mode_overwrite
                            }
                        ),
                        selected = selected,
                    )
                }
                Text(
                    text = stringResource(
                        when (mode) {
                            RestoreMode.MERGE -> R.string.backup_page_restore_mode_merge_desc
                            RestoreMode.OVERWRITE -> R.string.backup_page_restore_mode_overwrite_desc
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.backup_page_restore_items),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
                ConnectedMultiChoiceButtons(
                    options = RESTORE_ITEM_ORDER,
                    selected = items,
                    onToggle = { item, checked ->
                        items = if (checked) items + item else items - item
                    },
                ) { option, selected ->
                    ConnectedChoiceLabel(
                        text = stringResource(option.labelRes),
                        selected = selected,
                        icon = HugeIcons.Tick01,
                    )
                }
                if (items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.backup_page_restore_nothing_selected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (mode == RestoreMode.OVERWRITE) {
                    Text(
                        text = stringResource(R.string.backup_page_restore_overwrite_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(RestoreOptions(items = items, mode = mode)) },
                enabled = items.isNotEmpty(),
            ) {
                Text(stringResource(R.string.backup_page_restore_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** 备份里缺了所选类别时的提示文案；没有缺失时返回 null。 */
internal fun restoreSkippedMessage(context: Context, result: RestoreStageResult): String? {
    if (result.skipped.isEmpty()) return null
    val separator = context.getString(R.string.backup_page_restore_separator)
    val labels = result.skipped.joinToString(separator) { context.getString(it.labelRes) }
    return context.getString(R.string.backup_page_restore_skipped, labels)
}

@get:StringRes
private val RestoreItem.labelRes: Int
    get() = when (this) {
        RestoreItem.SETTINGS -> R.string.backup_page_settings
        RestoreItem.CHAT_RECORDS -> R.string.backup_page_chat_records
        RestoreItem.FILES -> R.string.backup_page_files
    }

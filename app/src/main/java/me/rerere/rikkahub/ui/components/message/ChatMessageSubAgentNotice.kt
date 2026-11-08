package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.ui.components.AnimatedAlertDialog

/**
 * 后台子代理跑完后自动回投的结果。
 *
 * 它进请求时用的是 USER 角色（各家 provider 都能带过去），但这句话不是用户说的，
 * 所以这里按系统通知渲染：标题标明来源，正文用低对比度的卡片承载，不套用户气泡。
 * 正文可能很长，点标题行可以折叠。
 *
 * 长按标题行可以把这条通知收掉或改写：回传结果偶尔会带着不需要的内容，
 * 留在上下文里既占 token 又会影响后面几轮，得让用户能就地处理。
 */
@Composable
fun ChatMessageSubAgentNotice(
    message: UIMessage,
    modifier: Modifier = Modifier,
    onEdit: ((String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    // 回传结果通常很长，默认收起，点标题行再展开
    var expanded by remember(message.createdAt) { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    // 两样都没有时这条通知是只读的：不给长按手势，也不弹空菜单
    val hasActions = onEdit != null || onDelete != null
    val openMenu: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuExpanded = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { expanded = !expanded },
                        onLongClick = if (hasActions) openMenu else null,
                    )
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.AiBrain01,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.chat_message_sub_agent_notice_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.chat_message_sub_agent_notice_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.onSurfaceVariant,
                )
            }
            // 贴着标题行开，锚点就是这一行；菜单项最多两条，不给它单独做一套气泡菜单
            if (hasActions) {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (onEdit != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_message_sub_agent_notice_edit)) },
                            leadingIcon = { Icon(HugeIcons.Edit01, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                draft = message.toText().trim()
                                editing = true
                            },
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.delete),
                                    color = colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = HugeIcons.Delete01,
                                    contentDescription = null,
                                    tint = colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete?.invoke()
                            },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(visible = expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    MarkdownBlock(
                        content = message.toText().trim(),
                        onClickCitation = {},
                    )
                }
            }
        }
    }

    if (editing) {
        AnimatedAlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.chat_message_sub_agent_notice_edit_title)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 320.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editing = false
                        onEdit?.invoke(draft.trim())
                    },
                    enabled = draft.isNotBlank(),
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

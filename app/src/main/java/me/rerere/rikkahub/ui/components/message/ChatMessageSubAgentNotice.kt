package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock

/**
 * 后台子代理跑完后自动回投的结果。
 *
 * 它进请求时用的是 USER 角色（各家 provider 都能带过去），但这句话不是用户说的，
 * 所以这里按系统通知渲染：标题标明来源，正文用低对比度的卡片承载，不套用户气泡。
 */
@Composable
fun ChatMessageSubAgentNotice(
    message: UIMessage,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
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
            )
        }
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

package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.ui.theme.extendColors

/**
 * MCP 连接状态指示灯。
 *
 * 绿色表示连接良好，红色表示断开（含空闲、出错、待授权），转圈表示正在连接或重连。
 * 固定占 24dp，和列表项原有的 leading 图标同一尺寸，替换后行高不会跳。
 */
@Composable
fun McpStatusLight(
    status: McpStatus,
    modifier: Modifier = Modifier,
) {
    val connecting = status == McpStatus.Connecting ||
        status == McpStatus.Authorizing ||
        status is McpStatus.Reconnecting
    val description = stringResource(
        when {
            connecting -> R.string.mcp_status_connecting
            status == McpStatus.Connected -> R.string.mcp_status_connected
            else -> R.string.mcp_status_disconnected
        }
    )
    Box(
        modifier = modifier
            .size(24.dp)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (connecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            val color = if (status == McpStatus.Connected) {
                MaterialTheme.extendColors.green6
            } else {
                MaterialTheme.extendColors.red6
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .drawBehind {
                        val dotRadius = 4.5.dp.toPx()
                        // 外圈淡光晕让灯在深浅背景上都立得住
                        drawCircle(color = color.copy(alpha = 0.18f), radius = dotRadius * 1.9f)
                        drawCircle(color = color, radius = dotRadius)
                    }
            )
        }
    }
}

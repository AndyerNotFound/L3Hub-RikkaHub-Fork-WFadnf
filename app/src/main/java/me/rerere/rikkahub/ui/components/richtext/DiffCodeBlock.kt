package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.ui.richtext.DiffAddedColor
import me.rerere.ui.richtext.DiffRemovedColor
import me.rerere.ui.richtext.DiffView

/** 单个代码块里最多渲染的 diff 行数，超出的部分折叠成一行提示。 */
internal const val MAX_DIFF_RENDER_LINES = 600

/**
 * 原生 unified diff 渲染：按行前缀着色，增删行数在顶部汇总。
 *
 * 这里只做展示，不解析成可应用的补丁，也不会去写文件。源码仍然完整保留在
 * [HighlightCodeBlock] 的复制与下载路径里，切到源码视图看到的就是原文。
 */
@Composable
internal fun DiffCodeBlock(
    code: String,
    modifier: Modifier = Modifier,
) {
    val stats = remember(code) { diffLineStats(code) }
    Column(modifier) {
        DiffStatsHeader(additions = stats.first, deletions = stats.second)
        DiffView(
            diff = code,
            modifier = Modifier.fillMaxWidth(),
            maxLines = MAX_DIFF_RENDER_LINES,
            showFileHeader = true,
        )
    }
}

@Composable
private fun DiffStatsHeader(additions: Int, deletions: Int) {
    if (additions == 0 && deletions == 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (additions > 0) {
            Text(
                text = "+$additions",
                color = DiffAddedColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        if (deletions > 0) {
            Text(
                text = "-$deletions",
                color = DiffRemovedColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        Text(
            text = "diff",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

/** 供导出等纯文本场景使用的着色，不参与 Compose 组合。 */
internal fun diffLineTint(line: String): Color = when {
    line.startsWith("+++") || line.startsWith("---") -> Color.Transparent
    line.startsWith("@@") -> Color.Transparent
    line.startsWith("+") -> DiffAddedColor.copy(alpha = 0.12f)
    line.startsWith("-") -> DiffRemovedColor.copy(alpha = 0.12f)
    else -> Color.Transparent
}

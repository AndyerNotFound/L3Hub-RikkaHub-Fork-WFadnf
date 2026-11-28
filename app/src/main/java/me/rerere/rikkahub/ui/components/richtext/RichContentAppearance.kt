package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.rerere.ui.charts.ChartCard
import me.rerere.ui.charts.ChartSpec

/** 富内容卡片（代码块、表格、图表）的标题栏底色：跟随主题，不采样背景。 */
@Composable
internal fun richContentHeaderColor(): Color = MaterialTheme.colorScheme.surfaceContainerHighest

/**
 * 富内容卡片的框：一道 [MaterialTheme] 的 outlineVariant 线 + 裁到 large 圆角的实色底。
 *
 * [streaming] 只在流式期间决定要不要压掉特效，朴素外观没有特效，参数留给调用点；
 * [withLegacyFrame] 为 false 时只让调用方自己处理底色（比如代码块自己铺了底），这里不叠。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun Modifier.richContentFrame(streaming: Boolean, withLegacyFrame: Boolean = true): Modifier {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    return if (withLegacyFrame) {
        border(1.dp, scheme.outlineVariant, shape).clip(shape).background(scheme.surfaceContainer)
    } else {
        this
    }
}

@Composable
internal fun RichChartCard(spec: ChartSpec, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    ChartCard(
        spec = spec,
        modifier = modifier,
        containerColor = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    )
}

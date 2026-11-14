package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 波浪条的原生高度（10dp）；直条只有 4dp，统一按这个行高排版 */
private val WaveProgressBarHeight = 10.dp

/**
 * 进度条：运行中交给 MD3 的波浪条，停下来换回 MD3 的直条。
 *
 * 两种原生指示器高度不同，外面统一套一层固定行高，生成开始和结束时进度条原地换样式，
 * 行高不会跟着跳。
 *
 * @param fraction 0~1 的填充比例；运行中传 `null` 表示没有可量的额度，画成不定进度
 */
@Composable
fun WaveProgressBar(
    fraction: Float?,
    running: Boolean,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
) {
    val target = (fraction ?: 0f).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "waveProgressFill",
    )
    Box(
        modifier = modifier.height(WaveProgressBarHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            running && fraction == null -> LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = trackColor,
            )

            running -> LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = trackColor,
            )

            else -> LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = trackColor,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WaveProgressBarPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("running", style = MaterialTheme.typography.labelSmall)
            WaveProgressBar(fraction = 0.35f, running = true, modifier = Modifier.fillMaxWidth())
            WaveProgressBar(fraction = null, running = true, modifier = Modifier.fillMaxWidth())
            Text("idle", style = MaterialTheme.typography.labelSmall)
            WaveProgressBar(fraction = 0.35f, running = false, modifier = Modifier.fillMaxWidth())
            WaveProgressBar(fraction = 1f, running = false, modifier = Modifier.fillMaxWidth())
            WaveProgressBar(fraction = 0f, running = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

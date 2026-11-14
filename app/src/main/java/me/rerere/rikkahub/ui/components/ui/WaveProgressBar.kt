package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 一道波浪滚过整段波长要多久 */
private const val WAVE_TRAVEL_MILLIS = 1_300

/**
 * 一条会「呼吸」的进度条。
 *
 * 运行中沿着填充方向滚过一道正弦波，停下来后波幅收回、落成普通直条；
 * 动的只是形状，填充量始终是 [fraction]，用到多少照样读得出来。
 */
@Composable
fun WaveProgressBar(
    fraction: Float,
    running: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
    strokeWidth: Dp = 4.dp,
    amplitude: Dp = 2.dp,
    wavelength: Dp = 14.dp,
) {
    val fill by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 320),
        label = "waveProgressFill",
    )
    val wave by animateFloatAsState(
        targetValue = if (running) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "waveProgressWave",
    )
    // 相位只在运行中推进：停下来时波纹就地收回，不会继续空转
    val phase = if (running) {
        rememberInfiniteTransition(label = "waveProgressTravel").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = WAVE_TRAVEL_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "waveProgressPhase",
        )
    } else {
        null
    }

    Canvas(modifier = modifier.height(strokeWidth + amplitude * 2)) {
        // 三个值都在绘制阶段读取，每帧只重画，不重组这一层
        val travel = phase?.value ?: 0f
        val barHeight = strokeWidth.toPx()
        val crest = amplitude.toPx() * wave
        val step = wavelength.toPx()
        val centerY = size.height / 2f
        val fillWidth = size.width * fill

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, centerY - barHeight / 2f),
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f),
        )
        if (fillWidth <= 0f) return@Canvas

        // 波浪就是一条描粗的曲线：波幅归零时它退化成直条，和静止状态同一个画法
        val wavePath = Path().apply {
            var x = -step
            var rising = true
            moveTo(x, centerY)
            while (x < fillWidth + step) {
                quadraticTo(
                    x + step / 4f,
                    centerY + if (rising) -crest * 2f else crest * 2f,
                    x + step / 2f,
                    centerY,
                )
                x += step / 2f
                rising = !rising
            }
        }
        // 圆角裁切让填充的末端始终是圆的，波浪从切口里穿过去也不会露直角
        val clip = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = fillWidth,
                    bottom = size.height,
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            )
        }
        clipPath(clip) {
            translate(left = -travel * step) {
                drawPath(
                    path = wavePath,
                    color = color,
                    style = Stroke(width = barHeight, cap = StrokeCap.Round),
                )
            }
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
            WaveProgressBar(fraction = 0.35f, running = true, modifier = Modifier.fillMaxWidth())
            WaveProgressBar(fraction = 0.35f, running = false, modifier = Modifier.fillMaxWidth())
            WaveProgressBar(fraction = 1f, running = false, modifier = Modifier.fillMaxWidth())
            WaveProgressBar(fraction = 0f, running = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

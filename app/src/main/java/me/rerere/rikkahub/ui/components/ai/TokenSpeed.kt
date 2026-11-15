package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.estimateTokens
import kotlin.math.cos
import kotlin.math.sin

/** 生成速度的三档配色：极慢灰、中等冰蓝、最快红，给消息列表下方的加载图标用 */
internal val TokenSpeedSlowColor = Color(0xFF9E9E9E)
internal val TokenSpeedMediumColor = Color(0xFF8DDDF2)
internal val TokenSpeedFastColor = Color(0xFFFF4D4F)

// 相对本轮峰值的档位：≤15% 停在灰色，50% 是冰蓝，≥85% 转红
private const val SLOW_RATIO = 0.15f
private const val MEDIUM_RATIO = 0.50f
private const val FAST_RATIO = 0.85f

/** 按「当前速度占据本轮峰值的比例」取色 */
fun tokenSpeedRatioColor(peakRatio: Float): Color = when {
    peakRatio <= SLOW_RATIO -> TokenSpeedSlowColor
    peakRatio < MEDIUM_RATIO -> lerp(
        TokenSpeedSlowColor,
        TokenSpeedMediumColor,
        (peakRatio - SLOW_RATIO) / (MEDIUM_RATIO - SLOW_RATIO),
    )

    peakRatio < FAST_RATIO -> lerp(
        TokenSpeedMediumColor,
        TokenSpeedFastColor,
        (peakRatio - MEDIUM_RATIO) / (FAST_RATIO - MEDIUM_RATIO),
    )

    else -> TokenSpeedFastColor
}

/**
 * 生成中的输出速度。
 *
 * 速度取「最近一段时间里模型新写出来的 token」，峰值只记本轮：本轮结束后读数留着，
 * 供上下文预警里的表盘显示；下一轮开始时才清空。
 */
@Stable
class GenerationSpeed {
    /** 当前速度（tok/s），已按采样窗口平滑 */
    var tokensPerSecond by mutableFloatStateOf(0f)
        private set

    /** 本轮出现过的最高速度，作为表盘的最大刻度 */
    var peakTokensPerSecond by mutableFloatStateOf(0f)
        private set

    /** 当前速度相对本轮峰值的位置：0 表示停住，1 表示正处在最高速 */
    val peakRatio: Float
        get() = if (peakTokensPerSecond <= 0f) {
            0f
        } else {
            (tokensPerSecond / peakTokensPerSecond).coerceIn(0f, 1f)
        }

    internal fun reset() {
        tokensPerSecond = 0f
        peakTokensPerSecond = 0f
    }

    internal fun publish(speed: Float) {
        tokensPerSecond = speed
        if (speed > peakTokensPerSecond) peakTokensPerSecond = speed
    }
}

/** 采样间隔：图标颜色与表盘指针都以这个频率重算 */
private const val SAMPLE_INTERVAL_MS = 100L

/** 统计窗口：窗口越长越稳，越短越跟手；1.6s 大致是「一句话」的尺度 */
private const val SPEED_WINDOW_MS = 1600L

/** 起步阶段窗口还没铺开，一次首字爆发就能算出几百 tok/s，等这之后再公布速度 */
private const val WARM_UP_MS = 900L

/**
 * 采样当前轮次的生成速度。
 *
 * 只在 [loading] 期间采样：读会话状态算 token 增量，不参与组合，读数变化只影响读了
 * [GenerationSpeed] 的那一小块界面（颜色、表盘）。
 */
@Composable
fun rememberGenerationSpeed(
    conversationState: State<Conversation>,
    loading: Boolean,
): GenerationSpeed {
    val speed = remember { GenerationSpeed() }
    LaunchedEffect(loading) {
        // 本轮结束不清零：表盘要停在最后一轮的数字上，下一轮开始时再重来
        if (!loading) return@LaunchedEffect
        speed.reset()
        val samples = ArrayDeque<Pair<Long, Int>>()
        var lastTokens = -1
        while (true) {
            val now = System.nanoTime() / 1_000_000
            val tokens = conversationState.value.generatedTokens()
            // 重新生成、切分支会把计数退回去，从这一刻重新起算
            if (lastTokens >= 0 && tokens < lastTokens) samples.clear()
            lastTokens = tokens
            samples.addLast(now to tokens)
            while (samples.size > 2 && now - samples.first().first > SPEED_WINDOW_MS) {
                samples.removeFirst()
            }
            val (startTime, startTokens) = samples.first()
            val span = now - startTime
            if (span >= WARM_UP_MS) {
                speed.publish(((tokens - startTokens) * 1000f / span).coerceAtLeast(0f))
            }
            delay(SAMPLE_INTERVAL_MS)
        }
    }
    return speed
}

/**
 * 本轮模型已经写出来的 token：正文、思考与工具入参都算，工具输出不算——
 * 那是工具在跑，不是模型在生成。
 */
private fun Conversation.generatedTokens(): Int {
    val message = messageNodes.lastOrNull { it.role == MessageRole.ASSISTANT }?.currentMessage
        ?: return 0
    return message.parts.sumOf { part ->
        when (part) {
            is UIMessagePart.Text -> estimateTokens(part.text)
            is UIMessagePart.Reasoning -> estimateTokens(part.reasoning)
            is UIMessagePart.ToolCall -> estimateTokens(part.arguments)
            is UIMessagePart.Tool -> estimateTokens(part.input)
            is UIMessagePart.ServerTool -> estimateTokens(part.input?.toString().orEmpty())
            else -> 0
        }
    }
}

/** 表盘凹槽占整圈的比例，剩下的是正下方的开口 */
private const val GAUGE_SWEEP = 270f
private const val GAUGE_START = 135f

/**
 * 车速仪样式的小表盘：满刻度是本轮跑过的最高速度，指针是当前速度。
 *
 * 颜色取自主题的主色容器一套（凹槽 primaryContainer、弧与轴 primary、指针 onPrimaryContainer），
 * 不再用灰/蓝/红那套自定义色。读数在绘制阶段取，采样每 100ms 只重画这只表盘，不引起重组。
 */
@Composable
fun TokenSpeedGauge(
    generationSpeed: GenerationSpeed,
    running: Boolean,
    fallbackSpeed: Float?,
    modifier: Modifier = Modifier,
    diameter: Dp = 30.dp,
) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier = modifier.size(diameter)) {
        val stroke = 2.5.dp.toPx()
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val arcTopLeft = Offset(stroke / 2f, stroke / 2f)

        // 生成中看实时速度；停下来看这一轮的均值，刻度仍是这一轮跑到的最高速度
        val live = generationSpeed.tokensPerSecond
        val peak = generationSpeed.peakTokensPerSecond
        val speed = if (running) live else (fallbackSpeed ?: live)
        val scale = when {
            peak > 0f -> maxOf(peak, speed)
            // 历史会话没有采样数据，留出余量让指针落在八分位，不至于一上来就顶到满格
            speed > 0f -> speed * 1.25f
            else -> 0f
        }
        val ratio = if (scale > 0f) (speed / scale).coerceIn(0f, 1f) else 0f

        drawArc(
            color = colors.primaryContainer,
            startAngle = GAUGE_START,
            sweepAngle = GAUGE_SWEEP,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (ratio > 0f) {
            drawArc(
                color = colors.primary,
                startAngle = GAUGE_START,
                sweepAngle = GAUGE_SWEEP * ratio,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        val center = Offset(size.width / 2f, size.height / 2f)
        val needleLength = size.minDimension / 2f - stroke - 1.5.dp.toPx()
        val angle = Math.toRadians((GAUGE_START + GAUGE_SWEEP * ratio).toDouble())
        drawLine(
            color = colors.onPrimaryContainer,
            start = center,
            end = center + Offset(
                x = (cos(angle) * needleLength).toFloat(),
                y = (sin(angle) * needleLength).toFloat(),
            ),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(color = colors.primary, radius = 1.4.dp.toPx(), center = center)
    }
}

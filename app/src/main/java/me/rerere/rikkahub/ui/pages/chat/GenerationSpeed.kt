package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.delay
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.estimateTokens

/**
 * 生成中的输出速度，只用来给加载图标上色。
 *
 * 速度取「最近一段时间里模型新写出来的 token」，峰值只记本轮：重新生成、下一轮开始时归零。
 */
@Stable
internal class GenerationSpeed {
    /** 当前速度（tok/s），已按采样窗口平滑 */
    var tokensPerSecond by mutableFloatStateOf(0f)
        private set

    /** 本轮出现过的最高速度，作为「红色」的基准 */
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

/** 采样间隔：图标颜色每次变化都以这个频率重算，动画负责把台阶抹平 */
private const val SAMPLE_INTERVAL_MS = 100L

/** 统计窗口：窗口越长越稳，越短越跟手；1.6s 大致是「一句话」的尺度 */
private const val SPEED_WINDOW_MS = 1600L

/** 起步阶段窗口还没铺开，一次首字爆发就能算出几百 tok/s，等这之后再公布速度 */
private const val WARM_UP_MS = 900L

private val SlowIndicatorColor = Color(0xFF9E9E9E)
private val MediumIndicatorColor = Color(0xFF8DDDF2)
private val PeakIndicatorColor = Color(0xFFFF4D4F)

// 相对本轮峰值的档位：≤15% 停在灰色，50% 是冰蓝，≥85% 转为红色
private const val SLOW_RATIO = 0.15f
private const val MEDIUM_RATIO = 0.50f
private const val PEAK_RATIO = 0.85f

/** 速度档位对应的图标颜色，档位之间按比例插值 */
internal fun generationIndicatorColor(peakRatio: Float): Color = when {
    peakRatio <= SLOW_RATIO -> SlowIndicatorColor
    peakRatio < MEDIUM_RATIO -> lerp(
        SlowIndicatorColor,
        MediumIndicatorColor,
        (peakRatio - SLOW_RATIO) / (MEDIUM_RATIO - SLOW_RATIO),
    )

    peakRatio < PEAK_RATIO -> lerp(
        MediumIndicatorColor,
        PeakIndicatorColor,
        (peakRatio - MEDIUM_RATIO) / (PEAK_RATIO - MEDIUM_RATIO),
    )

    else -> PeakIndicatorColor
}

/**
 * 采样当前轮次的生成速度。
 *
 * 只在 [loading] 期间运行：读会话状态算 token 增量，不参与组合，读数变化只影响读了
 * [GenerationSpeed] 的那一小块界面。
 */
@Composable
internal fun rememberGenerationSpeed(
    conversationState: State<Conversation>,
    loading: Boolean,
): GenerationSpeed {
    val speed = remember { GenerationSpeed() }
    LaunchedEffect(loading) {
        speed.reset()
        if (!loading) return@LaunchedEffect
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

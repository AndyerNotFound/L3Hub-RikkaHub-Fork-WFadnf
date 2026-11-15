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
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.estimateTokens
import kotlin.math.cos
import kotlin.math.sin
import kotlin.uuid.Uuid

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

/**
 * 统计窗口：要盖住一次网络突发。代理攒批、SSE 合并常常 1s 才到一批 token，
 * 窗口短于批间隔时窗口里有时一批都没有，速度就掉到 0，指针在两极之间来回跳。
 */
private const val SPEED_WINDOW_MS = 2500L

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
        val startedAt = System.nanoTime() / 1_000_000
        // 读数会回落：多步之间消息被整条替换、重新生成、切分支都会把累计值退回去。
        // 把落差记进基数让计数器保持单调，否则每回落一次速度都要从零重算，看起来就是停住了
        val counter = GeneratedTokenCounter()
        var baseline = 0
        var lastRaw = -1
        while (true) {
            val now = System.nanoTime() / 1_000_000
            val raw = counter.count(conversationState.value.currentMessages)
            if (lastRaw >= 0 && raw < lastRaw) baseline += lastRaw - raw
            lastRaw = raw
            val tokens = raw + baseline
            samples.addLast(now to tokens)
            while (samples.size > 2 && now - samples.first().first > SPEED_WINDOW_MS) {
                samples.removeFirst()
            }
            // 窗口铺满才公布：不足一个窗口时读到的全是首包突发，峰值会被一次爆发顶到天上，
            // 之后整轮都够不着，指针就一直贴在零附近
            if (now - startedAt >= SPEED_WINDOW_MS) {
                val (startTime, startTokens) = samples.first()
                val span = (now - startTime).coerceAtLeast(1)
                speed.publish(((tokens - startTokens) * 1000f / span).coerceAtLeast(0f))
            }
            delay(SAMPLE_INTERVAL_MS)
        }
    }
    return speed
}

/**
 * 单条消息里模型已经写出来的 token：正文、思考与工具入参都算，工具输出不算——
 * 那是工具在跑，不是模型在生成。
 */
private fun UIMessage.modelWrittenTokens(): Int = parts.sumOf { part ->
    when (part) {
        is UIMessagePart.Text -> estimateTokens(part.text)
        is UIMessagePart.Reasoning -> estimateTokens(part.reasoning)
        is UIMessagePart.ToolCall -> estimateTokens(part.arguments)
        is UIMessagePart.Tool -> estimateTokens(part.input)
        is UIMessagePart.ServerTool -> estimateTokens(part.input?.toString().orEmpty())
        else -> 0
    }
}

/**
 * 整段会话的累计值，按消息 id 缓存每条消息的计数。
 *
 * 累计整段而不是只看最后一条回复：多步之间回复会被整条替换、后面还可能追加通知节点，
 * 只看最后一条时读数会不断回落，速度就跟着归零。
 * 缓存按对象同一性判断，一轮里只有正在写的那条会换对象，其余直接复用，不每一帧扫全文。
 */
private class GeneratedTokenCounter {
    private val cache = HashMap<Uuid, Pair<UIMessage, Int>>()

    fun count(messages: List<UIMessage>): Int {
        // 删消息、切分支之后把不再出现的 id 清掉，别留着旧对象
        if (cache.size > messages.size * 2 + 16) {
            cache.keys.retainAll(messages.mapTo(HashSet()) { it.id })
        }
        var total = 0
        for (message in messages) {
            val cached = cache[message.id]
            total += if (cached != null && cached.first === message) {
                cached.second
            } else {
                message.modelWrittenTokens().also { cache[message.id] = message to it }
            }
        }
        return total
    }
}

/** 表盘凹槽占整圈的比例，剩下的是正下方的开口 */
private const val GAUGE_SWEEP = 270f
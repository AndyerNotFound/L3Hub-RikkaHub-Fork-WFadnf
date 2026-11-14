package me.rerere.rikkahub.ui.modifier

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.LocalMotionBlur

/** 底部多高的一段参与渐显。约一到两行，够看出「刚出来的字还在落定」。 */
private val STREAMING_REVEAL_SPAN = 26.dp

/** 渐显区最底部保留的最低不透明度：不能整行看不见，否则读起来像缺字。 */
private const val STREAMING_REVEAL_FLOOR = 0.35f

/**
 * 流式文本的落定过渡：整块内容本身不动，只有底部最新长出来的那一小段是淡的，
 * 随下一次追加往上推、逐渐实化；生成停下后整块回到全不透明。
 *
 * 聊天正文由富文本渲染器整块排版，没有办法只对最后一行做位移或模糊：
 * RenderEffect 作用于整个图层，之前按整块加模糊会把已经读过的正文一起糊掉。
 * 这里改用绘制层的一份越往下越淡的遮罩（DstIn），只影响底部那一小段，
 * 既不会牵动上面的文字，也不用二次绘制，生成中不掉帧。
 *
 * [length] 传当前已渲染的文本长度，只在它变大时刷新遮罩；[enabled] 由调用点按场景控制。
 * 关闭动态模糊时直接返回原 modifier。
 */
@Composable
fun Modifier.streamingTextMotion(
    length: Int,
    enabled: Boolean = true,
    span: Dp = STREAMING_REVEAL_SPAN,
): Modifier {
    val setting = LocalMotionBlur.current
    val active = enabled && setting.enabled
    val spanPx = with(LocalDensity.current) { span.toPx() }
    val reveal = remember { Animatable(0f) }
    // 首次组合是打开会话或翻回旧消息，不是「刚长出来」，不播
    var lastLength by remember { mutableIntStateOf(-1) }

    LaunchedEffect(length, active, setting.durationMillis) {
        if (!active) return@LaunchedEffect
        val grew = lastLength >= 0 && length > lastLength
        lastLength = length
        if (!grew) return@LaunchedEffect
        // 每来一段就把遮罩推回满格，随后线性收掉；连续输出时底部一直是淡的，
        // 停下后跟着「过渡时长」慢慢实化，读起来就是文字一点点落定
        reveal.snapTo(1f)
        reveal.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = (setting.durationMillis * 1.6f).toInt().coerceAtLeast(1),
                easing = LinearOutSlowInEasing,
            ),
        )
    }

    return this
        // DstIn 只应该作用在正文这一层，不能连下面的气泡底色一起吃掉
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val value = reveal.value
            if (value <= 0f) return@drawWithContent
            val fade = spanPx * value
            if (fade <= 1f) return@drawWithContent
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black,
                    1f to Color.Black.copy(alpha = STREAMING_REVEAL_FLOOR),
                    startY = size.height - fade,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}

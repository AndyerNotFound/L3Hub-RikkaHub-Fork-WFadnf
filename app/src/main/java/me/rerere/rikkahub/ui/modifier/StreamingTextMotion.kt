package me.rerere.rikkahub.ui.modifier

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.LocalMotionBlur

/** 每多出一段文字，整块内容先落在原位下方的这一点距离里，再回到原位。 */
private val STREAMING_LIFT = 3.dp

/** 跟着位移一起收掉的模糊半径，落位时一定回到零。 */
private val STREAMING_BLUR_RADIUS = 4.dp

/**
 * 流式文本的飘入过渡：内容每长一截，整块文字先沉到原位下方一点，再带着模糊浮回原位。
 *
 * 聊天正文是富文本，渲染器不给逐字拆分的口子，所以只能整块做。位移只走绘制层，
 * 不参与测量：文字该占的高度一开始就占好，飘的过程里不会把下面的内容挤来挤去。
 * 连续输出时每一段都往上补一点当前偏移，位移不会每帧从头跳一次；生成停下以后才真正落位。
 *
 * [length] 传当前已经渲染出来的文本长度，只在它变大时触发；[enabled] 给调用点按场景控制。
 * 关闭动态模糊时什么都不做，直接返回原 modifier。
 */
@Composable
fun Modifier.streamingTextMotion(
    length: Int,
    enabled: Boolean = true,
    lift: Dp = STREAMING_LIFT,
    maxBlurRadius: Dp = STREAMING_BLUR_RADIUS,
): Modifier {
    val setting = LocalMotionBlur.current
    val active = enabled && setting.enabled
    val liftPx = with(LocalDensity.current) { lift.toPx() }
    val blurSteps = rememberMotionBlurSteps(maxRadius = maxBlurRadius)
    val amount = remember { Animatable(0f) }
    // 首次组合是打开会话或滚回旧消息，不是「刚长出来」，不播
    var lastLength by remember { mutableIntStateOf(-1) }

    LaunchedEffect(length, active, setting.durationMillis) {
        if (!active) return@LaunchedEffect
        val grew = lastLength >= 0 && length > lastLength
        lastLength = length
        if (!grew) return@LaunchedEffect
        // 上一段还没落位就又来了：从当前值往上补，别每帧都从头跳到最下方
        amount.snapTo((amount.value + 0.45f).coerceAtMost(1f))
        amount.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = setting.durationMillis,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    return this.graphicsLayer {
        val value = amount.value
        translationY = value * liftPx
        renderEffect = blurSteps.at(value)
    }
}

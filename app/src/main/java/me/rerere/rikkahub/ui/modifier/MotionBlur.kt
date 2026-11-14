package me.rerere.rikkahub.ui.modifier

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 几档预生成的模糊效果。
 *
 * 模糊半径逐帧现造会一直分配对象，这里按半径档位预备好，动画时只按进度取最近的一档。
 * API 31 以下没有 RenderEffect，列表为空，退化成分辨率不变、只做高度与透明度动画。
 */
@Stable
class MotionBlurSteps(private val steps: List<RenderEffect?>) {
    /** [amount] 取 0f（不模糊）到 1f（最大模糊） */
    fun at(amount: Float): RenderEffect? {
        if (steps.isEmpty()) return null
        val index = (amount.coerceIn(0f, 1f) * steps.lastIndex).roundToInt()
        return steps[index]
    }
}

/**
 * 同一组半径在整棵组合里共用一份：会话列表一屏几十条，各自现造几百个 RenderEffect 不划算。
 * RenderEffect 本身不可变，多处 graphicsLayer 引用同一个实例是安全的。
 */
private val blurStepsCache = mutableMapOf<Triple<Float, Float, Int>, MotionBlurSteps>()
private const val BLUR_STEPS_CACHE_LIMIT = 8

// 整块内容都可见的容器不能一翻状态就糊满，先给一小段把模糊推上去，后面的衰减才接得顺；
// 推得太慢会错过收放最快的那一段，看起来像动画都快结束了才糊一下
private const val BLUR_ATTACK_MILLIS = 60

@Composable
fun rememberMotionBlurSteps(
    maxRadius: Dp = 6.dp,
    steps: Int = 4,
): MotionBlurSteps {
    val density = LocalDensity.current
    return remember(density.density, maxRadius, steps) {
        val key = Triple(density.density, maxRadius.value, steps)
        blurStepsCache.getOrPut(key) {
            // 组合只在主线程跑，这里不加锁；条目数上限很小，满了整批换掉
            if (blurStepsCache.size >= BLUR_STEPS_CACHE_LIMIT) blurStepsCache.clear()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && steps > 0) {
                val maxPx = with(density) { maxRadius.toPx() }
                MotionBlurSteps(
                    listOf<RenderEffect?>(null) + List(steps) { index ->
                        val radius = maxPx * (index + 1) / steps
                        AndroidRenderEffect
                            .createBlurEffect(radius, radius, Shader.TileMode.DECAL)
                            .asComposeRenderEffect()
                    }
                )
            } else {
                MotionBlurSteps(emptyList())
            }
        }
    }
}

/**
 * 展开/收起容器：与 [AnimatedVisibility] 用法一致，额外在高度收放的这段时间给内容叠一层动态模糊。
 *
 * 两个方向的包络不同：展开时内容从零尺寸长出来并淡入，起始一帧就带最大模糊也看不见，随后跟着一起聚焦；
 * 收起时内容已经完整地摆在屏幕上，模糊要从清晰慢慢推上去，不能一翻状态就糊满，否则第一帧会硬跳一下。
 * 曲线取自主题的动效方案，关掉动效时收放与模糊一起 snap。
 */
@Composable
fun MotionExpandCollapse(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = expandVertically(
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    ) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()),
    exit: ExitTransition = shrinkVertically(
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    ) + fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()),
    maxBlurRadius: Dp = 6.dp,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    // 状态放在 AnimatedVisibility 外面：收起时内容会离开组合，
    // 放在里面的话再展开就是全新的一份状态，模糊播不出来。
    val blur = rememberMotionBlurSteps(maxRadius = maxBlurRadius)
    val decay = remember { Animatable(0f) }
    // 和高度收放取同一条曲线：模糊与位移同时开始、同时结束，不会一边还在动一边已经清晰
    val blurSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // 首次进入组合不算一次翻转：页面加载时就展开的内容不该糊一下
    var lastVisible by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible != lastVisible) {
            lastVisible = visible
            if (visible) {
                // 展开：内容还在零尺寸、透明度为零，起始就糊满也看不出来，随后随收放一起变清晰
                decay.snapTo(1f)
                decay.animateTo(0f, blurSpec)
            } else {
                // 收起：内容已经在屏幕上，从当前清晰度慢慢推上去，
                // 被打断时也接着往上走，不会先跳回清晰再重新糊
                decay.animateTo(1f, blurSpec)
            }
        }
    }
    // 模糊加在 AnimatedVisibility 自己的层上：不额外套一层布局，
    // 内容原来在 Column / Row / Box 里怎么排，现在还怎么排。
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.graphicsLayer {
            renderEffect = blur.at(decay.value)
        },
        enter = enter,
        exit = exit,
        content = content,
    )
}

/**
 * [MotionExpandCollapse] 的横向版本：宽度收放，用于按钮文字、侧栏一类从左往右挤出的内容。
 */
@Composable
fun MotionExpandCollapseHorizontal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    maxBlurRadius: Dp = 4.dp,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    MotionExpandCollapse(
        visible = visible,
        modifier = modifier,
        enter = expandHorizontally(motionScheme.defaultSpatialSpec<IntSize>()) +
            fadeIn(motionScheme.defaultEffectsSpec<Float>()),
        exit = shrinkHorizontally(motionScheme.defaultSpatialSpec<IntSize>()) +
            fadeOut(motionScheme.defaultEffectsSpec<Float>()),
        maxBlurRadius = maxBlurRadius,
        content = content,
    )
}

/**
 * 给已经用 [androidx.compose.animation.animateContentSize] 收放的容器补一层动态模糊。
 *
 * 状态每翻一次，模糊从无快速推到最大、再衰减回没有；[toggle] 传的通常是控制展开的那个布尔值。
 * 容器在整个动画里都可见，不能直接 snap 到最大，否则第一帧会硬跳一下。
 */
@Composable
fun Modifier.motionBlurOnToggle(
    toggle: Any?,
    maxRadius: Dp = 5.dp,
    // 衰减曲线要和容器自己的收放对齐：拖得比收缩还久，容器停下来了模糊还在动
    animationSpec: AnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
): Modifier {
    val blur = rememberMotionBlurSteps(maxRadius = maxRadius)
    val decay = remember { Animatable(0f) }
    var lastToggle by remember { mutableStateOf(toggle) }
    LaunchedEffect(toggle) {
        if (toggle != lastToggle) {
            lastToggle = toggle
            decay.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = BLUR_ATTACK_MILLIS,
                    easing = LinearOutSlowInEasing,
                ),
            )
            decay.animateTo(0f, animationSpec)
        }
    }
    return this.graphicsLayer {
        renderEffect = blur.at(decay.value)
    }
}

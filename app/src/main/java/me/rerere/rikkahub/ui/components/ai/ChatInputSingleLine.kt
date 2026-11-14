package me.rerere.rikkahub.ui.components.ai

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.rikkahub.R
import kotlin.math.roundToInt

/**
 * 单行输入框从一行展开到两行用的曲线。
 *
 * 和设计稿一致：起手快、收尾慢，文字带上移和胶囊滑入共用同一条节奏，
 * 只是靠 delay 错峰。
 */
internal val ChatInputExpandEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** 模型选择胶囊：展开时贴着左侧按钮组滑出，收起时原路退回 */
@Composable
internal fun ChatInputModelPill(
    model: Model?,
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val slideDistance = with(density) { 12.dp.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(durationMillis = 200, delayMillis = 110)) +
            slideInHorizontally(
                animationSpec = tween(
                    durationMillis = 380,
                    delayMillis = 110,
                    easing = ChatInputExpandEasing,
                ),
                initialOffsetX = { -slideDistance },
            ),
        exit = fadeOut(tween(durationMillis = 120)) +
            slideOutHorizontally(
                animationSpec = tween(durationMillis = 200, easing = ChatInputExpandEasing),
                targetOffsetX = { -slideDistance },
            ),
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .height(30.dp)
                .widthIn(max = 128.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = model?.displayName ?: stringResource(R.string.model_list_select_model),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = HugeIcons.ArrowDown01,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 逐字渐变的节奏，跟参考稿一致：旧字先走、错峰，新字随后补位 */
private const val PLACEHOLDER_STAGGER_MS = 14
private const val PLACEHOLDER_OUT_MS = 230
private const val PLACEHOLDER_IN_MS = 300
private const val PLACEHOLDER_GAP_MS = 70
private const val PLACEHOLDER_START_DELAY_MS = 80

private val PlaceholderOutEasing = CubicBezierEasing(0.5f, 0f, 0.9f, 0.4f)
private val PlaceholderInEasing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)

/**
 * 输入框占位文案的逐字渐变：旧文案上浮、模糊、淡出，新文案自下而上补位。
 *
 * 只在文案真的换了（聚焦展开 / 收起、切换模型）时逐字渲染；动画结束后退回一个
 * 普通 [Text]，不给静止状态留二十来个布局节点。
 */
@Composable
internal fun MorphingPlaceholder(
    target: String,
    modifier: Modifier = Modifier,
) {
    var resting by remember { mutableStateOf(target) }
    var leaving by remember { mutableStateOf<String?>(null) }
    var morphing by remember { mutableStateOf(false) }
    val outDriver = remember { Animatable(0f) }
    val inDriver = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target == resting) return@LaunchedEffect
        val from = resting
        leaving = from
        morphing = true
        // 中途再换文案时（快速聚焦又收起）要能退回静止态，不能停在半透明的逐字层上
        try {
            val outTotal = from.length * PLACEHOLDER_STAGGER_MS + PLACEHOLDER_OUT_MS
            val inStart = outTotal + PLACEHOLDER_GAP_MS
            val inTotal = target.length * PLACEHOLDER_STAGGER_MS + PLACEHOLDER_IN_MS
            outDriver.snapTo(0f)
            inDriver.snapTo(0f)
            // 先让文字带上移一点再换字，两个动作别挤在同一帧
            delay(PLACEHOLDER_START_DELAY_MS.toLong())
            launch { outDriver.animateTo(1f, tween(durationMillis = outTotal, easing = LinearEasing)) }
            delay(inStart.toLong())
            inDriver.animateTo(1f, tween(durationMillis = inTotal, easing = LinearEasing))
            resting = target
        } finally {
            // 被取消时交给下一次 effect 接管，别在这里把它的状态抹掉
            if (isActive) {
                leaving = null
                morphing = false
            }
        }
    }

    Box(modifier = modifier) {
        leaving?.let { text ->
            PlaceholderChars(
                text = text,
                driver = outDriver,
                totalMillis = text.length * PLACEHOLDER_STAGGER_MS + PLACEHOLDER_OUT_MS,
                charMillis = PLACEHOLDER_OUT_MS,
                easing = PlaceholderOutEasing,
                outgoing = true,
            )
        }
        if (morphing) {
            PlaceholderChars(
                text = target,
                driver = inDriver,
                totalMillis = target.length * PLACEHOLDER_STAGGER_MS + PLACEHOLDER_IN_MS,
                charMillis = PLACEHOLDER_IN_MS,
                easing = PlaceholderInEasing,
                outgoing = false,
            )
        } else {
            Text(text = resting, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlaceholderChars(
    text: String,
    driver: Animatable<Float, AnimationVector1D>,
    totalMillis: Int,
    charMillis: Int,
    easing: Easing,
    outgoing: Boolean,
) {
    val density = LocalDensity.current
    val travel = with(density) { 9.dp.toPx() }
    val blurSteps = rememberPlaceholderBlur()
    Row(modifier = Modifier.fillMaxWidth()) {
        text.forEachIndexed { index, char ->
            val progress = {
                val elapsed = driver.value * totalMillis - index * PLACEHOLDER_STAGGER_MS
                easing.transform((elapsed / charMillis).coerceIn(0f, 1f))
            }
            Text(
                text = char.toString(),
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    val value = progress()
                    alpha = if (outgoing) 1f - value else value
                    translationY = if (outgoing) -travel * value else travel * (1f - value)
                    renderEffect = blurSteps.at(value)
                },
            )
        }
    }
}

/**
 * 逐字模糊用的几档 RenderEffect。
 *
 * 模糊半径逐帧现造会一直分配对象，这里预备几档、按进度取最近的一档；
 * API 31 以下没有 RenderEffect，直接给空列表，退化成纯位移淡入淡出。
 */
private class PlaceholderBlur(private val steps: List<RenderEffect?>) {
    fun at(progress: Float): RenderEffect? {
        if (steps.isEmpty()) return null
        val index = (progress * steps.lastIndex).roundToInt().coerceIn(0, steps.lastIndex)
        return steps[index]
    }
}

@Composable
private fun rememberPlaceholderBlur(): PlaceholderBlur {
    val density = LocalDensity.current
    return remember(density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val maxRadius = with(density) { 3.dp.toPx() }
            PlaceholderBlur(
                listOf<RenderEffect?>(null) + List(3) { step ->
                    val radius = maxRadius * (step + 1) / 3f
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
                }
            )
        } else {
            PlaceholderBlur(emptyList<RenderEffect?>())
        }
    }
}

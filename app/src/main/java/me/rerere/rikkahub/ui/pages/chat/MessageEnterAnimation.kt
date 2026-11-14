package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.modifier.rememberMotionBlurSteps
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import kotlin.uuid.Uuid

/** 一条消息从追加到真正被组合出来，允许的最大间隔；超过就说明当时不在可见区，不再补播。 */
private const val ENTER_WINDOW_MS = 800L

/**
 * 只认"追加在末尾"的那几条。
 *
 * 打开会话、翻页加载更早的历史、切换分支，都不会让列表尾部多出不认识的 id，
 * 这些情况下返回空，界面上就不会出现一排气泡同时往上滑。
 */
internal fun newAppendedMessageIds(previous: List<Uuid>, current: List<Uuid>): List<Uuid> {
    if (current.isEmpty()) return emptyList()
    // 上一帧还是空的、这一帧一次来了好几条：这是会话刚从库里读回来的整段历史。
    // 冷打开一个已有会话时，首帧拿到的是 Conversation.ofId 的空列表，随后整段历史一次性到位，
    // 不挡的话每条都会被当成"刚追加"，一屏旧消息一起从下面滑上来。
    // 空会话里新消息只会一条条进来，一次多条的只可能是历史。
    if (previous.isEmpty() && current.size > 1) return emptyList()
    val known = previous.toHashSet()
    // 一条都对不上：整段被换掉了（重新加载、导入、清空重来），不是"来了新消息"，
    // 否则会把一整页旧消息当成刚追加的，一起从下面滑上来
    if (known.isNotEmpty() && current.none { it in known }) return emptyList()
    var start = current.size
    while (start > 0 && current[start - 1] !in known) {
        start--
    }
    return current.subList(start, current.size).toList()
}

/**
 * 记住上一帧的 id 顺序，回答"这一帧尾部多了哪几条"。
 *
 * 用普通字段而不是 State：这个判断只在组合期读一次，写成 State 会多一次重组，
 * 多出来的那次恰好把刚算出来的结果清掉，气泡就永远滑不进来。
 * 因此 [track] 必须是幂等的——同一份 ids 连调两次，结果要一样。
 */
internal class MessageEnterTracker(
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private var previousIds: List<Uuid> = emptyList()
    private var hasFrame = false
    private var pending: Map<Uuid, Long> = emptyMap()

    /**
     * [animateAppends] 为 false 时只把这一帧记成已知，不产生入场：
     * 打开已有会话时首帧也是空列表，紧接着到位的那一批是历史，不是刚追加的。
     */
    fun track(ids: List<Uuid>, animateAppends: Boolean = true): Set<Uuid> {
        // 流式输出时每帧都会重组，id 序列没变就直接沿用上一帧的结论
        if (hasFrame && ids === previousIds) {
            return pending.keys
        }
        if (!hasFrame) {
            // 首帧是"打开会话"，整段历史一起出现，不播
            hasFrame = true
            previousIds = ids
            return emptySet()
        }
        val at = now()
        // 追加的时候 item 不在可见区（用户正在往上翻），超过窗口就作废，
        // 免得几分钟后滚到底时凭空冒出一段动画
        val alive = pending.filterValues { at - it <= ENTER_WINDOW_MS }
        val appended = if (animateAppends) newAppendedMessageIds(previousIds, ids) else emptyList()
        previousIds = ids
        pending = if (appended.isEmpty()) alive else appended.associateWith { at }
        return pending.keys
    }

    /** 动画播完就销账，滚出屏幕再滚回来时不重播。 */
    fun consume(id: Uuid) {
        if (id in pending) {
            pending = pending - id
        }
    }
}

/**
 * 新消息气泡的入场：从屏幕下方外部滑进最终位置。
 *
 * LazyColumn 自带的入场只有淡入，做不出位移，所以在 item 外层加一层 graphicsLayer，
 * 用一个从窗口高度出发的 Animatable 把整块内容推上来。只改绘制、不参与测量，
 * 列表该占的位置一开始就占好，动画过程中不会把上面的消息挤来挤去。
 */
@Composable
internal fun MessageEnterSlide(
    messageId: Uuid,
    animateEnter: Boolean,
    onEnterFinished: (Uuid) -> Unit,
    content: @Composable () -> Unit,
) {
    // animateEnter 每次重组都会重算（集合里的 id 播完就被摘掉），
    // 这里按 id 记一次，保证一条消息只在第一次组合时决定播不播。
    // 这条不变量也让下面两个 content() 分支永远不会互相切换，子树不会被重建。
    val animate = remember(messageId) { animateEnter }
    if (animate) {
        EnteringContent(
            messageId = messageId,
            onEnterFinished = onEnterFinished,
            content = content,
        )
    } else {
        content()
    }
}

@Composable
private fun EnteringContent(
    messageId: Uuid,
    onEnterFinished: (Uuid) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val displaySetting = LocalSettings.current.displaySetting
    // 起点取窗口高度，再乘上设置里的行程倍率：要的是"从屏幕外进来"，不是"从自己头顶掉下来"。
    // 32dp 记作 1.0（正好一整屏），调小就从更近的地方滑进来，调大则从更远处
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val fallbackHeight = with(density) { 720.dp.toPx() }
    val travelScale = displaySetting.motionBlur.travel / 32f
    val startOffset = (if (windowHeight > 0) windowHeight else fallbackHeight) * travelScale

    // 名字不要叫 alpha / offset：graphicsLayer 的作用域里有同名属性，裸名会解析到那边去
    val offsetAnim = remember(messageId) { Animatable(startOffset) }
    val alphaAnim = remember(messageId) { Animatable(0f) }
    // 整屏的位移速度很快，只做淡入看起来还是"凭空出现"；叠一层跟着位移一起收掉的模糊，
    // 气泡入场的这一段才读得出是从下面上来的。起点在屏幕外，糊满也看不见，随后和位移同步聚焦
    val blurSteps = rememberMotionBlurSteps(maxRadius = 6.dp)
    val blurAnim = remember(messageId) { Animatable(1f) }
    // 入场比普通收放长一点：起点在屏幕外，飞完整屏不能太急。
    // 时长从动态模糊设置里取，调「过渡时长」时入场跟着一起变；关了动效则直接落位
    val enterDuration = displaySetting.motionBlur.durationMillis
    val disableAnimations = displaySetting.disableAnimations
    val spatialSpec = remember(enterDuration, disableAnimations) {
        if (disableAnimations) {
            snap<Float>()
        } else {
            tween<Float>(
                durationMillis = (enterDuration * 1.8f).toInt().coerceAtLeast(1),
                easing = FastOutSlowInEasing,
            )
        }
    }
    val effectsSpec = rememberEffectsSpec<Float>()
    val finish = rememberUpdatedState(onEnterFinished)

    LaunchedEffect(messageId) {
        launch { alphaAnim.animateTo(1f, effectsSpec) }
        launch { blurAnim.animateTo(0f, spatialSpec) }
        offsetAnim.animateTo(0f, spatialSpec)
        finish.value(messageId)
    }

    // 动画没播完就被滚出屏幕时 item 会被回收，这里同样销账，滚回来不再重播
    DisposableEffect(messageId) {
        onDispose { finish.value(messageId) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = offsetAnim.value
                alpha = alphaAnim.value
                renderEffect = blurSteps.at(blurAnim.value)
            },
    ) {
        content()
    }
}

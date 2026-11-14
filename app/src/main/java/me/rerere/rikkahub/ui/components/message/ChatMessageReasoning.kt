package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.SelectableContent
import me.rerere.rikkahub.ui.components.ui.ChainOfThoughtProgress
import me.rerere.rikkahub.ui.components.ui.ChainOfThoughtScope
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.modifier.motionBlurOnToggle
import me.rerere.rikkahub.ui.modifier.rememberMotionBlurSteps
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.utils.extractLatestThinkingTitle
import me.rerere.rikkahub.utils.extractThinkingSummary
import me.rerere.rikkahub.utils.extractThinkingTitle
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.DurationUnit

enum class ReasoningCardState(val expanded: Boolean) {
    Collapsed(false),
    Preview(true),
    Expanded(true),
}

@Stable
private class ReasoningState(
    val scrollState: ScrollState,
    initialDuration: Duration,
) {
    var expandState by mutableStateOf(ReasoningCardState.Collapsed)
    var duration by mutableStateOf(initialDuration)

    fun onExpandedChange(nextExpanded: Boolean, loading: Boolean) {
        expandState = if (loading) {
            if (nextExpanded) ReasoningCardState.Expanded else ReasoningCardState.Preview
        } else {
            if (nextExpanded) ReasoningCardState.Expanded else ReasoningCardState.Collapsed
        }
    }
}

@Composable
private fun rememberReasoningState(reasoning: UIMessagePart.Reasoning): Pair<ReasoningState, Boolean> {
    val settings = LocalSettings.current
    val loading = reasoning.finishedAt == null
    val scrollState = rememberScrollState()

    val state = remember(reasoning.createdAt) {
        ReasoningState(
            scrollState = scrollState,
            initialDuration = reasoning.finishedAt?.let { it - reasoning.createdAt }
                ?: (Clock.System.now() - reasoning.createdAt)
        )
    }

    LaunchedEffect(reasoning.reasoning, loading) {
        if (loading) {
            if (!state.expandState.expanded && settings.displaySetting.showThinkingContent)
                state.expandState = ReasoningCardState.Preview
            scrollState.animateScrollTo(scrollState.maxValue)
        } else {
            if (state.expandState.expanded) {
                state.expandState = if (settings.displaySetting.autoCloseThinking)
                    ReasoningCardState.Collapsed
                else
                    ReasoningCardState.Expanded
            }
        }
    }

    LaunchedEffect(loading) {
        if (loading) {
            while (isActive) {
                state.duration = (reasoning.finishedAt ?: Clock.System.now()) - reasoning.createdAt
                delay(50)
            }
        }
    }

    return state to loading
}

@Composable
private fun ReasoningContent(
    reasoning: UIMessagePart.Reasoning,
    assistant: Assistant?,
    expandState: ReasoningCardState,
    scrollState: ScrollState,
    fadeHeight: Float,
    loading: Boolean,
) {
    val isPreview = expandState == ReasoningCardState.Preview
    val fontSizeRatio = LocalSettings.current.displaySetting.fontSizeRatio
    val bodySmall = MaterialTheme.typography.bodySmall
    val reasoningTextStyle = bodySmall.copy(
        fontSize = bodySmall.fontSize * fontSizeRatio,
        lineHeight = bodySmall.lineHeight * fontSizeRatio,
        fontFamily = LocalTextStyle.current.fontFamily,
    )
    val renderedReasoning = remember(reasoning.reasoning, assistant?.regexes) {
        reasoning.reasoning.replaceRegexes(
            assistant = assistant,
            scope = AssistantAffectScope.ASSISTANT,
            visual = true,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 预览高度与全文高度之间切换也走一次模糊，和折叠按钮的手感一致
            .motionBlurOnToggle(expandState)
            .let { contentModifier ->
                if (isPreview) {
                    contentModifier
                        .graphicsLayer { alpha = 0.99f }
                        .drawWithCache {
                            val brush = Brush.verticalGradient(
                                startY = 0f,
                                endY = size.height,
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    (fadeHeight / size.height) to Color.Black,
                                    (1 - fadeHeight / size.height) to Color.Black,
                                    1.0f to Color.Transparent
                                )
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(
                                    brush = brush,
                                    size = Size(size.width, size.height),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        }
                        .heightIn(max = 100.dp)
                        .verticalScroll(scrollState)
                } else {
                    contentModifier
                }
            }
    ) {
        val reasoningContent = @Composable {
            MarkdownBlock(
                content = renderedReasoning,
                style = reasoningTextStyle,
                streaming = loading,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Markdown 由 BetterText 渲染，要用它自己的选择容器；生成中也可以选择
        SelectableContent {
            reasoningContent()
        }
    }
}

@Composable
fun ChainOfThoughtScope.ChatMessageReasoningStep(
    reasoning: UIMessagePart.Reasoning,
    model: Model?,
    assistant: Assistant?,
    fadeHeight: Float = 64f,
    collapsedAdaptiveWidth: Boolean = false,
    // 这条消息是否还在生成：生成都结束了思考还没收尾，说明这一步失败了
    generating: Boolean = false,
) {
    val (state, loading) = rememberReasoningState(reasoning)
    // 没写完又不生成，只能是断在半路
    val failed = loading && !generating
    val settings = LocalSettings.current
    val summaryEnabled = settings.displaySetting.showThinkingSummary
    // 只解析一段窗口：流式输出时思考内容每帧都在变长，全文扫一遍太贵。
    // 生成中取最新写完的短句，标题跟着往下滚；结束后回到开头那句当摘要。
    val thinkingTitle = remember(reasoning.reasoning, summaryEnabled, loading) {
        when {
            !summaryEnabled -> null
            loading -> reasoning.reasoning.extractLatestThinkingTitle()
            else -> reasoning.reasoning.extractThinkingSummary()
        }
    }
    // 首句标题关掉后回到原版行为：流式过程中用整行加粗的标题
    val boldTitle = remember(reasoning.reasoning, summaryEnabled) {
        if (summaryEnabled) null else reasoning.reasoning.extractThinkingTitle()
    }
    val showBoldTitle = loading && boldTitle != null
    val chatFontFamily = LocalTextStyle.current.fontFamily

    ControlledChainOfThoughtStep(
        expanded = state.expandState == ReasoningCardState.Expanded,
        onExpandedChange = { state.onExpandedChange(it, loading) },
        icon = {
            Icon(
                imageVector = HugeIcons.Idea01,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        label = {
            when {
                thinkingTitle != null -> ReasoningTitleRow(title = thinkingTitle, loading = loading)

                showBoldTitle -> ReasoningTitle(title = boldTitle!!, loading = true)

                else -> Text(
                    text = stringResource(R.string.deep_thinking),
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = chatFontFamily),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.shimmer(isLoading = loading),
                )
            }
        },
        progress = ChainOfThoughtProgress(
            running = loading,
            failed = failed,
            label = {
                Text(
                    text = state.duration.toString(DurationUnit.SECONDS, 1),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = chatFontFamily),
                    color = if (failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .widthIn(min = 40.dp)
                        .shimmer(isLoading = loading),
                )
            },
        ),
        collapsedAdaptiveWidth = collapsedAdaptiveWidth,
        contentVisible = state.expandState != ReasoningCardState.Collapsed,
        content = {
            ReasoningContent(
                reasoning = reasoning,
                assistant = assistant,
                expandState = state.expandState,
                scrollState = state.scrollState,
                fadeHeight = fadeHeight,
                loading = loading,
            )
        },
    )
}

/** 标题滚动切换用多长 */
private const val TITLE_ROLL_MILLIS = 420

/**
 * 思考卡标题行：「深度思考 · 」这个前缀固定，后面接一段会滚动的短句。
 */
@Composable
private fun ReasoningTitleRow(title: String, loading: Boolean) {
    val chatFontFamily = LocalTextStyle.current.fontFamily
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.deep_thinking) + " · ",
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = chatFontFamily),
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            modifier = Modifier.shimmer(isLoading = loading),
        )
        ReasoningTitle(
            title = title,
            loading = loading,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * 会滚动的标题：换一句时旧句向上滚出、新句从下方滚进来，两头各带一点模糊，
 * 像一行字被快速卷过去。
 *
 * 两条文字分开动画：旧句独立于新句的高度，换句过程中宽度变化交给
 * [animateContentSize] 慢慢收放，不会把旧句当场裁掉。
 */
@Composable
private fun ReasoningTitle(
    title: String,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val chatFontFamily = LocalTextStyle.current.fontFamily
    val style = MaterialTheme.typography.titleSmall.copy(fontFamily = chatFontFamily)
    val color = MaterialTheme.colorScheme.secondary
    val travel = with(LocalDensity.current) { 10.dp.toPx() }
    val blur = rememberMotionBlurSteps(maxRadius = 3.dp, steps = 3)

    // 正在显示的标题和正在退场的标题分开存；中途又来了新句子就直接换，不排队
    var shown by remember { mutableStateOf(title) }
    var leaving by remember { mutableStateOf<String?>(null) }
    val roll = remember { Animatable(1f) }

    LaunchedEffect(title) {
        if (title == shown) return@LaunchedEffect
        roll.snapTo(0f)
        leaving = shown
        shown = title
        roll.animateTo(1f, tween(TITLE_ROLL_MILLIS, easing = FastOutSlowInEasing))
        leaving = null
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .animateContentSize(tween(TITLE_ROLL_MILLIS, easing = FastOutSlowInEasing)),
    ) {
        Text(
            text = shown,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .shimmer(isLoading = loading)
                .graphicsLayer {
                    val progress = roll.value
                    alpha = progress
                    translationY = travel * (1f - progress)
                    renderEffect = blur.at(1f - progress)
                },
        )
        leaving?.let { old ->
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = old,
                    style = style,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        val progress = roll.value
                        alpha = 1f - progress
                        translationY = -travel * progress
                        renderEffect = blur.at(progress)
                    },
                )
            }
        }
    }
}

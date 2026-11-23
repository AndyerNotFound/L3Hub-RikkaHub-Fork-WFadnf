package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import me.rerere.rikkahub.ui.theme.rememberFixedSpec
import me.rerere.rikkahub.ui.theme.rememberSlideSpec

/**
 * 单行输入框从一行展开到两行用的曲线。
 *
 * 和设计稿一致：起手快、收尾慢，文字带上移和胶囊滑入共用同一条节奏，
 * 只是靠 delay 错峰。
 */
internal val ChatInputExpandEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** 模型选择胶囊：展开时贴着左侧按钮组滑出，收起时原路退回；单行布局里兼任思考强度入口 */
@Composable
internal fun ChatInputModelPill(
    model: Model?,
    reasoningLevel: ReasoningLevel?,
    useL3HubReasoningStyle: Boolean,
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val slideDistance = with(density) { 12.dp.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        // 进出同参：收起不再先快一截，也没有只在进场存在的错峰延迟
        enter = fadeIn(rememberFixedSpec<Float>(200)) +
            slideInHorizontally(
                animationSpec = rememberFixedSpec<IntOffset>(380, ChatInputExpandEasing),
                initialOffsetX = { -slideDistance },
            ),
        exit = fadeOut(rememberFixedSpec<Float>(200)) +
            slideOutHorizontally(
                animationSpec = rememberFixedSpec<IntOffset>(380, ChatInputExpandEasing),
                targetOffsetX = { -slideDistance },
            ),
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .height(30.dp)
                .widthIn(max = 148.dp),
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
                if (reasoningLevel != null) {
                    // 合并后的思考强度仍然要看得到：文字风格跟思考按钮保持一套
                    if (useL3HubReasoningStyle) {
                        Text(
                            text = reasoningLevel.badgeText(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                lineHeight = 10.sp,
                            ),
                            color = reasoningLevel.indicatorColor(),
                            maxLines = 1,
                        )
                    } else {
                        Icon(
                            imageVector = reasoningLevel.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = reasoningLevel.indicatorColor(),
                        )
                    }
                }
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

/**
 * 输入框占位文案的切换：旧文案向下滑走、新文案自下方滑入，fade+scale 一起收。
 *
 * 逐字渐变那套在文案长度变化时宽度会跳、动画被打断还容易停在半透明的中间态，
 * 换成与消息折叠同一套 slide+fade+scale（ImageToolbox 同款曲线），进出同参同档；
 * 动画结束后退回普通 [Text]，不给静止状态留额外布局节点。
 */
@Composable
internal fun MorphingPlaceholder(
    target: String,
    modifier: Modifier = Modifier,
) {
    val slideSpec = rememberSlideSpec<IntOffset>()
    val scaleSpec = rememberSlideSpec<Float>()
    val fadeSpec = rememberEffectsSpec<Float>()
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically(slideSpec) { it / 2 } +
                fadeIn(fadeSpec) +
                scaleIn(scaleSpec, initialScale = 0.96f)) togetherWith
                (slideOutVertically(slideSpec) { it / 2 } +
                    fadeOut(fadeSpec) +
                    scaleOut(scaleSpec, targetScale = 0.96f))
        },
        label = "inputPlaceholder",
    ) { text ->
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

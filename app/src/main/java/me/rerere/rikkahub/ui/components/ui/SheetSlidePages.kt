package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import me.rerere.rikkahub.ui.theme.motionContentSize
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec

/**
 * 同一个面板内的两页互切。
 *
 * 两页叠在同一块区域上，各自横向滑入滑出，容器高度跟着内容走：前进时新页从右侧进、
 * 旧页向左退，返回沿原路反向。进退共用同一套动效曲线，避免出现单向硬过渡。
 *
 * 页面切换不关闭面板，只换内容，所以调用方把两页都交给这里，由 [showSecond] 决定当前页。
 */
@Composable
fun SheetSlidePages(
    showSecond: Boolean,
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    Box(modifier = modifier.motionContentSize().clipToBounds()) {
        AnimatedVisibility(
            visible = !showSecond,
            enter = slideInHorizontally(rememberSpatialSpec<IntOffset>()) { -it } +
                fadeIn(rememberEffectsSpec<Float>()),
            exit = slideOutHorizontally(rememberSpatialSpec<IntOffset>()) { -it } +
                fadeOut(rememberEffectsSpec<Float>()),
        ) {
            first()
        }
        AnimatedVisibility(
            visible = showSecond,
            enter = slideInHorizontally(rememberSpatialSpec<IntOffset>()) { it } +
                fadeIn(rememberEffectsSpec<Float>()),
            exit = slideOutHorizontally(rememberSpatialSpec<IntOffset>()) { it } +
                fadeOut(rememberEffectsSpec<Float>()),
        ) {
            second()
        }
    }
}

/**
 * 同一个面板内的多级页面栈。
 *
 * 与 [SheetSlidePages] 同一套动效：往下一级时新页从右侧进、旧页向左退，退回上一级沿原路
 * 反向；[depthOf] 用来比较两页的层级，决定往哪个方向滑。
 */
@Composable
fun <T> SheetSlideStackPages(
    page: T,
    depthOf: (T) -> Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    // transitionSpec 的 lambda 不是 @Composable 上下文，曲线要在外面先取好
    val spatialSpec = rememberSpatialSpec<IntOffset>()
    val effectsSpec = rememberEffectsSpec<Float>()
    AnimatedContent(
        targetState = page,
        modifier = modifier.motionContentSize().clipToBounds(),
        transitionSpec = {
            val direction = if (depthOf(targetState) > depthOf(initialState)) 1 else -1
            slideInHorizontally(spatialSpec) { it * direction } +
                fadeIn(effectsSpec) togetherWith
                slideOutHorizontally(spatialSpec) { it * direction } +
                fadeOut(effectsSpec)
        },
        label = "SheetSlideStackPages",
    ) { current ->
        content(current)
    }
}

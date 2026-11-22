package me.rerere.rikkahub.ui.navigation

import androidx.compose.animation.core.tween
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.animation.StackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.androidPredictiveBackAnimatableV1
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.essenty.backhandler.BackHandler
import me.rerere.rikkahub.ui.theme.AlphaEasing
import me.rerere.rikkahub.ui.theme.FancyTransitionEasing
import me.rerere.rikkahub.ui.theme.PointToPointEasing

/**
 * 页面转场引擎，ImageToolbox 的 `toolboxPredictiveBackAnimation` 一比一：
 *
 * - 预测性返回手势进行中由 decompose 直接吃进度（系统 V1 手感），松手完成/取消后交给 fallback；
 * - fallback 是 decompose 内置 stackAnimation 的 fade + slide + scale 三件套，
 *   曲线取 MotionEasing 的三条基线：透明度 Alpha、位移 FancyTransition（尾段轻微过冲）、
 *   变形 PointToPoint。时长由调用侧传「动画速度」档位，三条同时开始、同时结束。
 *
 * 这里没有任何逐页簿记和全局前进/返回分类：转场打断、快速连续切换全部由
 * decompose 内置实现处理，行为与 ImageToolbox 完全一致。
 */
@OptIn(ExperimentalDecomposeApi::class)
fun <C : Any, T : Any> l3HubStackAnimation(
    backHandler: BackHandler,
    onBack: () -> Unit,
    durationMillis: Int = 300,
): StackAnimation<C, T> = predictiveBackAnimation(
    backHandler = backHandler,
    onBack = onBack,
    fallbackAnimation = stackAnimation(
        fade(tween(durationMillis, easing = AlphaEasing)) +
            slide(tween(durationMillis, easing = FancyTransitionEasing)) +
            scale(tween(durationMillis, easing = PointToPointEasing)),
    ),
    selector = { backEvent, _, _ -> androidPredictiveBackAnimatableV1(backEvent) },
)

/**
 * 设置右列详情栈用：同一套 fade + slide + scale，但不接预测性返回——
 * 右列的返回键由 RouteActivity 的 BackCallback 接管，手势进度不走栈动画。
 */
@OptIn(ExperimentalDecomposeApi::class)
fun <C : Any, T : Any> l3HubDetailAnimation(
    durationMillis: Int = 300,
): StackAnimation<C, T> = stackAnimation(
    fade(tween(durationMillis, easing = AlphaEasing)) +
        slide(tween(durationMillis, easing = FancyTransitionEasing)) +
        scale(tween(durationMillis, easing = PointToPointEasing)),
)

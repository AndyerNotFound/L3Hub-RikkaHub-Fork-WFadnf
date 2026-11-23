package me.rerere.rikkahub.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * 曲线组，取自 ImageToolbox（T8RIN，Apache-2.0）。
 *
 * 原工程把这几条当作全局手感基线：位移用 [FancyTransitionEasing]（尾段 3% 过冲，
 * 落位时"顿"一下再停），淡入淡出用 [AlphaEasing]，容器变形用 [PointToPointEasing]。
 * 这里只搬曲线与前缀命名，取用仍然走 [MotionSpecs] 的档位层——
 * 曲线管手感，档位管快慢，两者不互相覆盖。
 *
 * | 曲线 | 用途 |
 * |---|---|
 * | [FancyTransitionEasing] | 页面滑入滑出、容器展开，尾段轻微过冲 |
 * | [AlphaEasing] | 透明度、遮罩、模糊半径 |
 * | [PointToPointEasing] | 两点之间的变形（共享元素、缩放落位） |
 * | [FastInvokeEasing] | 立即响应的直线位移（跟手、拖拽） |
 * | [OverslideEasing] | 有意过冲的越界回弹（越界回弹指示） |
 * | [RotationEasing] | 旋转（加载、自转图标），首尾对称 |
 * | [SoftEasing] | 长距离缓入缓出（抽屉、大面板） |
 * | [EaseOutCubicEasing] | Kelivo 的进入 / 展开 / 按压（Flutter `Curves.easeOutCubic`） |
 * | [EaseInCubicEasing] | Kelivo 的退出 / 收起，与上一条镜像配对（Flutter `Curves.easeInCubic`） |
 *
 * 不要在新代码里直接写 `CubicBezierEasing(...)`：要么用这里的常量，
 * 要么用 [MotionSpecs] 里带档位的 `remember*Spec`。
 */
val FancyTransitionEasing: Easing = CubicBezierEasing(0.48f, 0.19f, 0.05f, 1.03f)

val AlphaEasing: Easing = CubicBezierEasing(0.4f, 0.4f, 0.17f, 0.9f)

val PointToPointEasing: Easing = CubicBezierEasing(0.55f, 0.55f, 0f, 1f)

val FastInvokeEasing: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

val OverslideEasing: Easing = CubicBezierEasing(0.5f, 0.5f, 1f, 0.25f)

val RotationEasing: Easing = CubicBezierEasing(0.46f, 0.03f, 0.52f, 0.96f)

val SoftEasing: Easing = CubicBezierEasing(0.46f, 0.41f, 0.29f, 0.92f)

/**
 * Kelivo 的进入 / 展开 / 按压曲线：Flutter `Curves.easeOutCubic` 的原参数
 * `Cubic(0.215, 0.61, 0.355, 1.0)`（docs/kelivo-design-spec.md B.2：全库 392 处，事实上的唯一标准）。
 *
 * 与 [FancyTransitionEasing] 的区别是**没有过冲**：Kelivo 的位移是「快起、稳稳落位」，
 * 落位时不再回弹一下。页面、面板、展开、按压反馈都用它。
 */
val EaseOutCubicEasing: Easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/**
 * Kelivo 的退出 / 收起曲线：Flutter `Curves.easeInCubic` 的原参数
 * `Cubic(0.55, 0.055, 0.675, 0.19)`（B.2：27 处，只出现在 `reverseCurve`）。
 *
 * 与 [EaseOutCubicEasing] **镜像配对**：进场慢收、退场快走，这是 Kelivo 手写 route 的固定写法
 * （进 300ms / 退 240ms，见 docs/kelivo-migration-plan.md 决策 1）。
 */
val EaseInCubicEasing: Easing = CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f)

package me.rerere.rikkahub.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.CornerStyle
import me.rerere.rikkahub.ui.shape.styledShape

/**
 * 「全宽组件」显示设置（`DisplaySetting.fullWidthComponents`）。
 *
 * 打开后组件的左右边缘贴到父容器（屏幕）边，圆角一律收成直角。
 * 由 [RikkahubTheme] 统一提供；没有主题包裹的地方（比如 Preview）取默认值 false，
 * 所有读这个开关的地方都会退回改造前的取值。
 */
val LocalFullWidthComponents = compositionLocalOf { false }

/** 全宽模式下 Material 组件的统一形状：直角。 */
val SquareShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

/** 全宽模式下圆角一律为 0，否则用传入的半径。 */
@Composable
@ReadOnlyComposable
fun adaptiveCornerRadius(radius: Dp): Dp {
    // 动画（弹簧）会过冲，Dp 可能瞬时为负；RoundedCornerShape 见到负值直接抛异常
    if (LocalFullWidthComponents.current) return 0.dp
    return radius.coerceAtLeast(0.dp)
}

/** 组件轮廓类型，由 [RikkahubTheme] 从显示设置写入；没有主题包裹时是普通圆角。 */
val LocalCornerStyle = compositionLocalOf { CornerStyle.ROUNDED }

/** 边角大小倍率（显示设置里的「边角大小」，1f 为设计值）。 */
val LocalCornerScale = compositionLocalOf { 1f }

/** 按压轮廓变形的节流窗口（毫秒），对应显示设置里的「边角动画节流」。 */
val LocalCornerThrottle = compositionLocalOf { 128 }

/**
 * 全宽模式下是直角，否则按当前轮廓类型与边角大小造形状。
 *
 * 返回 `Shape` 而不是 `RoundedCornerShape`：选了切角 / 连续圆角 / 波浪边以后，
 * 这里给出的既不是圆角也不是 `RoundedCornerShape` 能表示的东西。调用点一律当 `Shape` 用。
 */
@Composable
@ReadOnlyComposable
fun adaptiveShape(radius: Dp): Shape {
    if (LocalFullWidthComponents.current) return SquareShapes.small
    return styledShape(
        style = LocalCornerStyle.current,
        radius = CornerSize(radius.coerceAtLeast(0.dp)),
        scale = LocalCornerScale.current,
    )
}

/**
 * 上下两段圆角不同的卡片分组：按当前轮廓类型造逐角形状。
 *
 * 参数是「上边的半径」和「下边的半径」两个值（分组首尾项取大圆角、中间项取小圆角），
 * 不是四个角各给一个——现有调用点只按上下两段区分。
 */
@Composable
@ReadOnlyComposable
fun adaptiveStyledShape(topRadius: Dp, bottomRadius: Dp): Shape {
    val top = topRadius.coerceAtLeast(0.dp)
    val bottom = bottomRadius.coerceAtLeast(0.dp)
    if (LocalFullWidthComponents.current) return SquareShapes.small
    return styledShape(
        style = LocalCornerStyle.current,
        topStart = CornerSize(top),
        topEnd = CornerSize(top),
        bottomEnd = CornerSize(bottom),
        bottomStart = CornerSize(bottom),
        scale = LocalCornerScale.current,
    )
}

/** 全宽模式下返回 0（贴父容器边缘），否则返回 [padding]。 */
@Composable
@ReadOnlyComposable
fun adaptiveHorizontalPaddingValue(padding: Dp): Dp =
    if (LocalFullWidthComponents.current) 0.dp else padding

/**
 * 全宽模式下贴到左右屏幕边，只额外让出刘海的宽度，不会被挖孔屏盖住。
 *
 * 侧边系统栏（横屏三键导航）本来由外层的 `navigationBarsPadding()` / `Scaffold` 的
 * contentPadding 负责，这里不再重复一遍，否则横屏会多出一倍的留白。
 * 非全宽时保持原来的固定留白。
 */
@Composable
fun Modifier.adaptiveHorizontalPadding(horizontal: Dp): Modifier =
    if (LocalFullWidthComponents.current) {
        windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
    } else {
        padding(horizontal = horizontal)
    }

/**
 * 全宽模式下的左右安全区宽度（刘海 / 侧边系统栏），用于内容的内边距而不是 Modifier 的场景。
 * 非全宽时为 0，调用方原来的留白不受影响。
 */
@Composable
fun fullWidthHorizontalInset(): Dp {
    if (!LocalFullWidthComponents.current) return 0.dp
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val start = insets.getLeft(density, layoutDirection)
    val end = insets.getRight(density, layoutDirection)
    return with(density) { maxOf(start, end).toDp() }
}

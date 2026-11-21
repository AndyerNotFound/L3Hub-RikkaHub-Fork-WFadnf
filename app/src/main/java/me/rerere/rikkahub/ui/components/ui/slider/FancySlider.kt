// Ported from ImageToolbox (https://github.com/T8RIN/ImageToolbox), Apache-2.0, Copyright (c) T8RIN (Malik Mukhametzyanov).
/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

package me.rerere.rikkahub.ui.components.ui.slider

import android.graphics.Matrix
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.SliderColors
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun FancySlider(
    value: Float,
    enabled: Boolean,
    colors: SliderColors,
    interactionSource: MutableInteractionSource,
    thumbShape: Shape,
    modifier: Modifier,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)?,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    drawContainer: Boolean = true
) {
    val thumbColor by animateColorAsState(
        if (enabled) colors.thumbColor else colors.disabledThumbColor
    )

    val thumb: @Composable (CustomSliderState) -> Unit = { sliderState ->
        val sliderFraction by remember(value, sliderState) {
            derivedStateOf {
                (value - sliderState.valueRange.start) /
                    (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
            }
        }
        Spacer(
            Modifier
                .zIndex(100f)
                .rotate(1080f * sliderFraction)
                .size(26.dp)
                .indication(
                    interactionSource = interactionSource,
                    indication = ripple(
                        bounded = false,
                        radius = 24.dp
                    )
                )
                .hoverable(interactionSource = interactionSource)
                // 源工程这里还有一层阴影修饰符（抬起 1dp，开关来自全局设置 drawSliderShadows），
                // 本仓库删掉阴影。
                .background(thumbColor, thumbShape)
        )
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        CustomSlider(
            interactionSource = interactionSource,
            thumb = thumb,
            enabled = enabled,
            // 源工程这里用一层圆形容器修饰符包住了底、描边与阴影（阴影开关来自全局设置
            // drawSliderShadows）；删掉那层包装，只留下它内侧的横向 padding。
            modifier = modifier
                .then(
                    if (drawContainer) {
                        Modifier
                            .padding(horizontal = 6.dp)
                    } else Modifier
                ),
            colors = colors.toCustom(),
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            track = { sliderState ->
                FancyTrack(
                    sliderState = sliderState,
                    colors = colors.toCustom(),
                    trackHeight = 38.dp,
                    enabled = enabled
                )
            }
        )
    }
}

@Stable
internal fun SliderColors.toCustom(): CustomSliderColors = CustomSliderColors(
    thumbColor = thumbColor,
    activeTrackColor = activeTrackColor,
    activeTickColor = activeTickColor,
    inactiveTrackColor = inactiveTrackColor,
    inactiveTickColor = inactiveTickColor,
    disabledThumbColor = disabledThumbColor,
    disabledActiveTrackColor = disabledActiveTrackColor,
    disabledActiveTickColor = disabledActiveTickColor,
    disabledInactiveTrackColor = disabledInactiveTrackColor,
    disabledInactiveTickColor = disabledInactiveTickColor
)

@Composable
internal fun fancyThumbShape(): Shape {
    // 源工程从全局设置读形状类型 shapesType：Cut / Scoop / Notch 走
    // AutoCircleShape(strength.coerceAtLeast(0.5f))，其余走 MaterialStarShape。
    // 这里用常量，冻结成默认值 ShapeType.Rounded()，落在 MaterialStarShape 这一支。
    return MaterialStarShape
}

/**
 * 源工程 core/resources/shapes/MaterialStarShape.kt，原样搬进来：
 * [fancyThumbShape] 的默认分支就是它，换成圆会让 Fancy 拇指变形，所以不替换成 CircleShape。
 */
internal val MaterialStarShape: Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val baseWidth = 865.0807f
        val baseHeight = 865.0807f

        val path = Path().apply {
            moveTo(403.3913f, 8.7356f)
            cubicTo(421.0787f, -2.9119f, 444.002f, -2.9119f, 461.6894f, 8.7356f)
            lineTo(518.743f, 46.3066f)
            cubicTo(528.2839f, 52.5895f, 539.5995f, 55.6215f, 551.0036f, 54.9508f)
            lineTo(619.1989f, 50.9402f)
            cubicTo(640.3404f, 49.6968f, 660.1926f, 61.1585f, 669.6865f, 80.0892f)
            lineTo(700.3109f, 141.1534f)
            cubicTo(705.4321f, 151.365f, 713.7157f, 159.6486f, 723.9273f, 164.7699f)
            lineTo(784.9915f, 195.3942f)
            cubicTo(803.9222f, 204.8881f, 815.3839f, 224.7403f, 814.1406f, 245.8818f)
            lineTo(810.1299f, 314.0771f)
            cubicTo(809.4593f, 325.4812f, 812.4913f, 336.7969f, 818.7742f, 346.3378f)
            lineTo(856.3451f, 403.3913f)
            cubicTo(867.9926f, 421.0787f, 867.9927f, 444.002f, 856.3452f, 461.6894f)
            lineTo(818.7742f, 518.743f)
            cubicTo(812.4913f, 528.2839f, 809.4593f, 539.5995f, 810.1299f, 551.0036f)
            lineTo(814.1406f, 619.1989f)
            cubicTo(815.3839f, 640.3404f, 803.9223f, 660.1926f, 784.9916f, 669.6865f)
            lineTo(723.9274f, 700.3109f)
            cubicTo(713.7158f, 705.4321f, 705.4321f, 713.7157f, 700.3109f, 723.9273f)
            lineTo(669.6866f, 784.9915f)
            cubicTo(660.1926f, 803.9222f, 640.3404f, 815.3839f, 619.1989f, 814.1406f)
            lineTo(551.0036f, 810.1299f)
            cubicTo(539.5995f, 809.4593f, 528.2839f, 812.4913f, 518.743f, 818.7742f)
            lineTo(461.6894f, 856.3451f)
            cubicTo(444.0021f, 867.9926f, 421.0787f, 867.9927f, 403.3914f, 856.3452f)
            lineTo(346.3378f, 818.7742f)
            cubicTo(336.7969f, 812.4913f, 325.4812f, 809.4593f, 314.0771f, 810.1299f)
            lineTo(245.8818f, 814.1406f)
            cubicTo(224.7404f, 815.3839f, 204.8882f, 803.9223f, 195.3942f, 784.9916f)
            lineTo(164.7699f, 723.9274f)
            cubicTo(159.6486f, 713.7158f, 151.365f, 705.4321f, 141.1534f, 700.3109f)
            lineTo(80.0892f, 669.6866f)
            cubicTo(61.1585f, 660.1926f, 49.6968f, 640.3404f, 50.9402f, 619.199f)
            lineTo(54.9508f, 551.0036f)
            cubicTo(55.6215f, 539.5995f, 52.5895f, 528.2839f, 46.3066f, 518.743f)
            lineTo(8.7356f, 461.6894f)
            cubicTo(-2.9119f, 444.0021f, -2.9119f, 421.0787f, 8.7356f, 403.3914f)
            lineTo(46.3066f, 346.3378f)
            cubicTo(52.5895f, 336.7969f, 55.6215f, 325.4813f, 54.9508f, 314.0771f)
            lineTo(50.9402f, 245.8818f)
            cubicTo(49.6968f, 224.7404f, 61.1585f, 204.8882f, 80.0892f, 195.3942f)
            lineTo(141.1534f, 164.7699f)
            cubicTo(151.365f, 159.6486f, 159.6486f, 151.365f, 164.7699f, 141.1534f)
            lineTo(195.3942f, 80.0892f)
            cubicTo(204.8882f, 61.1585f, 224.7403f, 49.6968f, 245.8818f, 50.9402f)
            lineTo(314.0771f, 54.9508f)
            cubicTo(325.4813f, 55.6215f, 336.7969f, 52.5895f, 346.3378f, 46.3066f)
            lineTo(403.3913f, 8.7356f)
            close()
        }

        return Outline.Generic(
            path
                .asAndroidPath()
                .apply {
                    transform(
                        Matrix().apply {
                            setScale(size.width / baseWidth, size.height / baseHeight)
                        }
                    )
                }
                .asComposePath()
        )
    }
}

@Composable
private fun FancyTrack(
    sliderState: CustomSliderState,
    colors: CustomSliderColors,
    enabled: Boolean,
    trackHeight: Dp
) {
    FancyTrack(
        tickFractions = sliderState.tickFractions,
        activeRangeStart = 0f,
        activeRangeEnd = sliderState.coercedValueAsFraction,
        colors = colors,
        enabled = enabled,
        trackHeight = trackHeight
    )
}

@Composable
private fun FancyTrack(
    tickFractions: FloatArray,
    activeRangeStart: Float,
    activeRangeEnd: Float,
    colors: CustomSliderColors,
    enabled: Boolean,
    trackHeight: Dp,
    shape: Shape = CircleShape
) {
    val inactiveTrackColor = colors.trackColor(enabled, active = false)
    val activeTrackColor = colors.trackColor(enabled, active = true)
    val inactiveTickColor = colors.tickColor(enabled, active = false)
    val activeTickColor = colors.tickColor(enabled, active = true)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(trackHeight)
    ) {
        drawFancyTrack(
            tickFractions = tickFractions,
            activeRangeStart = activeRangeStart,
            activeRangeEnd = activeRangeEnd,
            inactiveTrackColor = inactiveTrackColor,
            activeTrackColor = activeTrackColor,
            inactiveTickColor = inactiveTickColor,
            activeTickColor = activeTickColor,
            shape = shape
        )
    }
}

private fun DrawScope.drawFancyTrack(
    tickFractions: FloatArray,
    activeRangeStart: Float,
    activeRangeEnd: Float,
    inactiveTrackColor: Color,
    activeTrackColor: Color,
    inactiveTickColor: Color,
    activeTickColor: Color,
    shape: Shape
) {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val activeStart = if (isRtl) 1f - activeRangeEnd else activeRangeStart
    val activeEnd = if (isRtl) 1f - activeRangeStart else activeRangeEnd
    val tickSize = TickSize.toPx()

    drawFancyTrackSegment(
        startFraction = 0f,
        endFraction = 1f,
        color = inactiveTrackColor,
        shape = shape
    )
    drawFancyTrackSegment(
        startFraction = activeStart,
        endFraction = activeEnd,
        color = activeTrackColor,
        shape = shape
    )

    for (tick in tickFractions) {
        val outsideFraction = tick !in activeRangeStart..activeRangeEnd
        val visualTick = if (isRtl) 1f - tick else tick
        drawCircle(
            color = if (outsideFraction) inactiveTickColor else activeTickColor,
            center = Offset(size.width * visualTick, center.y),
            radius = tickSize / 2f
        )
    }
}

private fun DrawScope.drawFancyTrackSegment(
    startFraction: Float,
    endFraction: Float,
    color: Color,
    shape: Shape
) {
    val capRadius = size.height / 2f
    val left = size.width * startFraction.coerceIn(0f, 1f) - capRadius
    val right = size.width * endFraction.coerceIn(0f, 1f) + capRadius
    val width = right - left

    if (width <= 0f) return

    val outline = shape.createOutline(
        size = Size(
            width = width,
            height = size.height
        ),
        layoutDirection = layoutDirection,
        density = this
    )

    translate(left = left) {
        drawFancyTrackOutline(outline, color)
    }
}

private fun DrawScope.drawFancyTrackOutline(
    outline: Outline,
    color: Color
) {
    when (outline) {
        is Outline.Rectangle -> drawRect(
            color = color,
            topLeft = outline.rect.topLeft,
            size = outline.rect.size
        )

        is Outline.Rounded -> drawPath(
            path = Path().apply { addRoundRect(outline.roundRect) },
            color = color
        )

        is Outline.Generic -> drawPath(
            path = outline.path,
            color = color
        )
    }
}

private val TickSize = 2.dp

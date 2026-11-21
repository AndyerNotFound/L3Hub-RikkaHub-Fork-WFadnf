// Ported from ImageToolbox (https://github.com/T8RIN/ImageToolbox), Apache-2.0, Copyright (c) T8RIN (Malik Mukhametzyanov).
package me.rerere.rikkahub.ui.shape

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.capsule.Continuity
import me.rerere.rikkahub.data.datastore.CornerStyle
import sv.lib.squircleshape.CornerSmoothing
import sv.lib.squircleshape.SquircleShape

/** 同半径的四角形状。 */
@Stable
fun styledShape(style: CornerStyle, radius: CornerSize, scale: Float = 1f): CornerBasedShape =
    styledShape(
        style = style,
        topStart = radius,
        topEnd = radius,
        bottomEnd = radius,
        bottomStart = radius,
        scale = scale,
    )

/** 同半径的四角形状。 */
@Stable
fun styledShape(style: CornerStyle, radius: Dp, scale: Float = 1f): CornerBasedShape =
    styledShape(style = style, radius = CornerSize(radius), scale = scale)

/** 同半径的四角形状。 */
@Stable
fun styledShape(style: CornerStyle, radius: Float, scale: Float = 1f): CornerBasedShape =
    styledShape(style = style, radius = CornerSize(radius), scale = scale)

/** 逐角形状（上下两段圆角不同的卡片分组用）。 */
@Stable
fun styledShape(
    style: CornerStyle,
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    scale: Float = 1f,
): CornerBasedShape {
    val strength = scale * style.scaleRatio
    if (strength <= 0f) return RoundedCornerShape(0.dp)

    val scaledTopStart = topStart.scaledBy(strength)
    val scaledTopEnd = topEnd.scaledBy(strength)
    val scaledBottomEnd = bottomEnd.scaledBy(strength)
    val scaledBottomStart = bottomStart.scaledBy(strength)

    return when (style) {
        CornerStyle.ROUNDED -> RoundedCornerShape(
            topStart = scaledTopStart,
            topEnd = scaledTopEnd,
            bottomEnd = scaledBottomEnd,
            bottomStart = scaledBottomStart,
        )

        CornerStyle.CUT -> CutCornerShape(
            topStart = scaledTopStart,
            topEnd = scaledTopEnd,
            bottomEnd = scaledBottomEnd,
            bottomStart = scaledBottomStart,
        )

        CornerStyle.SMOOTH -> ContinuousRoundedRectangle(
            topStart = scaledTopStart,
            topEnd = scaledTopEnd,
            bottomEnd = scaledBottomEnd,
            bottomStart = scaledBottomStart,
            continuity = Continuity.Default,
        )

        CornerStyle.SQUIRCLE -> SquircleShape(
            topStartCorner = scaledTopStart,
            topEndCorner = scaledTopEnd,
            bottomEndCorner = scaledBottomEnd,
            bottomStartCorner = scaledBottomStart,
            smoothing = CornerSmoothing.Medium,
        )

        CornerStyle.WAVY -> WavyShape(
            topStart = scaledTopStart,
            topEnd = scaledTopEnd,
            bottomEnd = scaledBottomEnd,
            bottomStart = scaledBottomStart,
        )

        CornerStyle.SCOOP -> ScoopShape(
            topStart = scaledTopStart,
            topEnd = scaledTopEnd,
            bottomEnd = scaledBottomEnd,
            bottomStart = scaledBottomStart,
        )

        CornerStyle.NOTCH -> NotchShape(
            topStart = scaledTopStart,
            topEnd = scaledTopEnd,
            bottomEnd = scaledBottomEnd,
            bottomStart = scaledBottomStart,
        )
    }
}

@Stable
private fun CornerSize.scaledBy(strength: Float): CornerSize = when {
    strength == 1f -> this
    this is ScaledCornerSize -> copy(strength = strength)
    else -> ScaledCornerSize(parent = this, strength = strength)
}

@Immutable
internal data class ScaledCornerSize(
    private val parent: CornerSize,
    private val strength: Float,
) : CornerSize {

    override fun toPx(shapeSize: Size, density: Density): Float =
        (parent.toPx(shapeSize, density) * strength).coerceAtLeast(0f)
}

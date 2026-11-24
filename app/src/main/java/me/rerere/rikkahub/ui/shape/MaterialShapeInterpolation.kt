package me.rerere.rikkahub.ui.shape

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * M3 的动画器只负责推进进度，真正的插值仍要由形状的 lerp 实现。
 * CornerBasedShape 默认返回 null，自定义 Path 轮廓因此会在进度 0.5 处硬切。
 *
 * 各状态保留自己的角尺寸，但统一用主题的同一个 prototype 绘制：它只承载轮廓算法，
 * 不提供角尺寸，也不再次应用 cornerScale。这样 ToggleButton 的固定 CircleShape
 * 与主题的按下/选中形状也属于同一可插值族。默认全圆角组合保持 M3 原有实现不动。
 * 按压、焦点、选中、反向打断和动效开关仍全部由 M3 的原生动画器处理。
 */
internal fun ListItemShapes.withInterpolatableCorners(prototype: CornerBasedShape): ListItemShapes {
    if (shape is RoundedCornerShape && selectedShape is RoundedCornerShape &&
        pressedShape is RoundedCornerShape && focusedShape is RoundedCornerShape &&
        hoveredShape is RoundedCornerShape && draggedShape is RoundedCornerShape
    ) return this

    return copy(
        shape = shape.withInterpolatableCorners(prototype),
        selectedShape = selectedShape.withInterpolatableCorners(prototype),
        pressedShape = pressedShape.withInterpolatableCorners(prototype),
        focusedShape = focusedShape.withInterpolatableCorners(prototype),
        hoveredShape = hoveredShape.withInterpolatableCorners(prototype),
        draggedShape = draggedShape.withInterpolatableCorners(prototype),
    )
}

internal fun ToggleButtonShapes.withInterpolatableCorners(prototype: CornerBasedShape): ToggleButtonShapes {
    if (shape is RoundedCornerShape && pressedShape is RoundedCornerShape &&
        checkedShape is RoundedCornerShape
    ) return this

    return copy(
        shape = shape.withInterpolatableCorners(prototype),
        pressedShape = pressedShape.withInterpolatableCorners(prototype),
        checkedShape = checkedShape.withInterpolatableCorners(prototype),
    )
}

internal fun Shape.withInterpolatableCorners(prototype: CornerBasedShape): Shape {
    val corners = when (this) {
        is CornerBasedShape -> this
        RectangleShape -> RoundedCornerShape(0f)
        else -> return this // 非逐角形状不猜测其路径，沿用 M3 自己的降级行为
    }
    return InterpolatableCornerShape(
        prototype = prototype,
        topStart = corners.topStart,
        topEnd = corners.topEnd,
        bottomEnd = corners.bottomEnd,
        bottomStart = corners.bottomStart,
    )
}

@Immutable
private class InterpolatableCornerShape(
    private val prototype: CornerBasedShape,
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline = prototype.createOutline(
        size = size,
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart,
        layoutDirection = layoutDirection,
    )

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = InterpolatableCornerShape(prototype, topStart, topEnd, bottomEnd, bottomStart)

    override fun lerp(other: Any?, t: Float): Any? {
        if (other !is InterpolatableCornerShape || prototype !== other.prototype) return null
        // 不让 spring 过冲画出负半径；端点直接返回，避免无意义地嵌套角尺寸。
        if (t <= 0f) return this
        if (t >= 1f) return other
        return copy(
            topStart = InterpolatedCornerSize.between(topStart, other.topStart, t),
            topEnd = InterpolatedCornerSize.between(topEnd, other.topEnd, t),
            bottomEnd = InterpolatedCornerSize.between(bottomEnd, other.bottomEnd, t),
            bottomStart = InterpolatedCornerSize.between(bottomStart, other.bottomStart, t),
        )
    }

    // M3 用整组 shapes 作 key；相同参数必须相等，否则重组会重建动画状态而再次硬切。
    override fun equals(other: Any?): Boolean = this === other ||
        (other is InterpolatableCornerShape && prototype === other.prototype &&
            topStart == other.topStart && topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd && bottomStart == other.bottomStart)

    override fun hashCode(): Int {
        var result = System.identityHashCode(prototype)
        result = 31 * result + topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        return 31 * result + bottomStart.hashCode()
    }
}

@Immutable
private data class InterpolatedCornerSize(
    val terms: List<WeightedCornerSize>,
) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        // 百分比胶囊和 dp 角要在同一份实时尺寸/密度下求值，不预先折成固定像素。
        // 不另压到半高：让 CornerBasedShape 按上下角对归一化，非对称角在端点才不会跳变。
        return terms.fold(0f) { total, term ->
            total + term.cornerSize.toPx(shapeSize, density) * term.weight
        }.coerceAtLeast(0f)
    }

    companion object {
        fun between(from: CornerSize, to: CornerSize, fraction: Float): CornerSize {
            if (from == to) return from
            val weights = linkedMapOf<CornerSize, Float>()
            fun add(corner: CornerSize, weight: Float) {
                if (corner is InterpolatedCornerSize) {
                    corner.terms.forEach { term ->
                        weights[term.cornerSize] = (weights[term.cornerSize] ?: 0f) + term.weight * weight
                    }
                } else {
                    weights[corner] = (weights[corner] ?: 0f) + weight
                }
            }
            // 连点中断时展平并合并同一来源，toPx 的求值量只随原始角来源数增长，不递归套娃。
            add(from, 1f - fraction)
            add(to, fraction)
            val terms = weights.mapNotNull { (corner, weight) ->
                if (weight == 0f) null else WeightedCornerSize(corner, weight)
            }
            return InterpolatedCornerSize(terms)
        }
    }
}

@Immutable
private data class WeightedCornerSize(
    val cornerSize: CornerSize,
    val weight: Float,
)

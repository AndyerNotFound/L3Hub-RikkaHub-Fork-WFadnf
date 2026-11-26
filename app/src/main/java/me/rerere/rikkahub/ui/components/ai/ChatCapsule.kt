package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.ui.components.ui.AppearanceMaterial
import me.rerere.rikkahub.ui.components.ui.advancedMaterial
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.appButtonShadow

/** 胶囊本身的形状：药丸；全宽模式下和其余组件一起收成直角。 */
@Composable
private fun capsuleShape(): RoundedCornerShape =
    if (LocalFullWidthComponents.current) {
        RoundedCornerShape(0.dp)
    } else {
        RoundedCornerShape(50)
    }

/**
 * 这一档材质要不要再补一道平描边。
 *
 * 玻璃档由 [advancedMaterial] 自己画渐变描边（高光 + 内阴影），再叠一道平线就成了双线，
 * 所以只有玻璃档返回 false；其余各档（含实色）都补一道，和实色底那条路一样能看清边界。
 */
internal fun AppearanceMaterial?.needsFlatOutline(): Boolean =
    this != null && style != BackgroundSurfaceStyle.LIQUID_GLASS

/** [outlineOnly] leaves blur and tint to the top bar already drawn behind this control. */
@Composable
internal fun Modifier.chatCapsuleSurface(
    shape: Shape,
    base: Color,
    material: AppearanceMaterial?,
    hazeState: HazeState?,
    readableFloor: Float = 0f,
    outlineOnly: Boolean = false,
): Modifier {
    val surface = when {
        outlineOnly -> this
        material == null -> background(base, shape)
        else -> advancedMaterial(shape, base, material, hazeState, readableFloor)
    }
    return if (outlineOnly || material.needsFlatOutline()) {
        surface.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
    } else {
        surface
    }
}

/**
 * 顶栏与输入框下方悬浮栏共用的胶囊容器：同圆角、同投影、同底色，模糊与描边只有这一份实现。
 *
 * 圆角取 50%，容器多高就圆出多大的药丸；高度由调用方通过 [modifier] 给。
 * 里面的入口各自点击，段与段之间用 [ChatCapsuleDivider] 隔开。
 *
 * [material] 由调用方决定，传 null 时退回实色底。[outlineOnly] 只绘制轮廓，不覆盖已有的顶栏背景。
 */
@Composable
internal fun ChatCapsule(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    material: AppearanceMaterial? = null,
    readableFloor: Float = .35f,
    outlineOnly: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = capsuleShape()
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    Row(
        modifier = modifier
            .appButtonShadow(shape, 2.dp)
            .clip(shape)
            .chatCapsuleSurface(
                shape = shape,
                base = containerColor,
                material = material,
                hazeState = hazeState,
                readableFloor = readableFloor,
                outlineOnly = outlineOnly,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 入口按整颗胶囊的高度铺满，不被 M3 的最小触控尺寸撑大，两头才一般高
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            content()
        }
    }
}

/** 胶囊里两段之间的细线：只切中间一段，胶囊还是一整块。 */
@Composable
internal fun ChatCapsuleDivider(height: Dp = 18.dp) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(height)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}

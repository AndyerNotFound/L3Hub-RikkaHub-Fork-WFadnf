package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dev.chrisbanes.haze.HazeState
import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.finiteAppearanceValue

/**
 * 朴素外观：所有表面只画一层实心底色。
 *
 * 这里保留 [AppearanceMaterial] 的签名，是因为调用点还在传材质、半径和 Haze 源；
 * 拆掉这些参数属于后续的接口清理，渲染层本身已经不再读它们。
 */
@Immutable
internal data class AppearanceMaterial(
    val style: BackgroundSurfaceStyle,
    val opacity: Float,
    val radius: Float = 0f,
    val effects: Boolean = true,
    val allowDuringBusy: Boolean = false,
)

internal val LocalAdvancedPageMaterial = compositionLocalOf<AppearanceMaterial?> { null }
internal val LocalTopBarHazeState = compositionLocalOf<HazeState?> { null }
internal val LocalAdvancedAppearanceBusy = compositionLocalOf { false }
internal val LocalAdvancedAppearancePowerSave = compositionLocalOf { false }

/** 页面、浮层、导航栏统一走不透明表面。 */
private val PlainMaterial = AppearanceMaterial(BackgroundSurfaceStyle.OPAQUE, 1f)

internal fun AdvancedAppearanceSetting.pageMaterial() = PlainMaterial

internal fun AdvancedAppearanceSetting.overlayMaterial() = PlainMaterial

internal fun AdvancedAppearanceSetting.navigationMaterial() = PlainMaterial

/** 输入框与顶栏不再单独叠材质，返回 null 表示「用页面底色」。 */
internal fun AdvancedAppearanceSetting.composerMaterial(display: DisplaySetting): AppearanceMaterial? = null

/** 顶栏按实心表面绘制，避免正文从标题下穿过。 */
internal fun AdvancedAppearanceSetting.topBarMaterial(display: DisplaySetting): AppearanceMaterial? = PlainMaterial

internal fun resolvedLiveAppearanceRadius(radius: Float, sdk: Int, effects: Boolean): Float = 0f

internal fun appearanceSurfaceOpacity(opacity: Float, readableFloor: Float = 0f): Float {
    val value = finiteAppearanceValue(opacity, 0f, 1f, 1f)
    val floor = finiteAppearanceValue(readableFloor, 0f, 1f, 0f)
    return floor + (1f - floor) * value
}

/** 不叠色，底色即最终色。 */
internal fun AdvancedAppearanceSetting.materialTint(base: Color, accent: Color, style: BackgroundSurfaceStyle): Color = base

/** 浮层不铺中间层，直接透出底色。 */
internal fun AdvancedAppearanceSetting.staticOverlayColor(base: Color, backing: Color, accent: Color): Color? = null

/** 玻璃描边一并取消。 */
@Composable
internal fun AdvancedAppearanceSetting.staticOverlayBorder(): BorderStroke? = null

/**
 * 唯一的表面画笔：一层实心 [base]，裁到 [shape]。
 *
 * [material]、[hazeState]、[readableFloor]、[fallbackBase] 仅供调用点迁移期继续传参，
 * 朴素外观下不参与绘制。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun Modifier.advancedMaterial(
    shape: Shape,
    base: Color,
    material: AppearanceMaterial,
    hazeState: HazeState? = LocalGlassHazeState.current,
    readableFloor: Float = 0f,
    fallbackBase: Color? = null,
): Modifier = if (base.alpha <= 0f) this else background(base.copy(alpha = 1f), shape)

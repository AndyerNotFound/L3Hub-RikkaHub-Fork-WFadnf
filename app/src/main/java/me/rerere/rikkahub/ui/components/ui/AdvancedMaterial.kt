package me.rerere.rikkahub.ui.components.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.finiteAppearanceValue
import me.rerere.rikkahub.data.model.LiquidGlassRenderer
import me.rerere.rikkahub.data.model.MAX_LIQUID_GLASS_REFRACTION
import me.rerere.rikkahub.data.model.parameters
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.LocalAnimationsEnabled
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.ui.components.LocalSeparateWindow
import kotlin.random.Random

@Immutable
internal data class AppearanceMaterial(
    val style: BackgroundSurfaceStyle,
    val opacity: Float,
    val radius: Float = 0f,
    val effects: Boolean = true,
)

internal val LocalAdvancedPageMaterial = compositionLocalOf<AppearanceMaterial?> { null }
internal val LocalAdvancedAppearanceBusy = compositionLocalOf { false }
internal val LocalAdvancedAppearancePowerSave = compositionLocalOf { false }

internal fun AdvancedAppearanceSetting.pageMaterial() = AppearanceMaterial(
    style = pageSurfaceStyle,
    opacity = pageSurfaceOpacity,
    radius = backdropBlurRadius(),
)

internal fun AdvancedAppearanceSetting.backdropBlurRadius() = when (pageSurfaceStyle) {
    BackgroundSurfaceStyle.FROSTED, BackgroundSurfaceStyle.ACRYLIC -> globalBackgroundBlurRadius
    BackgroundSurfaceStyle.LIQUID_GLASS -> pageLiquidGlassBlurRadius
    else -> 0f
}

internal fun AdvancedAppearanceSetting.overlayMaterial() = AppearanceMaterial(
    style = overlaySurfaceStyle,
    opacity = overlaySurfaceOpacity,
    radius = if (overlaySurfaceStyle == BackgroundSurfaceStyle.LIQUID_GLASS) overlayLiquidGlassBlurRadius else overlaySurfaceBlurRadius,
    effects = enableOverlayPerformanceEffects,
)

internal fun AdvancedAppearanceSetting.navigationMaterial() = AppearanceMaterial(
    style = if (enableNavigationGlass) navigationSurfaceStyle else BackgroundSurfaceStyle.OPAQUE,
    opacity = navigationGlassOpacity,
    radius = if (navigationSurfaceStyle == BackgroundSurfaceStyle.LIQUID_GLASS) navigationLiquidGlassBlurRadius else navigationGlassBlurRadius,
    effects = enableNavigationPerformanceEffects,
)

internal fun AdvancedAppearanceSetting.composerMaterial(display: DisplaySetting): AppearanceMaterial? {
    if (!enableAdvancedAppearance) return null
    val style = composerSurfaceStyle ?: if (liquidGlass.applyToComposer) BackgroundSurfaceStyle.LIQUID_GLASS else return null
    return AppearanceMaterial(style, display.inputSurfaceOpacity, display.inputBlurRadius, enableInputPerformanceEffects)
}

internal fun AdvancedAppearanceSetting.topBarBackdropMaterial(display: DisplaySetting): AppearanceMaterial? {
    if (!enableAdvancedAppearance || !display.showTopBarBackground) return null
    return AppearanceMaterial(
        style = pageSurfaceStyle,
        opacity = display.topBarSurfaceOpacity,
        radius = display.topBarBlurRadius,
        effects = enableTopBarPerformanceEffects && display.enableTopBarBlur,
    )
}

internal fun resolvedLiveAppearanceRadius(radius: Float, sdk: Int, effects: Boolean): Float =
    if (!effects || sdk < Build.VERSION_CODES.S) 0f else
        finiteAppearanceValue(radius, 0f, if (sdk < Build.VERSION_CODES.TIRAMISU) 12f else 40f, 0f)

internal fun GlassOptics.withAppearanceRefraction(refraction: Float, radius: Dp): GlassOptics {
    val amount = finiteAppearanceValue(refraction, 0f, MAX_LIQUID_GLASS_REFRACTION, 0f)
    // Haze requires depth and strength in 0..1. Strength remains adjustable past full depth.
    return copy(
        refractionStrength = amount / MAX_LIQUID_GLASS_REFRACTION,
        depth = OpticalSizeValue.Fixed((amount / 6f).coerceAtMost(1f)),
        blurRadius = OpticalSizeValue.Fixed(radius),
    )
}

internal fun appearanceSurfaceOpacity(opacity: Float, readableFloor: Float = 0f): Float =
    readableFloor + (1f - readableFloor) * finiteAppearanceValue(opacity, 0f, 1f, 1f)

private fun AdvancedAppearanceSetting.materialTint(base: Color, accent: Color, style: BackgroundSurfaceStyle): Color = when (style) {
    BackgroundSurfaceStyle.MICA -> lerp(base, accent, micaTintStrength * .17f)
    BackgroundSurfaceStyle.ACRYLIC -> lerp(base, accent, .08f)
    BackgroundSurfaceStyle.LIQUID_GLASS -> lerp(base, accent, liquidGlass.parameters().tint)
    else -> base
}

internal fun AdvancedAppearanceSetting.staticOverlayColor(base: Color, backing: Color, accent: Color): Color? {
    if (!enableAdvancedAppearance) return null
    if (overlaySurfaceStyle.opaqueMaterial) return base
    val strength = when (overlaySurfaceStyle) {
        BackgroundSurfaceStyle.FROSTED -> .035f
        BackgroundSurfaceStyle.LIQUID_GLASS -> .055f
        BackgroundSurfaceStyle.ACRYLIC -> .025f
        else -> 0f
    }
    val tint = materialTint(lerp(base, accent, strength), accent, overlaySurfaceStyle)
    return tint.copy(alpha = overlaySurfaceOpacity).compositeOver(backing)
}

@Composable
internal fun AdvancedAppearanceSetting.staticOverlayBorder(): BorderStroke? {
    if (!enableAdvancedAppearance || overlaySurfaceStyle != BackgroundSurfaceStyle.LIQUID_GLASS) return null
    val p = liquidGlass.parameters()
    return BorderStroke(p.edgeWidth.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .18f + p.highlight * .3f))
}

/** Shares the existing Haze source and never captures a new scene for each item. */
@Composable
internal fun Modifier.advancedMaterial(
    shape: Shape,
    base: Color,
    material: AppearanceMaterial,
    hazeState: HazeState? = LocalGlassHazeState.current,
    readableFloor: Float = 0f,
    fallbackBase: Color? = null,
): Modifier {
    val style = material.style
    if (style == BackgroundSurfaceStyle.OPAQUE) return background(base.copy(alpha = 1f), shape)
    val separate = LocalSeparateWindow.current
    val source = if (separate) null else hazeState
    val appearance = LocalSettings.current.advancedAppearanceSetting
    val glass = appearance.liquidGlass
    val params = glass.parameters()
    val dark = LocalDarkMode.current
    val scheme = MaterialTheme.colorScheme
    val lowPower = LocalAdvancedAppearancePowerSave.current || !LocalAnimationsEnabled.current
    val busy = LocalAdvancedAppearanceBusy.current
    val live = material.effects && !separate && !lowPower && !(busy && glass.adaptivePerformance)
    val sdk = Build.VERSION.SDK_INT
    val realtime = live && !(style == BackgroundSurfaceStyle.LIQUID_GLASS && glass.renderer == LiquidGlassRenderer.STANDARD)
    val radius = resolvedLiveAppearanceRadius(material.radius, sdk, realtime).dp
    val tint = appearance.materialTint(base, scheme.primary, style).copy(alpha = appearanceSurfaceOpacity(material.opacity, readableFloor))
    val backing = if (source == null && fallbackBase != null) tint.compositeOver(fallbackBase) else tint
    val refraction = if (realtime && sdk >= Build.VERSION_CODES.TIRAMISU) {
        params.refraction
    } else 0f
    val effect = when {
        style == BackgroundSurfaceStyle.MICA -> Modifier.background(
            Brush.verticalGradient(listOf(backing, lerp(backing, scheme.surfaceContainerHigh.copy(alpha = backing.alpha), .36f))),
            shape,
        )
        style == BackgroundSurfaceStyle.LIQUID_GLASS && source != null && refraction > 0f -> {
            val glassShape = when (shape) {
                is RoundedCornerShape -> shape
                RectangleShape -> RoundedCornerShape(0.dp)
                else -> RoundedCornerShape(params.edgeWidth.dp)
            }
            val optics = remember(glassShape, dark, radius, refraction, params, tint) {
                SaltGlassStyles.regularSurface(dark).then {
                    shape(glassShape)
                    tint(tint)
                    optics { withAppearanceRefraction(refraction, radius) }
                    specularIntensity(params.highlight)
                    edgeShadow(Color.Black.copy(alpha = params.innerShadow))
                }
            }
            Modifier.hazeGlass(input = HazeInput.Sources(source), style = optics)
        }
        style.usesBlur && source != null && radius > 0.dp -> Modifier
            .hazeBlur(input = HazeInput.Sources(source), style = SaltGlassStyles.blur(dark, radius))
            .background(tint, shape)
        else -> Modifier.background(backing, shape)
    }
    val decoration = when (style) {
        BackgroundSurfaceStyle.LIQUID_GLASS -> {
            val scale = when {
                !material.effects || sdk < Build.VERSION_CODES.S -> .28f
                !realtime || source == null || sdk < Build.VERSION_CODES.TIRAMISU -> .55f
                else -> 1f
            }
            Modifier.border(
                params.edgeWidth.dp,
                Brush.linearGradient(listOf(
                    Color.White.copy(alpha = params.highlight * scale),
                    Color.Transparent,
                    Color.Black.copy(alpha = params.innerShadow * scale),
                )),
                shape,
            )
        }
        BackgroundSurfaceStyle.ACRYLIC -> if (live) Modifier.acrylicGrain(shape, dark) else Modifier
        else -> Modifier
    }
    // Clip only the effects, not the content or hit-test outline of very tall message bubbles.
    return then(Modifier.drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
    }.then(effect).then(decoration))
}

private fun Modifier.acrylicGrain(shape: Shape, dark: Boolean): Modifier = drawWithCache {
    val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    // A static, capped grain pattern costs no allocations while scrolling.
    val count = (size.width * size.height / 135f).toInt().coerceIn(0, 1550)
    val random = Random(709)
    val grains = List(count) { androidx.compose.ui.geometry.Offset(random.nextFloat() * size.width, random.nextFloat() * size.height) }
    val color = if (dark) Color.White.copy(alpha = .075f) else Color.Black.copy(alpha = .075f)
    onDrawBehind { clipPath(outline) { grains.forEach { drawCircle(color, .7f, it) } } }
}

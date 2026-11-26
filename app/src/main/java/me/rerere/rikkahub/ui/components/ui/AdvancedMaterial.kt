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
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassStyleScope
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.material3
import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.finiteAppearanceValue
import me.rerere.rikkahub.data.model.LiquidGlassRenderer
import me.rerere.rikkahub.data.model.MAX_LIQUID_GLASS_REFRACTION
import me.rerere.rikkahub.data.model.parameters
import me.rerere.rikkahub.data.model.usesMaterial3Style
import me.rerere.rikkahub.ui.context.LocalSettings
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
    radius = when (pageSurfaceStyle) {
        BackgroundSurfaceStyle.FROSTED, BackgroundSurfaceStyle.ACRYLIC -> pageSurfaceBlurRadius
        BackgroundSurfaceStyle.LIQUID_GLASS -> pageLiquidGlassBlurRadius
        else -> 0f
    },
)

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

internal fun AdvancedAppearanceSetting.topBarMaterial(display: DisplaySetting): AppearanceMaterial? {
    if (!enableAdvancedAppearance) return null
    return AppearanceMaterial(
        // The default translucent page style can still opt into blur for the top bar.
        style = when (pageSurfaceStyle) {
            BackgroundSurfaceStyle.TRANSLUCENT -> BackgroundSurfaceStyle.FROSTED
            else -> pageSurfaceStyle
        },
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

internal fun appearanceSurfaceOpacity(opacity: Float, readableFloor: Float = 0f): Float {
    val value = finiteAppearanceValue(opacity, 0f, 1f, 1f)
    val floor = finiteAppearanceValue(readableFloor, 0f, 1f, 0f)
    return floor + (1f - floor) * value
}

internal fun AdvancedAppearanceSetting.materialTint(base: Color, accent: Color, style: BackgroundSurfaceStyle): Color = when (style) {
    BackgroundSurfaceStyle.MICA -> lerp(base, accent, micaTintStrength * .17f)
    BackgroundSurfaceStyle.ACRYLIC -> lerp(base, accent, .08f)
    // 原版玻璃自己按 containerColor / tint 做着色响应，不叠加 Salt preset 的 tint 参数。
    BackgroundSurfaceStyle.LIQUID_GLASS ->
        if (liquidGlass.usesMaterial3Style()) base else lerp(base, accent, liquidGlass.parameters().tint)
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
    // 原版玻璃的描边由 Haze 的 specular / edgeShadow 负责，此处不读 Salt 的 edgeWidth / highlight。
    if (liquidGlass.usesMaterial3Style()) {
        return BorderStroke(Material3GlassFallbackEdgeWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .24f))
    }
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
    // 原版玻璃只替换 LIQUID_GLASS 的实现；其它表面风格不受这个开关影响。
    val material3Glass = style == BackgroundSurfaceStyle.LIQUID_GLASS && glass.usesMaterial3Style()
    val salt = if (style == BackgroundSurfaceStyle.LIQUID_GLASS && !material3Glass) glass.parameters() else null
    val dark = LocalDarkMode.current
    val scheme = MaterialTheme.colorScheme
    // Blur is a rendering effect, not motion. Disabling app/system animations must not silently disable it.
    val lowPower = LocalAdvancedAppearancePowerSave.current
    val busy = LocalAdvancedAppearanceBusy.current
    val live = material.effects && !separate && !lowPower && !(busy && glass.adaptivePerformance)
    val sdk = Build.VERSION.SDK_INT
    val realtime = live && !(style == BackgroundSurfaceStyle.LIQUID_GLASS && glass.renderer == LiquidGlassRenderer.STANDARD)
    val requestedRadius = if (material3Glass) Material3GlassBlurRadius.value else material.radius
    val radius = resolvedLiveAppearanceRadius(requestedRadius, sdk, realtime).dp
    val tint = appearance.materialTint(base, scheme.primary, style).copy(alpha = appearanceSurfaceOpacity(material.opacity, readableFloor))
    val backing = if (source == null && fallbackBase != null) tint.compositeOver(fallbackBase) else tint
    val refraction = if (realtime && sdk >= Build.VERSION_CODES.TIRAMISU) {
        salt?.refraction ?: 0f
    } else 0f
    val effect = when {
        style == BackgroundSurfaceStyle.MICA -> Modifier.background(
            Brush.verticalGradient(listOf(backing, lerp(backing, scheme.surfaceContainerHigh.copy(alpha = backing.alpha), .36f))),
            shape,
        )
        style == BackgroundSurfaceStyle.LIQUID_GLASS && material3Glass && source != null &&
            realtime && sdk >= Build.VERSION_CODES.TIRAMISU -> {
            // 原版玻璃：containerColor + tint 交给 Haze Material3 样式，光学参数固定为上游取值，
            // 不读取 Salt 的 preset / highlight / refraction / edgeWidth / innerShadow。
            val glassShape = shape.liquidGlassShape(Material3GlassFallbackRadius)
            val baseStyle = GlassStyle.material3(
                containerColor = tint,
                tint = tint.copy(alpha = tint.alpha * MATERIAL3_GLASS_TINT_ALPHA),
            )
            // Haze 2.1 按实例比较样式，记住它，避免每次重组都换一个新样式。
            val glassStyle = remember(baseStyle, glassShape, dark) {
                baseStyle.then {
                    appearanceResponse(dark)
                    optics {
                        copy(
                            blurRadius = OpticalSizeValue.Fixed(Material3GlassBlurRadius),
                            depth = OpticalSizeValue.Fixed(MATERIAL3_GLASS_DEPTH),
                        )
                    }
                    shape(glassShape)
                }
            }
            Modifier.hazeGlass(input = HazeInput.Sources(source), style = glassStyle)
        }
        salt != null && source != null && refraction > 0f -> {
            val glassShape = shape.liquidGlassShape(salt.edgeWidth.dp)
            val optics = remember(glassShape, dark, radius, refraction, salt, tint) {
                SaltGlassStyles.regularSurface(dark).then {
                    shape(glassShape)
                    tint(tint)
                    optics { withAppearanceRefraction(refraction, radius) }
                    specularIntensity(salt.highlight)
                    edgeShadow(Color.Black.copy(alpha = salt.innerShadow))
                }
            }
            Modifier.hazeGlass(input = HazeInput.Sources(source), style = optics)
        }
        style.usesBlur && source != null && radius > 0.dp -> Modifier
            .blurBackdrop(source, radius)
            .background(tint, shape)
        else -> Modifier.background(backing, shape)
    }
    val decoration = when (style) {
        // 原版玻璃的 specular 与边缘阴影由 Haze 自己画；实时不可用时只补一条固定中性描边，
        // 同样不读 Salt 的 highlight / innerShadow。
        BackgroundSurfaceStyle.LIQUID_GLASS -> if (material3Glass) {
            if (source != null && realtime && sdk >= Build.VERSION_CODES.TIRAMISU) Modifier
            else Modifier.border(
                Material3GlassFallbackEdgeWidth,
                Brush.linearGradient(listOf(
                    Color.White.copy(alpha = MATERIAL3_GLASS_FALLBACK_HIGHLIGHT),
                    Color.Transparent,
                    Color.Black.copy(alpha = MATERIAL3_GLASS_FALLBACK_SHADOW),
                )),
                shape,
            )
        } else if (salt != null) {
            val scale = when {
                !material.effects || sdk < Build.VERSION_CODES.S -> .28f
                !realtime || source == null || sdk < Build.VERSION_CODES.TIRAMISU -> .55f
                else -> 1f
            }
            Modifier.border(
                salt.edgeWidth.dp,
                Brush.linearGradient(listOf(
                    Color.White.copy(alpha = salt.highlight * scale),
                    Color.Transparent,
                    Color.Black.copy(alpha = salt.innerShadow * scale),
                )),
                shape,
            )
        } else Modifier
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

// RikkaHub 原版液态玻璃（ChatInputHaze.kt）的固定参数：不随 Salt 的滑杆变化。
private const val MATERIAL3_GLASS_DEPTH = .5f
// 原版把 containerColor 当 tint 传入，透明度压到三成。
private const val MATERIAL3_GLASS_TINT_ALPHA = .3f
// 降级（无实时光学）时中性描边的高光 / 阴影强度。
private const val MATERIAL3_GLASS_FALLBACK_HIGHLIGHT = .18f
private const val MATERIAL3_GLASS_FALLBACK_SHADOW = .12f
private val Material3GlassBlurRadius = 16.dp
// 原版玻璃拿到非圆角形状时的兜底圆角，以及降级描边宽度。
private val Material3GlassFallbackRadius = 16.dp
private val Material3GlassFallbackEdgeWidth = 1.5.dp

/**
 * 与原版 ChatInputHaze 一致：按应用主题显式写入玻璃的响应参数。
 * Haze 按系统夜间模式挑预设，应用内单独设深色主题时会挑错（深色下被提亮发白），
 * 深色数值对应库内 internal 的 GlassStyle.regularDark。
 */
private fun GlassStyleScope.appearanceResponse(dark: Boolean) {
    if (dark) {
        specularIntensity(.38f)
        edgeShadow(Color.Black.copy(alpha = .32f))
        ambientResponse(.08f)
        contrast(.08f)
        whitePoint(-.22f)
        chromaMultiplier(1.1f)
    } else {
        specularIntensity(GlassDefaults.specularIntensity)
        edgeShadow(GlassDefaults.edgeShadow)
        ambientResponse(GlassDefaults.ambientResponse)
        contrast(GlassDefaults.contrast)
        whitePoint(GlassDefaults.whitePoint)
        chromaMultiplier(GlassDefaults.chromaMultiplier)
    }
}

/** Haze 的玻璃样式要具体圆角形状；非圆角形状按固定的兜底半径处理。 */
private fun Shape.liquidGlassShape(fallbackRadius: Dp): RoundedCornerShape = when (this) {
    is RoundedCornerShape -> this
    RectangleShape -> RoundedCornerShape(0.dp)
    else -> RoundedCornerShape(fallbackRadius)
}

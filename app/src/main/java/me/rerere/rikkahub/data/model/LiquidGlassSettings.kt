package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.datastore.finiteAppearanceValue

internal const val MAX_LIQUID_GLASS_REFRACTION = 16f

/** Glass preset parameters adapted from Rikkahub Revised (AGPL-3.0). */
@Serializable
enum class LiquidGlassPreset { SOFT, STANDARD, CLEAR, CUSTOM }

@Serializable
enum class LiquidGlassRenderer { AUTO, STANDARD, AGSL }

/**
 * 液态玻璃的光学实现来源。
 *
 * - [SALT]：现有的 Salt 光学样式，读取 [LiquidGlassParameters]（高光、雾化、折射、内阴影）。
 * - [MATERIAL3]：RikkaHub 原版的 Haze Material3 玻璃（containerColor + tint + 固定 optics），
 *   不读取 Salt 的 preset 与自定义参数。
 *
 * 旧数据的 JSON 没有这个字段，默认落到 [SALT]，观感与加入该字段前一致。
 */
@Serializable
enum class LiquidGlassStyle { SALT, MATERIAL3 }

@Serializable
data class LiquidGlassParameters(
    val highlight: Float = .38f,
    val tint: Float = .05f,
    val edgeWidth: Float = 1.5f,
    val refraction: Float = 6f,
    val innerShadow: Float = .1f,
)

@Serializable
data class LiquidGlassSettings(
    val preset: LiquidGlassPreset = LiquidGlassPreset.STANDARD,
    val custom: LiquidGlassParameters = LiquidGlassParameters(),
    val renderer: LiquidGlassRenderer = LiquidGlassRenderer.AUTO,
    val adaptivePerformance: Boolean = true,
    val applyToComposer: Boolean = false,
    val style: LiquidGlassStyle = LiquidGlassStyle.SALT,
)

/** 是否走 RikkaHub 原版的 Haze Material3 玻璃（Salt 的 preset / 自定义参数不参与）。 */
internal fun LiquidGlassSettings.usesMaterial3Style(): Boolean = style == LiquidGlassStyle.MATERIAL3

internal fun LiquidGlassSettings.parameters(): LiquidGlassParameters = when (preset) {
    LiquidGlassPreset.SOFT -> LiquidGlassParameters(.20f, .035f, 1f, 3f, .06f)
    LiquidGlassPreset.STANDARD -> LiquidGlassParameters()
    LiquidGlassPreset.CLEAR -> LiquidGlassParameters(.55f, .015f, 2f, 9f, .08f)
    LiquidGlassPreset.CUSTOM -> custom
}.normalized()

internal fun LiquidGlassParameters.normalized(): LiquidGlassParameters = copy(
    highlight = finiteAppearanceValue(highlight, 0f, .8f, .38f),
    tint = finiteAppearanceValue(tint, 0f, .2f, .05f),
    edgeWidth = finiteAppearanceValue(edgeWidth, .5f, 4f, 1.5f),
    refraction = finiteAppearanceValue(refraction, 0f, MAX_LIQUID_GLASS_REFRACTION, 6f),
    innerShadow = finiteAppearanceValue(innerShadow, 0f, .25f, .1f),
)

internal fun LiquidGlassSettings.edit(transform: LiquidGlassParameters.() -> LiquidGlassParameters): LiquidGlassSettings =
    copy(preset = LiquidGlassPreset.CUSTOM, custom = parameters().transform().normalized())

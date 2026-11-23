package me.rerere.rikkahub.ui.components.ui

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue

/**
 * 移植自 SaltUI（Apache-2.0，Moriafly）的 premium 模糊与折射玻璃参数。
 *
 * 与 L3Hub 原先的 `HazeBlurStyle.Material3` / `GlassStyle.material3` 预设相比：
 * - 模糊档在模糊过程中做多层颜色混合（Softlight / Plus / Overlay / Luminosity）并加噪点，
 *   纯色背景下也有层次，观感对齐 SaltUI 的 premium material；
 * - 玻璃档使用完整光学参数（depth / specular / edgeShadow / whitePoint 校色），
 *   是真正的折射玻璃，而非厚重的磨砂。
 *
 * 半径参数化：模糊档的 [blur] 半径可调，供消息样式设置里的「模糊半径」直接驱动。
 */
object SaltGlassStyles {
    const val DARK_CONTRAST = 0.08f

    /** 模糊档：多层 colorEffects 混合 + 噪点，半径按调用方传入。 */
    fun blur(
        isDarkTheme: Boolean,
        radius: Dp = 16.dp,
        background: Color = Color.Transparent,
    ): HazeBlurStyle = HazeBlurStyle {
        backgroundColor(background)
        colorEffects(
            if (isDarkTheme) {
                listOf(
                    HazeColorEffect.tint(color = Color(0x35666666)),
                    HazeColorEffect.tint(
                        color = Color(0x15333333),
                        blendMode = BlendMode.Softlight,
                    ),
                    HazeColorEffect.tint(
                        color = Color(0x99000000),
                        blendMode = BlendMode.Overlay,
                    ),
                    HazeColorEffect.tint(
                        color = Color(0x18000000),
                        blendMode = BlendMode.Luminosity,
                    ),
                )
            } else {
                listOf(
                    HazeColorEffect.tint(
                        color = Color(0x65DBDBDB),
                        blendMode = BlendMode.Softlight,
                    ),
                    HazeColorEffect.tint(
                        color = Color(0x38EFEFEF),
                        blendMode = BlendMode.Plus,
                    ),
                )
            }
        )
        blurRadius(radius)
        noiseFactor(0.01f)
    }

    /** 折射玻璃基础样式：depth 与轻模糊制造景深，specular 高光关闭。 */
    fun regular(isDarkTheme: Boolean): GlassStyle = GlassStyle.regular.then {
        optics {
            copy(
                depth = OpticalSizeValue.Fixed(1f),
                blurRadius = OpticalSizeValue.Fixed(2.5.dp),
            )
        }
        specularIntensity(0f)
        if (isDarkTheme) {
            edgeShadow(Color(0x33000000))
            ambientResponse(0.08f)
            contrast(DARK_CONTRAST)
            whitePoint(-0.22f)
            chromaMultiplier(1.1f)
        } else {
            edgeShadow(GlassDefaults.edgeShadow)
            ambientResponse(GlassDefaults.ambientResponse)
            contrast(GlassDefaults.contrast)
            whitePoint(GlassDefaults.whitePoint)
            chromaMultiplier(GlassDefaults.chromaMultiplier)
        }
    }

    /** 表面玻璃（气泡 / 卡片 / 面板）：对比压平、白点提升，specular 高光恢复。 */
    fun regularSurface(isDarkTheme: Boolean): GlassStyle = regular(isDarkTheme).then {
        contrast(0f)
        whitePoint(regularWhitePoint(isDarkTheme))
        if (isDarkTheme) {
            chromaMultiplier(1f)
        }
        specularIntensity(0.32f)
    }

    fun transparent(isDarkTheme: Boolean): GlassStyle = regular(isDarkTheme).then {
        tint(Color.Transparent)
        contrast(0f)
        whitePoint(0f)
        chromaMultiplier(1f)
    }

    fun regularWhitePoint(isDarkTheme: Boolean): Float = if (isDarkTheme) 0.05f else 0.20f
}

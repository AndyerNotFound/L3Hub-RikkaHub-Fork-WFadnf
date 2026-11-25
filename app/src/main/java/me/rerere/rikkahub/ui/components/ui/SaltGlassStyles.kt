package me.rerere.rikkahub.ui.components.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue

/** Optical glass styles adapted from SaltUI (Apache-2.0, Moriafly). */
object SaltGlassStyles {
    const val DARK_CONTRAST = 0.08f

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

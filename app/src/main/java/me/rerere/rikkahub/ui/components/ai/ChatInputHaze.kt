package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassStyleScope
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.material3
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.ui.components.ui.glassSurfaceAlpha
import me.rerere.rikkahub.ui.theme.LocalDarkMode

/**
 * 输入框容器的背景效果（模糊或玻璃），未开启时原样返回。
 *
 * 开启后容器本身需要保持透明，[containerColor] 由效果负责铺底和着色。
 *
 * [alpha] 留空时按效果取浓度：模糊读 [DisplaySetting.blurOpacity]（那一档只显示这个滑杆），
 * 玻璃读 [DisplaySetting.glassOpacity]。以前玻璃档写死 0.3，滑杆在这一整类组件上是死的。
 */
@Composable
internal fun Modifier.chatInputHaze(
    hazeState: HazeState,
    displaySetting: DisplaySetting,
    containerColor: Color,
    shape: RoundedCornerShape,
    // 顶栏图标按钮这类「玻璃底」显式传玻璃透明度；输入框和气泡留空
    alpha: Float? = null,
): Modifier {
    val effect = displaySetting.effectiveBackgroundEffect
    val tintAlpha = (alpha ?: when (effect) {
        BackgroundEffectType.OFF -> 1f
        BackgroundEffectType.BLUR -> displaySetting.blurOpacity
        BackgroundEffectType.GLASS -> displaySetting.glassSurfaceAlpha
    }).coerceIn(0f, 1f)
    return when (effect) {
        BackgroundEffectType.OFF -> this

        BackgroundEffectType.BLUR -> {
            val tint = containerColor.copy(
                alpha = containerColor.alpha * tintAlpha,
            )
            hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = HazeBlurStyle.Material3(containerColor = tint) {
                    blurRadius(12.dp)
                },
            )
        }

        BackgroundEffectType.GLASS -> {
            val darkMode = LocalDarkMode.current
            val tint = containerColor.copy(alpha = containerColor.alpha * tintAlpha)
            // GlassStyle.material3 是 @Composable，必须在 remember 的 lambda 外面构造
            val baseStyle = GlassStyle.material3(
                containerColor = containerColor,
                tint = tint,
            )
            // 2.1.0 起样式按实例比较，记住它，避免每次重组都换一个新样式
            val style = remember(baseStyle, darkMode, shape) {
                baseStyle.then {
                    appearanceResponse(dark = darkMode)
                    // Keep background text from competing with the input text.
                    optics {
                        copy(
                            blurRadius = OpticalSizeValue.Fixed(16.dp),
                            depth = OpticalSizeValue.Fixed(0.5f),
                        )
                    }
                    shape(shape)
                }
            }
            hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = style,
            )
        }
    }
}

// Haze 按系统夜间模式选玻璃的明暗预设，应用内单独设置主题时会选错（深色主题下被提亮发白），
// 所以按应用主题显式写入。深色数值对应库里 internal 的 GlassStyle.regularDark。
private fun GlassStyleScope.appearanceResponse(dark: Boolean) {
    if (dark) {
        specularIntensity(0.38f)
        edgeShadow(Color.Black.copy(alpha = 0.32f))
        ambientResponse(0.08f)
        contrast(0.08f)
        whitePoint(-0.22f)
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

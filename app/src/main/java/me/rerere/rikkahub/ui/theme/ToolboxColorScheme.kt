package me.rerere.rikkahub.ui.theme

import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.t8rin.dynamic.theme.ColorTuple
import com.t8rin.dynamic.theme.animateAllColors
import com.t8rin.dynamic.theme.rememberAppColorTuple
import com.t8rin.dynamic.theme.rememberColorScheme
import me.rerere.rikkahub.data.datastore.Settings

/**
 * 用 ImageToolbox 的主题引擎（`com.t8rin.dynamic.theme`）算整套配色。
 *
 * 引擎的输入只有一组种子色（`ColorTuple`）。整套 48 个角色由配色风格（`PaletteStyle` 九档）、对比度、
 * 反色、色盲矫正四档参数推开——这正是本项目原来的 `Palette.kt` 与 7 个 preset 各自做了一半的事。
 *
 * 取舍写在下面三条，改这里之前先看一遍：
 *
 * 1. **调色板（逐角色覆盖）不进引擎**。[`PaletteSetting`] 允许用户把某个角色直接改成某个值，
 *    而引擎只会「从种子推」，推不出「这一格就是这个值」。所以 `palette.enabled` 时整个绕过引擎、
 *    原样走 [me.rerere.rikkahub.ui.theme.PaletteSetting.generateColorScheme]，代价是那一档下
 *    风格/对比度/色盲/反色四项不起作用（也就不会和用户的逐角色设置打架）。
 * 2. **preset / custom 主题的配色作为种子喂回去**，而不是只喂 primary。七个预设主题各是一套手写的
 *    完整配色，只取主色会把次色/三色/表面族全部按 TonalSpot 重算，色相等级全变；喂进六个种子之后，
 *    出来的仍是同一族颜色。注意这仍是「重算」而非「原样」：预设主题在引擎下的绝对色值会变，
 *    只有 AMOLED 与调色板那两条老路是逐字节不变的。
 * 3. **动态取色交给引擎**：`dynamicColor` 打开且系统 ≥ S 时，引擎直接铺 `dynamicDarkColorScheme`
 *    （和接引擎之前同一份），壁纸取色（[rememberAppColorTuple]）只在「点了动态取色但系统不够新」时兜底。
 *
 * 返回的配色已经过 [animateAllColors]：换主题、拖对比度时颜色是渐变的，不是一帧跳过去。
 * 因此这个函数的返回值**每次重组都是新对象**，别拿它当 `remember` 的 key 去做重活。
 */
@Composable
fun rememberToolboxColorScheme(
    settings: Settings,
    darkTheme: Boolean,
    amoledDark: Boolean,
): ColorScheme {
    val toolTheme = settings.toolTheme

    // 旧路的结果：preset / custom 主题本身就是一套完整配色，这里把它当作种子来源
    val presetScheme = remember(settings.themeId, settings.customThemes, darkTheme) {
        val theme = findThemeById(settings.themeId, settings.customThemes)
            ?: findPresetTheme(settings.themeId)
        theme.getColorScheme(dark = darkTheme)
    }

    // 用户选了自定义主色就它优先，否则从当前主题反推六个种子
    val seedTuple = remember(presetScheme, toolTheme.appColorTuple) {
        toolTheme.appColorTuple?.toColorTuple() ?: presetScheme.toColorTuple()
    }

    // 系统不够新时 dynamicColor 是开不出 M3 动态色的，按「没开」处理，和接引擎之前一致
    val dynamicColor = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorTuple = rememberAppColorTuple(
        defaultColorTuple = seedTuple,
        dynamicColor = dynamicColor,
        darkTheme = darkTheme,
    )

    return rememberColorScheme(
        isDarkTheme = darkTheme,
        // AMOLED 由引擎压黑（含强调色的降调），外层不要再叠一次 amoledSurface
        amoledMode = darkTheme && amoledDark,
        colorTuple = colorTuple,
        style = toolTheme.paletteStyle,
        contrastLevel = toolTheme.themeContrastLevel,
        dynamicColor = dynamicColor,
        isInvertColors = toolTheme.isInvertThemeColors,
        colorBlindType = toolTheme.colorBlindType,
        dynamicColorsOverride = null,
    ).animateAllColors(tween(300))
}

/** 反过来：把一套已经算好的配色拆回种子色。容器色与状态色不参与推导，丢了不影响风格。 */
private fun ColorScheme.toColorTuple(): ColorTuple = ColorTuple(
    primary = primary,
    secondary = secondary,
    tertiary = tertiary,
    surface = surface,
    neutralVariant = surfaceVariant,
    error = error,
)

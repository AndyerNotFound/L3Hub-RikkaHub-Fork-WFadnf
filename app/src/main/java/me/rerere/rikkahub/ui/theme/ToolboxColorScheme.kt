package me.rerere.rikkahub.ui.theme

import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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

    val engineScheme = rememberColorScheme(
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
    )

    // 五档容器覆盖发生在 animateAllColors 之前：换主题/拖对比度时，这五格跟其余角色一起渐变。
    // 放到动画之后的话它们会一帧跳过去，与整套配色的过渡脱节。
    // AMOLED 那一档不覆盖：引擎已经把整片表面压成纯黑，这里盖回灰阶就等于把 AMOLED 关掉。
    val kelivoScheme = if (darkTheme && amoledDark) {
        engineScheme
    } else {
        engineScheme.withKelivoSurfaceLadder(darkTheme)
    }

    return kelivoScheme.animateAllColors(tween(300))
}

// ---------------------------------------------------------------------------
// Kelivo legacy 表面色阶（docs/kelivo-design-spec.md A.1.3 / A.1.4 实算值）
//
// 引擎那套 surfaceContainer* 是 M3 的固定音调阶梯，与 Kelivo 默认（legacy，非分层）的长相
// 不一样：Kelivo 明色是「往上加白」的微差（0/+2/+3/+7/−11 相对页面），暗色是 3%→4.5%→6%→9%
// 的白叠加。差值太小，M3 阶梯表达不出来，所以在这里按实算值逐格覆盖。
//
// 只在「主题引擎算出来的那一份」上覆盖：调色板（palette.enabled）那条路是逐角色指定，
// 绕过引擎，也不走这个函数（见文件顶部取舍第 1 条）。
// ---------------------------------------------------------------------------

/** Kelivo legacy 的五档容器（A.1.3）。顺序：Lowest / Low / Container / High / Highest。 */
private val KelivoSurfaceLight = listOf(
    Color(0xFFFDFDFD),
    Color(0xFFFBFBFB),
    Color(0xFFFAFAFA),
    Color(0xFFFEFEFE),
    Color(0xFFECECEC),
)

private val KelivoSurfaceDark = listOf(
    Color(0xFF0D0D0E),
    Color(0xFF19191A),
    Color(0xFF1C1C1D),
    Color(0xFF202021),
    Color(0xFF272728),
)

/** 按深浅换成 Kelivo 的五档容器。surfaceFill（= Highest）与卡片色由调用点自己的角色取用。 */
private fun ColorScheme.withKelivoSurfaceLadder(dark: Boolean): ColorScheme {
    val ladder = if (dark) KelivoSurfaceDark else KelivoSurfaceLight
    return copy(
        surfaceContainerLowest = ladder[0],
        surfaceContainerLow = ladder[1],
        surfaceContainer = ladder[2],
        surfaceContainerHigh = ladder[3],
        surfaceContainerHighest = ladder[4],
    )
}

/**
 * Kelivo 的 hairline（A.1.4）：明 6% 黑、暗 8% 白，分割线与卡片描边用。
 *
 * 为什么没有回灌进 [ColorScheme]：引擎给的两格语义与 Kelivo 对不上。
 * `outline` 在 14 处被当「弱化图标/文字色」整值使用，`outlineVariant` 在 46 处被当描边基色
 * 再乘 0.35–0.5 的 alpha；把任一格改成 6% 黑，前一类会淡到看不见，后一类会掉到 1–3%。
 * Kelivo 的 hairline 是**独立**语义 token（`AppSemanticColors.hairline`），不是 M3 outline 的别名。
 * 这里先只给出值，等阶段 2 的 SectionCard / 分组描边落地时由那一层消费。
 */
val KelivoHairlineLight = Color(0x0F000000)

val KelivoHairlineDark = Color(0x14FFFFFF)

/** 取当前深浅下的 Kelivo hairline。 */
fun kelivoHairline(dark: Boolean): Color = if (dark) KelivoHairlineDark else KelivoHairlineLight

/** 反过来：把一套已经算好的配色拆回种子色。容器色与状态色不参与推导，丢了不影响风格。 */
private fun ColorScheme.toColorTuple(): ColorTuple = ColorTuple(
    primary = primary,
    secondary = secondary,
    tertiary = tertiary,
    surface = surface,
    neutralVariant = surfaceVariant,
    error = error,
)

package me.rerere.rikkahub.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import me.rerere.rikkahub.R

/**
 * 内置调色板：只给主色/辅色/第三色/错误色四个种子，
 * 其余角色（容器色、状态色、表面色阶）由 M3 音调规则生成。
 */
data class PalettePreset(
    val id: String,
    @StringRes val nameRes: Int,
    val primary: Long,
    val secondary: Long,
    val tertiary: Long,
    val error: Long,
    /** 中性色种子，只给单色系用；为 null 时中性色跟随主色。 */
    val neutral: Long? = null,
) {
    fun generateColorScheme(dark: Boolean): ColorScheme = paletteColorScheme(
        dark = dark,
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        error = error,
        neutral = neutral ?: primary,
        neutralVariant = neutral ?: secondary,
    )

    /** 展开成九个可直接编辑的颜色，自定义时以它为起点。 */
    fun colors(dark: Boolean): PaletteColors {
        val scheme = generateColorScheme(dark)
        return PaletteColors(
            primary = scheme.primary.argbLong(),
            onPrimary = scheme.onPrimary.argbLong(),
            secondary = scheme.secondary.argbLong(),
            tertiary = scheme.tertiary.argbLong(),
            error = scheme.error.argbLong(),
            background = scheme.background.argbLong(),
            surface = scheme.surface.argbLong(),
            surfaceVariant = scheme.surfaceVariant.argbLong(),
            onSurface = scheme.onSurface.argbLong(),
        )
    }
}

val DEFAULT_PALETTE_ID = "default"

val PalettePresets: List<PalettePreset> = listOf(
    PalettePreset(
        id = DEFAULT_PALETTE_ID,
        nameRes = R.string.palette_name_default,
        primary = 0xFF6750A4,
        secondary = 0xFF625B71,
        tertiary = 0xFF7D5260,
        error = 0xFFB3261E,
    ),
    PalettePreset(
        id = "ocean",
        nameRes = R.string.palette_name_ocean,
        primary = 0xFF116682,
        secondary = 0xFF4D616C,
        tertiary = 0xFF5D5B7D,
        error = 0xFFBA1A1A,
    ),
    PalettePreset(
        id = "forest",
        nameRes = R.string.palette_name_forest,
        primary = 0xFF4C662B,
        secondary = 0xFF586249,
        tertiary = 0xFF386663,
        error = 0xFFBA1A1A,
    ),
    PalettePreset(
        id = "sunset",
        nameRes = R.string.palette_name_sunset,
        primary = 0xFF8B5000,
        secondary = 0xFF755843,
        tertiary = 0xFF8C4A60,
        error = 0xFFBA1A1A,
    ),
    PalettePreset(
        id = "rose",
        nameRes = R.string.palette_name_rose,
        primary = 0xFF8E4955,
        secondary = 0xFF76565A,
        tertiary = 0xFF785831,
        error = 0xFFBA1A1A,
    ),
    PalettePreset(
        id = "monochrome",
        nameRes = R.string.palette_name_monochrome,
        primary = 0xFF5C5C5C,
        secondary = 0xFF5C5C5C,
        tertiary = 0xFF5C5C5C,
        error = 0xFFBA1A1A,
        neutral = 0xFF5C5C5C,
    ),
    // ---- Kelivo 组（阶段 1 规格层）------------------------------------------
    // 种子一律取 Kelivo 调色板的 **明色** 角色值（来源 lib/theme/palettes.dart，见
    // docs/kelivo-design-spec.md A.1.1 / A.1.2）：引擎按 M3 音调规则由同一个种子推明暗两套，
    // 暗色主色落在 tonal 80，与 Kelivo 自己写的暗色主色基本同一档。
    // 已知偏差：`kelivo_monochrome` 在 Kelivo 里暗色主色是纯白、明色是纯黑，引擎只能给 tonal 灰
    // （明 ~tone 40 / 暗 ~tone 80）；要一模一样得等调色板那套逐角色覆盖，不是种子能表达的。
    // neutral 取该调色板的明色 surface：Kelivo 的表面是中性偏一点点主题色，喂主色会把卡片底染色。
    PalettePreset(
        id = "kelivo",
        nameRes = R.string.palette_name_kelivo,
        primary = 0xFF4D5C92,
        secondary = 0xFF595D72,
        tertiary = 0xFF75546F,
        error = 0xFFBB0947,
        neutral = 0xFFF7F7F7,
    ),
    PalettePreset(
        id = "kelivo_blue",
        nameRes = R.string.palette_name_kelivo_blue,
        primary = 0xFF3E5E98,
        secondary = 0xFF575E71,
        tertiary = 0xFF77517D,
        error = 0xFFBB0947,
        neutral = 0xFFFDFBFF,
    ),
    PalettePreset(
        id = "kelivo_green",
        nameRes = R.string.palette_name_kelivo_green,
        primary = 0xFF166C47,
        secondary = 0xFF4E6356,
        tertiary = 0xFF22667A,
        error = 0xFFA63B00,
        neutral = 0xFFFBFDF8,
    ),
    PalettePreset(
        id = "kelivo_purple",
        nameRes = R.string.palette_name_kelivo_purple,
        primary = 0xFF5D5698,
        secondary = 0xFF5F5C71,
        tertiary = 0xFF844D69,
        error = 0xFFBB0947,
        neutral = 0xFFFFFBFF,
    ),
    PalettePreset(
        id = "kelivo_yellow",
        nameRes = R.string.palette_name_kelivo_yellow,
        primary = 0xFF855304,
        secondary = 0xFF715A41,
        tertiary = 0xFF50652F,
        error = 0xFFA63B00,
        neutral = 0xFFFFFBF9,
    ),
    PalettePreset(
        id = "kelivo_smoky_rose",
        nameRes = R.string.palette_name_kelivo_smoky_rose,
        primary = 0xFF824E6C,
        secondary = 0xFF715764,
        tertiary = 0xFF6E557D,
        error = 0xFFBB0947,
        neutral = 0xFFFFFBFF,
    ),
    PalettePreset(
        id = "kelivo_terracotta",
        nameRes = R.string.palette_name_kelivo_terracotta,
        primary = 0xFF8B4E3B,
        secondary = 0xFF77574D,
        tertiary = 0xFF745B25,
        error = 0xFFB91F01,
        neutral = 0xFFFFFBFF,
    ),
    PalettePreset(
        id = "kelivo_monochrome",
        nameRes = R.string.palette_name_kelivo_monochrome,
        primary = 0xFF000000,
        secondary = 0xFF1A1A1A,
        tertiary = 0xFF2E2E2E,
        error = 0xFF000000,
        neutral = 0xFFF7F7F7,
    ),
    PalettePreset(
        id = "kelivo_doc",
        nameRes = R.string.palette_name_kelivo_doc,
        primary = 0xFF00B96B,
        secondary = 0xFF509177,
        tertiary = 0xFF9C96F9,
        error = 0xFFFF0000,
        neutral = 0xFFF7F7F7,
    ),
)

fun findPalettePreset(id: String): PalettePreset =
    PalettePresets.find { it.id == id } ?: PalettePresets.first()

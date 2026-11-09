package me.rerere.rikkahub.ui.theme

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.MiuixColorSource
import me.rerere.rikkahub.data.datastore.MiuixPalette
import me.rerere.rikkahub.data.datastore.MiuixPressFeedback
import me.rerere.rikkahub.data.datastore.UiStyle
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 当前这套界面走哪套组件。原生页面读它决定要不要换实现，主题层负责提供。 */
val LocalUiStyle = compositionLocalOf { UiStyle.NATIVE }

/** Miuix 组件的按压反馈方式，跟着设置走。 */
val LocalMiuixPressFeedback = compositionLocalOf { MiuixPressFeedback.SINK }

/** Miuix 模式下的卡片圆角。 */
val LocalMiuixCornerRadius = compositionLocalOf { 16.dp }

/**
 * 整棵树用的 Miuix 主题：在 RikkahubTheme 里按「界面风格」决定包不包。
 *
 * 配色默认不从种子色重新生成，而是把 L3Hub 已经算好的位置层（[UiSlots]）逐项搬过去。
 * 用 Monet 从主色推导的话，暗色下会得到一套几乎全黑的面板加一块高饱和的强调色，
 * 而且用户改调色板时 Miuix 这边的底色不跟着动——映射过去则是同一套底色，
 * 只有形状、水波纹、按压反馈、玻璃是 Miuix 的。
 */
@Composable
internal fun MiuixRootTheme(
    dark: Boolean,
    colorSource: MiuixColorSource,
    palette: MiuixPalette,
    overscroll: Boolean,
    content: @Composable () -> Unit,
) {
    MiuixTheme(
        controller = rememberMiuixController(dark = dark, colorSource = colorSource, palette = palette),
    ) {
        // MiuixTheme 自己会提供 MiuixOverscrollFactory；用户关掉回弹时在它内层再压回 null
        CompositionLocalProvider(
            LocalOverscrollFactory provides if (overscroll) LocalOverscrollFactory.current else null,
            content = content,
        )
    }
}

@Composable
private fun rememberMiuixController(
    dark: Boolean,
    colorSource: MiuixColorSource,
    palette: MiuixPalette,
): ThemeController {
    val scheme = MaterialTheme.colorScheme
    val slots = LocalUiSlots.current
    return remember(dark, colorSource, palette, scheme, slots) {
        when (colorSource) {
            MiuixColorSource.THEME -> {
                val mapped = miuixColorsFrom(slots, scheme)
                ThemeController(
                    colorSchemeMode = if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                    lightColors = mapped,
                    darkColors = mapped,
                )
            }

            MiuixColorSource.GENERATED -> ThemeController(
                colorSchemeMode = if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                keyColor = scheme.primary,
                paletteStyle = palette.toMiuixStyle(),
                colorSpec = ThemeColorSpec.Spec2025,
            )

            MiuixColorSource.DEFAULT -> ThemeController(
                colorSchemeMode = if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                lightColors = lightColorScheme(),
                darkColors = darkColorScheme(),
            )
        }
    }
}

private fun MiuixPalette.toMiuixStyle(): ThemePaletteStyle = when (this) {
    MiuixPalette.TONAL_SPOT -> ThemePaletteStyle.TonalSpot
    MiuixPalette.NEUTRAL -> ThemePaletteStyle.Neutral
    MiuixPalette.VIBRANT -> ThemePaletteStyle.Vibrant
    MiuixPalette.EXPRESSIVE -> ThemePaletteStyle.Expressive
    MiuixPalette.RAINBOW -> ThemePaletteStyle.Rainbow
    MiuixPalette.FRUIT_SALAD -> ThemePaletteStyle.FruitSalad
    MiuixPalette.MONOCHROME -> ThemePaletteStyle.Monochrome
    MiuixPalette.FIDELITY -> ThemePaletteStyle.Fidelity
    MiuixPalette.CONTENT -> ThemePaletteStyle.Content
}

/**
 * L3Hub 的配色 → Miuix 的配色。
 *
 * 底色、卡片、浮层、分隔线都取自 [UiSlots]：那是这套界面「一个位置一个颜色」的层，
 * 调色板和 AMOLED 压黑都作用在它上面，跟着它走就能一起变。强调色仍取 Material 的主色，
 * 开关轨道、选中态才够醒目。
 */
private fun miuixColorsFrom(slots: UiSlots, scheme: ColorScheme): Colors {
    val base = if (scheme.background.luminance() < 0.5f) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = scheme.primary,
        onPrimary = scheme.onPrimary,
        primaryVariant = scheme.primaryContainer,
        onPrimaryVariant = scheme.onPrimaryContainer,
        primaryContainer = scheme.primaryContainer,
        onPrimaryContainer = scheme.onPrimaryContainer,
        secondary = scheme.secondary,
        onSecondary = scheme.onSecondary,
        secondaryVariant = scheme.secondaryContainer,
        onSecondaryVariant = scheme.onSecondaryContainer,
        secondaryContainer = scheme.secondaryContainer,
        onSecondaryContainer = scheme.onSecondaryContainer,
        secondaryContainerVariant = slots.listItem,
        onSecondaryContainerVariant = slots.bodyText,
        tertiaryContainer = scheme.tertiaryContainer,
        onTertiaryContainer = scheme.onTertiaryContainer,
        tertiaryContainerVariant = slots.listItem,
        background = slots.pageBackground,
        onBackground = slots.bodyText,
        onBackgroundVariant = slots.captionText,
        surface = slots.pageBackground,
        onSurface = slots.bodyText,
        surfaceVariant = slots.listItem,
        onSurfaceSecondary = slots.captionText,
        onSurfaceVariantSummary = slots.captionText,
        onSurfaceVariantActions = slots.accentText,
        disabledOnSurface = slots.captionText.copy(alpha = 0.38f),
        surfaceContainer = slots.card,
        onSurfaceContainer = slots.titleText,
        onSurfaceContainerVariant = slots.bodyText,
        surfaceContainerHigh = slots.panel,
        onSurfaceContainerHigh = slots.titleText,
        surfaceContainerHighest = slots.listItem,
        onSurfaceContainerHighest = slots.bodyText,
        outline = slots.border,
        dividerLine = slots.divider,
        error = scheme.error,
        onError = scheme.onError,
        errorContainer = slots.errorBackground,
        onErrorContainer = slots.errorOnBackground,
        windowDimming = slots.scrim,
    )
}

/** 设置里的三档映射到 Miuix 的按压反馈枚举。 */
@Composable
@ReadOnlyComposable
fun miuixPressFeedbackType(): PressFeedbackType = when (LocalMiuixPressFeedback.current) {
    MiuixPressFeedback.NONE -> PressFeedbackType.None
    MiuixPressFeedback.SINK -> PressFeedbackType.Sink
    MiuixPressFeedback.TILT -> PressFeedbackType.Tilt
}

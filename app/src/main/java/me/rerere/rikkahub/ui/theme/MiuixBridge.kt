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
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.MiuixPressFeedback
import me.rerere.rikkahub.data.datastore.UiStyle
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
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
 * 配色不从种子色现生成，而是把 L3Hub 已经算好的 Material 配色逐项映射过去。用 Monet 从主色
 * 重新推导的话，暗色下会得到一套几乎全黑的面板加一块高饱和的强调色，跟旁边没迁过去的页面
 * 完全不是一个调子；映射过来则是同一套底色，只有形状、水波纹、按压反馈是 Miuix 的。
 */
@Composable
internal fun MiuixRootTheme(
    dark: Boolean,
    followThemeColor: Boolean,
    overscroll: Boolean,
    content: @Composable () -> Unit,
) {
    MiuixTheme(
        controller = rememberMiuixController(dark = dark, followThemeColor = followThemeColor),
    ) {
        // MiuixTheme 自己会提供 MiuixOverscrollFactory；用户关掉回弹时在它内层再压回 null
        CompositionLocalProvider(
            LocalOverscrollFactory provides if (overscroll) LocalOverscrollFactory.current else null,
            content = content,
        )
    }
}

@Composable
private fun rememberMiuixController(dark: Boolean, followThemeColor: Boolean): ThemeController {
    val scheme = MaterialTheme.colorScheme
    return remember(dark, followThemeColor, scheme) {
        val mapped = miuixColorsFrom(scheme, dark)
        ThemeController(
            colorSchemeMode = if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            lightColors = if (followThemeColor) mapped else lightColorScheme(),
            darkColors = if (followThemeColor) mapped else darkColorScheme(),
        )
    }
}

/**
 * L3Hub 的配色 → Miuix 的配色。
 *
 * 只覆盖承载视觉的那几项；Miuix 特有的那批角色（滑块的键点、窗口压暗、各种 disabled 态）
 * 留默认值，它们在 L3Hub 这套配色里没有对应项，硬凑反而会脏。
 */
private fun miuixColorsFrom(scheme: ColorScheme, dark: Boolean): Colors {
    val base = if (dark) darkColorScheme() else lightColorScheme()
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
        secondaryContainerVariant = scheme.surfaceContainerHigh,
        onSecondaryContainerVariant = scheme.onSurfaceVariant,
        tertiaryContainer = scheme.tertiaryContainer,
        onTertiaryContainer = scheme.onTertiaryContainer,
        tertiaryContainerVariant = scheme.surfaceContainerHigh,
        background = scheme.background,
        onBackground = scheme.onBackground,
        onBackgroundVariant = scheme.onSurfaceVariant,
        surface = scheme.surface,
        onSurface = scheme.onSurface,
        surfaceVariant = scheme.surfaceVariant,
        onSurfaceSecondary = scheme.onSurfaceVariant,
        onSurfaceVariantSummary = scheme.onSurfaceVariant,
        onSurfaceVariantActions = scheme.onSurfaceVariant,
        disabledOnSurface = scheme.onSurface.copy(alpha = 0.38f),
        surfaceContainer = scheme.surfaceContainer,
        onSurfaceContainer = scheme.onSurface,
        onSurfaceContainerVariant = scheme.onSurfaceVariant,
        surfaceContainerHigh = scheme.surfaceContainerHigh,
        onSurfaceContainerHigh = scheme.onSurface,
        surfaceContainerHighest = scheme.surfaceContainerHighest,
        onSurfaceContainerHighest = scheme.onSurface,
        outline = scheme.outline,
        dividerLine = scheme.outlineVariant,
        error = scheme.error,
        onError = scheme.onError,
        errorContainer = scheme.errorContainer,
        onErrorContainer = scheme.onErrorContainer,
        windowDimming = Color.Black.copy(alpha = 0.32f),
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

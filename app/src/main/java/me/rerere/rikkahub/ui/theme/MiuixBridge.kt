package me.rerere.rikkahub.ui.theme

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import me.rerere.rikkahub.data.datastore.MiuixPressFeedback
import me.rerere.rikkahub.data.datastore.UiStyle
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 当前这套界面走哪套组件。原生页面读它决定要不要换实现，主题层负责提供。 */
val LocalUiStyle = compositionLocalOf { UiStyle.NATIVE }

/** Miuix 组件的按压反馈方式，跟着设置走。 */
val LocalMiuixPressFeedback = compositionLocalOf { MiuixPressFeedback.SINK }

/**
 * 把 Miuix 接进 L3Hub 现在这套主题。
 *
 * Miuix 的 MiuixTheme 与 MaterialTheme 是两套并列的 CompositionLocal，互不覆盖：哪个界面要用
 * Miuix 组件，就在那一片外面包一层这个。
 *
 * 如果外层主题已经因为「界面风格 = Miuix」包过一层，这里就直接放行——同一个页面不该套两层
 * 主题，套了内层的色板会重新算一遍，白费。
 *
 * 色相取当前 MaterialTheme 的主色做种子，明暗取 App 自己的暗色开关（LocalDarkMode）：
 * 这样切到 Miuix 页面时，色调跟旁边页面是一路的，深浅也跟用户的开关一致。
 */
@Composable
fun L3MiuixTheme(content: @Composable () -> Unit) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        content()
        return
    }
    MiuixTheme(controller = rememberMiuixController(), content = content)
}

/**
 * 整棵树用的 Miuix 主题：在 RikkahubTheme 里按「界面风格」决定包不包。
 *
 * 跟 [L3MiuixTheme] 的差别是它不吃 LocalUiStyle——这一层正是定义 LocalUiStyle 的地方，
 * 读自己提供的值会拿到默认的 NATIVE。
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
private fun rememberMiuixController(
    dark: Boolean = LocalDarkMode.current,
    followThemeColor: Boolean = true,
): ThemeController {
    val keyColor = MaterialTheme.colorScheme.primary
    return remember(dark, followThemeColor, keyColor) {
        ThemeController(
            colorSchemeMode = if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = if (followThemeColor) keyColor else null,
        )
    }
}

/** 设置里的三档映射到 Miuix 的按压反馈枚举。 */
@Composable
@ReadOnlyComposable
fun miuixPressFeedbackType(): PressFeedbackType = when (LocalMiuixPressFeedback.current) {
    MiuixPressFeedback.NONE -> PressFeedbackType.None
    MiuixPressFeedback.SINK -> PressFeedbackType.Sink
    MiuixPressFeedback.TILT -> PressFeedbackType.Tilt
}

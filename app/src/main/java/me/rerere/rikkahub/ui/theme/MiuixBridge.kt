package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 把 Miuix 接进 L3Hub 现在这套主题。
 *
 * Miuix 的 MiuixTheme 与 MaterialTheme 是两套并列的 CompositionLocal，互不覆盖：哪个界面要用
 * Miuix 组件，就在那一片外面包一层这个，其余界面不受影响。
 *
 * 色相取当前 MaterialTheme 的主色做种子，明暗取 App 自己的暗色开关（LocalDarkMode）——
 * 这样切到 Miuix 页面时，色调跟旁边页面是一路的，深浅也跟用户的开关一致。
 */
@Composable
fun L3MiuixTheme(content: @Composable () -> Unit) {
    val dark = LocalDarkMode.current
    val keyColor = MaterialTheme.colorScheme.primary
    val controller = remember(dark, keyColor) {
        ThemeController(
            colorSchemeMode = if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = keyColor,
        )
    }
    MiuixTheme(controller = controller, content = content)
}

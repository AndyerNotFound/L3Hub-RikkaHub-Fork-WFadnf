package me.rerere.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.moriafly.salt.ui.SaltDynamicColors
import com.moriafly.salt.ui.SaltTheme

/** A separate window can tint its own surface but cannot use sources from its parent window. */
@Immutable
data class SeparateWindowColors(
    val dialog: Color,
    val sheet: Color,
    val menu: Color,
    val menuGroup: Color,
    val menuBorder: BorderStroke?,
)

val LocalSeparateWindowColors = compositionLocalOf<SeparateWindowColors?> { null }
val LocalSeparateWindow = compositionLocalOf { false }

@Composable
fun SeparateWindowContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSeparateWindow provides true, content = content)
}

@Composable
internal fun SheetWindowTheme(content: @Composable () -> Unit) {
    val background = LocalSeparateWindowColors.current?.sheet
    SeparateWindowContent {
        if (background == null) {
            content()
        } else {
            val parent = SaltTheme.colors
            val colors = remember(parent, background) {
                val sheet = parent.copy(background = background)
                SaltDynamicColors(light = sheet, dark = sheet)
            }
            // Salt owns and clips the panel. Supply its actual panel color rather than drawing over it.
            SaltTheme(configs = SaltTheme.configs, dynamicColors = colors, content = content)
        }
    }
}

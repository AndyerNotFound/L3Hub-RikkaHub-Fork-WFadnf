package me.rerere.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

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

package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/** 终端风格会话界面的配色，黑底绿字，强调色用琥珀色 */
object TerminalPalette {
    val Background = Color(0xFF050805)
    val Surface = Color(0xFF0A100B)
    val SurfaceHigh = Color(0xFF111A12)
    val Green = Color(0xFF7CFF9B)
    val GreenDim = Color(0xFF4D9E61)
    val Amber = Color(0xFFFFD166)
    val Error = Color(0xFFFF7B72)
    val Border = Color(0xFF245632)
}

val TerminalColorScheme = darkColorScheme(
    primary = TerminalPalette.Green,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF12331B),
    onPrimaryContainer = Color(0xFFB9FFC9),
    secondary = TerminalPalette.Amber,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3A2E12),
    onSecondaryContainer = Color(0xFFFFE7A8),
    tertiary = Color(0xFF7AD7FF),
    background = TerminalPalette.Background,
    onBackground = Color(0xFFC9FFD5),
    surface = TerminalPalette.Surface,
    onSurface = Color(0xFFC9FFD5),
    surfaceVariant = TerminalPalette.SurfaceHigh,
    onSurfaceVariant = Color(0xFF79B889),
    surfaceContainerLowest = Color(0xFF030503),
    surfaceContainerLow = TerminalPalette.Surface,
    surfaceContainer = TerminalPalette.SurfaceHigh,
    surfaceContainerHigh = Color(0xFF162218),
    surfaceContainerHighest = Color(0xFF1B291D),
    outline = TerminalPalette.Border,
    outlineVariant = Color(0xFF173522),
    error = TerminalPalette.Error,
    onError = Color.Black,
)

private fun TextStyle.monospace() = copy(fontFamily = FontFamily.Monospace)

fun terminalTypography(base: Typography): Typography = base.copy(
    displayLarge = base.displayLarge.monospace(),
    displayMedium = base.displayMedium.monospace(),
    displaySmall = base.displaySmall.monospace(),
    headlineLarge = base.headlineLarge.monospace(),
    headlineMedium = base.headlineMedium.monospace(),
    headlineSmall = base.headlineSmall.monospace(),
    titleLarge = base.titleLarge.monospace(),
    titleMedium = base.titleMedium.monospace(),
    titleSmall = base.titleSmall.monospace(),
    bodyLarge = base.bodyLarge.monospace(),
    bodyMedium = base.bodyMedium.monospace(),
    bodySmall = base.bodySmall.monospace(),
    labelLarge = base.labelLarge.monospace(),
    labelMedium = base.labelMedium.monospace(),
    labelSmall = base.labelSmall.monospace(),
)

@Composable
fun TerminalTheme(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = terminalTypography(MaterialTheme.typography),
        content = content,
    )
}

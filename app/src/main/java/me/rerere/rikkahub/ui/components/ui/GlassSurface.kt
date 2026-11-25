package me.rerere.rikkahub.ui.components.ui

import android.os.Build
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import me.rerere.rikkahub.ui.context.LocalSettings

/** Pages provide a backdrop. Chrome that can sample chat content supplies its source explicitly. */
val LocalGlassHazeState = compositionLocalOf<HazeState?> { null }

/** As in Rikkahub Revised (AGPL-3.0), the caller supplies the only tint after the backdrop blur. */
@Composable
internal fun Modifier.blurBackdrop(hazeState: HazeState, radius: Dp): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !radius.value.isFinite() || radius <= 0.dp) return this
    val style = remember(radius) {
        HazeBlurStyle {
            blurRadius(radius)
            backgroundColor(Color.Transparent)
            colorEffects(emptyList())
            fallbackColorEffect(null)
            noiseFactor(0f)
        }
    }
    return hazeBlur(input = HazeInput.Sources(hazeState), style = style)
}

/** Small controls inherit the page tint without each recording and blurring the backdrop. */
@Composable
private fun usePageControlTint(): Boolean = LocalSettings.current.advancedAppearanceSetting.run {
    enableAdvancedAppearance && !pageSurfaceStyle.opaqueMaterial
}

@Composable
fun glassColor(base: Color): Color {
    if (!usePageControlTint()) return base
    val appearance = LocalSettings.current.advancedAppearanceSetting
    return base.copy(alpha = base.alpha * appearanceSurfaceOpacity(appearance.pageSurfaceOpacity, .35f))
}

/** The tint for small controls floating over messages; keep their outlines readable without Haze. */
@Composable
internal fun overlayControlColor(base: Color, floor: Float = .6f, fallback: Color = base): Color {
    val appearance = LocalSettings.current.advancedAppearanceSetting
    if (!appearance.enableAdvancedAppearance) return fallback
    if (appearance.overlaySurfaceStyle.opaqueMaterial) return base
    val tint = appearance.materialTint(base, MaterialTheme.colorScheme.primary, appearance.overlaySurfaceStyle)
    return tint.copy(alpha = tint.alpha * appearanceSurfaceOpacity(appearance.overlaySurfaceOpacity, floor))
}

@Composable
fun glassSegmentedButtonColors() = if (usePageControlTint()) {
    SegmentedButtonDefaults.colors(
        activeContainerColor = glassColor(MaterialTheme.colorScheme.surface),
        activeContentColor = MaterialTheme.colorScheme.primary,
    )
} else {
    SegmentedButtonDefaults.colors()
}

@Composable
fun glassSwitchColors() = if (usePageControlTint()) {
    SwitchDefaults.colors(
        uncheckedTrackColor = glassColor(MaterialTheme.colorScheme.surfaceContainerHighest),
        uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .5f),
    )
} else {
    SwitchDefaults.colors()
}

@Composable
fun glassFilterChipColors() = if (usePageControlTint()) {
    FilterChipDefaults.filterChipColors(
        containerColor = glassColor(MaterialTheme.colorScheme.surfaceContainerLow),
        selectedContainerColor = glassColor(MaterialTheme.colorScheme.secondaryContainer),
    )
} else {
    FilterChipDefaults.filterChipColors()
}

@Composable
fun glassAssistChipColors() = if (usePageControlTint()) {
    AssistChipDefaults.assistChipColors(
        containerColor = glassColor(MaterialTheme.colorScheme.surfaceContainerLow),
    )
} else {
    AssistChipDefaults.assistChipColors()
}

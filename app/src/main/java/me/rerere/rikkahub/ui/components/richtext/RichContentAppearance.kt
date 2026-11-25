package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.data.datastore.RichContentStyle
import me.rerere.rikkahub.ui.components.ui.AppearanceMaterial
import me.rerere.rikkahub.ui.components.ui.advancedMaterial
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.ui.charts.ChartCard
import me.rerere.ui.charts.ChartSpec

internal fun AdvancedAppearanceSetting.richContentMaterial(streaming: Boolean = false): AppearanceMaterial? {
    if (!enableAdvancedAppearance) return null
    val surface = when (richContentStyle) {
        RichContentStyle.OUTLINED -> BackgroundSurfaceStyle.TRANSLUCENT
        RichContentStyle.TRANSLUCENT -> BackgroundSurfaceStyle.FROSTED
        RichContentStyle.LIQUID_GLASS -> BackgroundSurfaceStyle.LIQUID_GLASS
    }
    val radius = richContentSurfaceBlurRadius ?: when {
        surface == BackgroundSurfaceStyle.LIQUID_GLASS -> 16f
        surface == BackgroundSurfaceStyle.FROSTED -> 8f
        else -> 0f
    }
    return AppearanceMaterial(
        style = surface,
        opacity = richContentSurfaceOpacity * if (richContentStyle == RichContentStyle.OUTLINED) .62f else 1f,
        radius = radius,
        effects = enableRichContentPerformanceEffects && !streaming,
    )
}

@Composable
internal fun RichContentStyle.outline(): BorderStroke = BorderStroke(
    1.dp,
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = when (this) {
        RichContentStyle.OUTLINED -> .94f
        RichContentStyle.TRANSLUCENT -> .55f
        RichContentStyle.LIQUID_GLASS -> .4f
    }),
)

@Composable
internal fun richContentHeaderColor(): Color {
    val appearance = LocalSettings.current.advancedAppearanceSetting
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    return if (!appearance.enableAdvancedAppearance) base else {
        base.copy(alpha = .32f + appearance.richContentSurfaceOpacity * .42f)
    }
}

@Composable
internal fun Modifier.richContentFrame(streaming: Boolean, withLegacyFrame: Boolean = true): Modifier {
    val appearance = LocalSettings.current.advancedAppearanceSetting
    val material = appearance.richContentMaterial(streaming)
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    return when {
        material != null -> advancedMaterial(shape, scheme.surfaceContainer, material)
            .border(appearance.richContentStyle.outline(), shape)
        withLegacyFrame -> border(1.dp, scheme.outlineVariant, shape).clip(shape).background(scheme.surfaceContainer)
        else -> this
    }
}

@Composable
internal fun RichChartCard(spec: ChartSpec, modifier: Modifier = Modifier) {
    val appearance = LocalSettings.current.advancedAppearanceSetting
    val material = appearance.richContentMaterial()
    val scheme = MaterialTheme.colorScheme
    val shape: Shape = RoundedCornerShape(16.dp)
    ChartCard(
        spec = spec,
        modifier = if (material == null) modifier else modifier.advancedMaterial(shape, scheme.surfaceContainer, material),
        containerColor = if (material == null) scheme.surface else Color.Transparent,
        border = if (material == null) BorderStroke(1.dp, scheme.outlineVariant) else appearance.richContentStyle.outline(),
    )
}

package me.rerere.rikkahub.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.data.datastore.isFullscreenPreviewBackgroundActive
import me.rerere.rikkahub.data.datastore.isGlobalBackgroundVisible
import me.rerere.rikkahub.data.datastore.isPageBackgroundActive
import me.rerere.rikkahub.ui.components.ui.AppearanceMaterial
import me.rerere.rikkahub.ui.components.ui.LocalAdvancedPageMaterial
import me.rerere.rikkahub.ui.components.ui.LocalGlassHazeState
import me.rerere.rikkahub.ui.components.ui.LocalTopBarHazeState
import me.rerere.rikkahub.ui.components.ui.pageMaterial
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.AppearancePreviewBackdrop
import me.rerere.rikkahub.ui.theme.LocalAppearanceImage
import me.rerere.rikkahub.ui.theme.LocalUiSlots

internal val LocalRouteBackdropVisible = compositionLocalOf { false }

/** A source belongs to one navigation pane; a translucent detail never samples the list behind it. */
@Composable
internal fun RouteAppearance(screen: Screen, content: @Composable () -> Unit) {
    val settings = LocalSettings.current
    val appearance = settings.advancedAppearanceSetting
    if (screen is Screen.Chat) {
        content()
        return
    }
    val preview = screen is Screen.WebView && (screen.offlineMessagePreview || screen.url.isEmpty())
    val usesBackdrop = if (preview) {
        settings.isFullscreenPreviewBackgroundActive()
    } else {
        settings.isPageBackgroundActive()
    }
    RouteBackdrop(
        appearance = appearance,
        useImage = usesBackdrop,
        useMica = appearance.enableAdvancedAppearance && !preview && appearance.pageSurfaceStyle == BackgroundSurfaceStyle.MICA,
        material = appearance.pageMaterial().takeIf { appearance.enableAdvancedAppearance },
        topBarPageContentSource = screen == Screen.SettingPreferencesMessageStyle,
        content = content,
    )
}

@Composable
internal fun SettingRailAppearance(content: @Composable () -> Unit) {
    val settings = LocalSettings.current
    val appearance = settings.advancedAppearanceSetting
    RouteBackdrop(
        appearance = appearance,
        useImage = settings.isGlobalBackgroundVisible() && appearance.enableNavigationGlass && !appearance.navigationSurfaceStyle.opaqueMaterial,
        content = content,
    )
}

@Composable
private fun RouteBackdrop(
    appearance: AdvancedAppearanceSetting,
    useImage: Boolean,
    useMica: Boolean = false,
    material: AppearanceMaterial? = null,
    topBarPageContentSource: Boolean = false,
    content: @Composable () -> Unit,
) {
    val image = if (useImage) LocalAppearanceImage.current?.image else null
    val visible = image != null || useMica
    val haze = if (visible) rememberHazeState() else null
    // Keep the top bar's content source separate from page surfaces to avoid sampling them recursively.
    val settings = LocalSettings.current
    val topBarNeedsHazeSource = visible || topBarPageContentSource
    val topBarHaze = if (
        material != null && settings.displaySetting.showTopBarBackground && topBarNeedsHazeSource
    ) rememberHazeState() else null
    // A transitioning pane keeps its own backing, including when part of it covers another pane.
    Box(Modifier.fillMaxSize().clipToBounds()) {
        if (haze != null) {
            val source = Modifier.matchParentSize()
                .hazeSource(haze)
                .then(topBarHaze?.let { Modifier.hazeSource(it) } ?: Modifier)
            if (image != null) {
                AppearancePreviewBackdrop(image, appearance, source)
            } else {
                val base = LocalUiSlots.current.pageBackground
                val accent = MaterialTheme.colorScheme.primary
                val mica = lerp(base, accent, .2f * appearance.micaTintStrength)
                Box(source.background(Brush.verticalGradient(listOf(mica, base))))
            }
        }
        CompositionLocalProvider(
            LocalRouteBackdropVisible provides visible,
            LocalAdvancedPageMaterial provides material,
            LocalGlassHazeState provides haze,
            LocalTopBarHazeState provides topBarHaze,
            LocalContentColor provides if (visible) MaterialTheme.colorScheme.onBackground else LocalContentColor.current,
        ) { content() }
    }
}

@Composable
internal fun appearancePageContainerColor(default: Color = MaterialTheme.colorScheme.background): Color =
    if (LocalRouteBackdropVisible.current) Color.Transparent else default

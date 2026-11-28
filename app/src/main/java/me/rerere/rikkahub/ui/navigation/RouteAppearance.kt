package me.rerere.rikkahub.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.Screen

/** Keep each navigation pane clipped without loading a wallpaper or recording Haze sources. */
@Composable
internal fun RouteAppearance(screen: Screen, content: @Composable () -> Unit) {
    if (screen is Screen.Chat) {
        content()
    } else {
        PlainRoutePane(content)
    }
}

@Composable
internal fun SettingRailAppearance(content: @Composable () -> Unit) {
    PlainRoutePane(content)
}

@Composable
private fun PlainRoutePane(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().clipToBounds()) { content() }
}

@Composable
internal fun appearancePageContainerColor(default: Color = MaterialTheme.colorScheme.background): Color = default

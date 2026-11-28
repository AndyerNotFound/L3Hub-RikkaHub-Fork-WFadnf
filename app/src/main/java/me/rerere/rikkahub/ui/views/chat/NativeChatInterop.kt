package me.rerere.rikkahub.ui.views.chat

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.LocalDarkMode

/** View inflation needs an AppCompat/Material theme; the Activity deliberately has neither. */
@Composable
internal fun rememberNativeChatContext(): Context {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val dark = LocalDarkMode.current
    return remember(context, configuration, density.density, density.fontScale, dark) {
        val viewConfiguration = Configuration(configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            // XML dp/sp must use the same scaled coordinates as their Compose parent.
            densityDpi = (density.density * 160f).toInt().coerceAtLeast(1)
            fontScale = density.fontScale
        }
        // Keep the Activity in the base-context chain (IME, window services, findActivity),
        // overriding resources on the wrapper instead of replacing the base with ContextImpl.
        ContextThemeWrapper(context, R.style.Theme_L3Hub_NativeChat).apply {
            applyOverrideConfiguration(viewConfiguration)
        }
    }
}

/** Explicit runtime palette; native render methods do not mutate or persist user settings. */
@Composable
internal fun nativeChatTheme(): NativeChatTheme {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        NativeChatTheme(
            scheme.primary.toArgb(), scheme.onPrimary.toArgb(),
            scheme.primaryContainer.toArgb(), scheme.onPrimaryContainer.toArgb(),
            scheme.secondaryContainer.toArgb(), scheme.onSecondaryContainer.toArgb(),
            scheme.tertiaryContainer.toArgb(), scheme.onTertiaryContainer.toArgb(),
            scheme.surface.toArgb(), scheme.onSurface.toArgb(), scheme.onSurfaceVariant.toArgb(),
            scheme.surfaceContainer.toArgb(),
            scheme.surfaceContainerLow.toArgb(), scheme.surfaceContainerHigh.toArgb(),
            scheme.surfaceContainerHighest.toArgb(), scheme.outline.toArgb(),
            scheme.outlineVariant.toArgb(), scheme.error.toArgb(),
            scheme.errorContainer.toArgb(), scheme.onErrorContainer.toArgb(),
        )
    }
}

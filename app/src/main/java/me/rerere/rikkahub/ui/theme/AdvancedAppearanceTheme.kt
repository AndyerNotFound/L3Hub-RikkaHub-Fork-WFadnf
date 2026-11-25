package me.rerere.rikkahub.ui.theme

import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dynamiccolor.Variant
import hct.Hct
import me.rerere.rikkahub.data.datastore.AppearanceColorStyle
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.TextColorMode
import kotlin.math.max
import kotlin.math.min

private fun AppearanceColorStyle.variant() = when (this) {
    AppearanceColorStyle.TONAL_SPOT -> Variant.TONAL_SPOT
    AppearanceColorStyle.NEUTRAL -> Variant.NEUTRAL
    AppearanceColorStyle.VIBRANT -> Variant.VIBRANT
    AppearanceColorStyle.EXPRESSIVE -> Variant.EXPRESSIVE
    AppearanceColorStyle.MONOCHROME -> Variant.MONOCHROME
}

@Composable
internal fun rememberAdvancedScheme(
    settings: Settings,
    base: ColorScheme,
    dark: Boolean,
    image: AppearanceImageColors?,
): ColorScheme {
    val appearance = settings.advancedAppearanceSetting
    if (!appearance.enableAdvancedAppearance) return base
    val autoSeed = image?.accent.takeIf { appearance.enableAutoAccent }
    val seed = autoSeed ?: base.primary.toArgb()
    val style = appearance.colorStyle
    val contrast = appearance.colorContrast
    return remember(base, dark, autoSeed, seed, style, contrast) {
        if (autoSeed == null && style == AppearanceColorStyle.TONAL_SPOT && contrast == 0f) base
        else CustomTheme(id = "appearance", primaryColorArgb = seed.toLong()).generateColorScheme(
            dark = dark, variant = style.variant(), contrastLevel = contrast.toDouble(),
        )
    }
}

@Composable
internal fun rememberAppearanceWallpaperSeed(enabled: Boolean, provided: Int?, providedId: Int?): Int? {
    if (!enabled) return null
    if (Build.VERSION.SDK_INT < 27) return provided
    val context = LocalContext.current
    var seed by remember(context) { mutableStateOf<Int?>(null) }
    var wallpaperId by remember(context) { mutableStateOf<Int?>(null) }
    DisposableEffect(context) {
        val manager = context.getSystemService(WallpaperManager::class.java)
        fun updateId() {
            wallpaperId = runCatching { manager?.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrNull()?.takeIf { it > 0 }
        }
        val listener = WallpaperManager.OnColorsChangedListener { colors, flags ->
            if (flags and WallpaperManager.FLAG_SYSTEM != 0) {
                updateId()
                seed = colors?.primaryColor?.toArgb()
            }
        }
        val registered = runCatching {
            manager?.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            manager != null
        }.getOrDefault(false)
        updateId()
        seed = runCatching { manager?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb() }.getOrNull()
        onDispose { if (registered) runCatching { manager?.removeOnColorsChangedListener(listener) } }
    }
    if (provided != null && (providedId == null || wallpaperId == null || providedId == wallpaperId)) return provided
    if (seed != null) return seed
    return if (Build.VERSION.SDK_INT >= 31) {
        runCatching { context.getColor(android.R.color.system_accent1_500) }.getOrNull()
    } else null
}

internal fun readableAppearanceText(base: Color, fallback: Color, seed: Int?): Color {
    val light = Color.White
    val dark = Color.Black
    val bg = base.luminance()
    fun contrast(color: Color): Float {
        val fg = color.luminance()
        return (max(fg, bg) + .05f) / (min(fg, bg) + .05f)
    }
    val neutral = if (contrast(light) > contrast(dark)) light else dark
    if (seed != null) {
        val hct = Hct.fromInt(seed)
        val tones = if (neutral == light) listOf(88.0, 92.0, 96.0, 98.0) else listOf(28.0, 23.0, 16.0, 8.0)
        val target = min(7f, contrast(neutral))
        for (tone in tones) {
            val color = Color(Hct.from(hct.hue, hct.chroma.coerceIn(12.0, 44.0), tone).toInt())
            if (contrast(color) >= target - .05f) return color
        }
    }
    return if (seed != null && contrast(fallback) >= min(7f, contrast(neutral))) fallback else neutral
}

internal fun ColorScheme.withAppearanceText(mode: TextColorMode, seed: Int?): ColorScheme {
    if (mode == TextColorMode.THEME || (mode != TextColorMode.AUTO_CLEAR && seed == null)) return this
    return copy(
        onBackground = readableAppearanceText(background, onBackground, seed),
        onSurface = readableAppearanceText(surface, onSurface, seed),
        onSurfaceVariant = readableAppearanceText(surfaceVariant, onSurfaceVariant, seed),
    )
}

/** Use enough theme tint even on the highest-contrast part of a photo. */
internal fun readableBackdropColor(base: Color, foreground: Color, minimum: Float): Color {
    val fg = foreground.luminance()
    val worst = if (fg > .179f) Color.White else Color.Black
    fun contrast(bg: Color): Float {
        val luminance = bg.luminance()
        return (max(fg, luminance) + .05f) / (min(fg, luminance) + .05f)
    }
    val tint = if (contrast(base) >= 4.5f) base else if (fg > .179f) Color.Black else Color.White
    for (alpha in listOf(minimum, .68f, .78f, .88f)) {
        if (alpha < minimum) continue
        val candidate = tint.copy(alpha = alpha)
        if (contrast(candidate.compositeOver(worst)) >= 4.5f) return candidate
    }
    return tint.copy(alpha = 1f)
}

@Composable
internal fun appearanceBackdropScrim(average: Int?, opacity: Float, minimum: Float): Color {
    val base = LocalUiSlots.current.pageBackground
    val mode = me.rerere.rikkahub.ui.context.LocalSettings.current.advancedAppearanceSetting.textColorMode
    val foreground = MaterialTheme.colorScheme.onBackground
    if (mode == TextColorMode.THEME || average == null) return base.copy(alpha = minimum)
    val photo = Color(average).copy(alpha = opacity).compositeOver(base)
    val fg = foreground.luminance()
    for (alpha in listOf(minimum, max(minimum, .54f), max(minimum, .70f), max(minimum, .84f), 1f)) {
        val bg = base.copy(alpha = alpha).compositeOver(photo).luminance()
        if ((max(fg, bg) + .05f) / (min(fg, bg) + .05f) >= 4.5f) return base.copy(alpha = alpha)
    }
    return base
}

@Composable
internal fun rememberSystemReducedMotion(enabled: Boolean): Boolean {
    if (!enabled) return false
    val resolver = LocalContext.current.contentResolver
    fun read() = !ValueAnimator.areAnimatorsEnabled() ||
        SystemSettings.Global.getFloat(resolver, SystemSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        val registered = runCatching {
            resolver.registerContentObserver(SystemSettings.Global.getUriFor(SystemSettings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        }.isSuccess
        onDispose { if (registered) resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

@Composable
internal fun rememberAppearancePowerSave(enabled: Boolean): Boolean {
    if (!enabled) return false
    val context = LocalContext.current
    val power = context.getSystemService(PowerManager::class.java)
    var saving by remember(power) { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context, power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { saving = power?.isPowerSaveMode == true }
        }
        val registered = runCatching {
            ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        }.isSuccess
        onDispose { if (registered) context.unregisterReceiver(receiver) }
    }
    return saving
}

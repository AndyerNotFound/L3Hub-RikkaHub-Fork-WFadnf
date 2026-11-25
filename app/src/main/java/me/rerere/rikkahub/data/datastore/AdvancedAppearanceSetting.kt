package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.model.InspirationAppearance
import me.rerere.rikkahub.data.model.LiquidGlassSettings
import me.rerere.rikkahub.data.model.normalized

const val MIN_GLOBAL_BACKGROUND_BLUR_RADIUS = 4f
const val MAX_GLOBAL_BACKGROUND_BLUR_RADIUS = 40f
const val MIN_NAVIGATION_GLASS_BLUR_RADIUS = 4f
const val MAX_NAVIGATION_GLASS_BLUR_RADIUS = 32f
const val MIN_LIQUID_GLASS_BLUR_RADIUS = 0f
const val MAX_LIQUID_GLASS_BLUR_RADIUS = 24f
const val MIN_CHAT_TEXT_LINE_HEIGHT_RATIO = 1.25f
const val MAX_CHAT_TEXT_LINE_HEIGHT_RATIO = 1.8f
const val MIN_CHAT_PARAGRAPH_SPACING_RATIO = .35f
const val MAX_CHAT_PARAGRAPH_SPACING_RATIO = 1f
const val MIN_CHAT_SUGGESTION_HEIGHT = 72f
const val MAX_CHAT_SUGGESTION_HEIGHT = 220f

/** Adapted from Rikkahub Revised (AGPL-3.0); see docs/port-rikkahub-revised.md. */
@Serializable
data class AdvancedAppearanceSetting(
    val enableAdvancedAppearance: Boolean = false,
    val micaTintStrength: Float = .45f,
    val composerSurfaceStyle: BackgroundSurfaceStyle? = null,
    val enableOverlayPerformanceEffects: Boolean = true,
    val textColorMode: TextColorMode = TextColorMode.THEME,
    val wallpaperTextSeedArgb: Long? = null,
    val wallpaperTextSeedWallpaperId: Int? = null,
    val liquidGlass: LiquidGlassSettings = LiquidGlassSettings(),
    val enableGlobalBackground: Boolean = false,
    val globalBackground: String? = null,
    val globalBackgroundOpacity: Float = 1f,
    val applyGlobalBackgroundToChat: Boolean = false,
    val applyGlobalBackgroundToFullscreenPreview: Boolean = true,
    val globalBackgroundBlurRadius: Float = 20f,
    val pageLiquidGlassBlurRadius: Float = 0f,
    val pageSurfaceOpacity: Float = .68f,
    val pageSurfaceStyle: BackgroundSurfaceStyle = BackgroundSurfaceStyle.TRANSLUCENT,
    // 页面卡片的模糊与壁纸图片本身的模糊分开保存。
    val pageSurfaceBlurRadius: Float = 20f,
    val overlaySurfaceStyle: BackgroundSurfaceStyle = BackgroundSurfaceStyle.OPAQUE,
    val overlaySurfaceOpacity: Float = .82f,
    val overlaySurfaceBlurRadius: Float = 20f,
    val overlayLiquidGlassBlurRadius: Float = 0f,
    val enableNavigationGlass: Boolean = true,
    val navigationSurfaceStyle: BackgroundSurfaceStyle = BackgroundSurfaceStyle.LIQUID_GLASS,
    val navigationGlassOpacity: Float = .72f,
    val navigationGlassBlurRadius: Float = 24f,
    val navigationLiquidGlassBlurRadius: Float = 0f,
    val enableChatDockGlass: Boolean = false,
    val chatDockSurfaceStyle: BackgroundSurfaceStyle = BackgroundSurfaceStyle.LIQUID_GLASS,
    val chatDockGlassOpacity: Float = .58f,
    val chatDockGlassBlurRadius: Float = 0f,
    val chatBubbleStyle: ChatBubbleStyle = ChatBubbleStyle.FROSTED,
    val chatBubbleSurfaceOpacity: Float? = null,
    val chatBubbleBlurRadius: Float? = null,
    val enableChatTextReadability: Boolean = true,
    val chatTextLineHeightRatio: Float = 1.5f,
    val chatParagraphSpacingRatio: Float = .65f,
    val richContentStyle: RichContentStyle = RichContentStyle.TRANSLUCENT,
    val richContentSurfaceOpacity: Float = .62f,
    val richContentSurfaceBlurRadius: Float? = null,
    val enableChatSuggestionSurface: Boolean = true,
    val chatSuggestionSurfaceOpacity: Float = .62f,
    val chatSuggestionBorderOpacity: Float = .72f,
    val chatSuggestionMaxHeight: Float = 128f,
    val inspirationAppearance: InspirationAppearance = InspirationAppearance(),
    val enableAutoAccent: Boolean = false,
    val colorStyle: AppearanceColorStyle = AppearanceColorStyle.TONAL_SPOT,
    val colorContrast: Float = 0f,
    val enableInputPerformanceEffects: Boolean = true,
    val enableTopBarPerformanceEffects: Boolean = true,
    val enableNavigationPerformanceEffects: Boolean = true,
    val enableChatDockPerformanceEffects: Boolean = true,
    val enableBubblePerformanceEffects: Boolean = true,
    val enableRichContentPerformanceEffects: Boolean = true,
    val enableGradientPerformanceEffects: Boolean = true,
    val gradientRendererMode: GradientRendererMode = GradientRendererMode.AUTO,
    val respectSystemReducedMotion: Boolean = true,
)

@Serializable
enum class GradientRendererMode {
    @SerialName("auto") AUTO,
    @SerialName("agsl") AGSL,
    @SerialName("kotlin") KOTLIN,
}

@Serializable
enum class TextColorMode {
    @SerialName("theme") THEME,
    @SerialName("auto_clear") AUTO_CLEAR,
    @SerialName("app_background") APP_BACKGROUND,
    @SerialName("system_wallpaper") SYSTEM_WALLPAPER,
}

@Serializable
enum class AppearanceColorStyle {
    @SerialName("tonal_spot") TONAL_SPOT,
    @SerialName("neutral") NEUTRAL,
    @SerialName("vibrant") VIBRANT,
    @SerialName("expressive") EXPRESSIVE,
    @SerialName("monochrome") MONOCHROME,
}

@Serializable
enum class BackgroundSurfaceStyle {
    @SerialName("opaque") OPAQUE,
    @SerialName("translucent") TRANSLUCENT,
    @SerialName("frosted") FROSTED,
    @SerialName("liquid_glass") LIQUID_GLASS,
    @SerialName("acrylic") ACRYLIC,
    @SerialName("mica") MICA;

    val opaqueMaterial: Boolean get() = this == OPAQUE
    val usesBlur: Boolean get() = this == FROSTED || this == LIQUID_GLASS || this == ACRYLIC
}

@Serializable
enum class ChatBubbleStyle {
    @SerialName("frosted") FROSTED,
    @SerialName("outlined") OUTLINED,
    @SerialName("liquid_glass") LIQUID_GLASS,
}

@Serializable
enum class RichContentStyle {
    @SerialName("translucent") TRANSLUCENT,
    @SerialName("outlined") OUTLINED,
    @SerialName("liquid_glass") LIQUID_GLASS,
}

internal fun finiteAppearanceValue(value: Float, min: Float, max: Float, fallback: Float): Float =
    if (value.isFinite()) value.coerceIn(min, max) else fallback

fun AdvancedAppearanceSetting.normalizedChatTextLineHeightRatio(): Float =
    finiteAppearanceValue(chatTextLineHeightRatio, MIN_CHAT_TEXT_LINE_HEIGHT_RATIO, MAX_CHAT_TEXT_LINE_HEIGHT_RATIO, 1.5f)

fun AdvancedAppearanceSetting.normalizedChatParagraphSpacingRatio(): Float =
    finiteAppearanceValue(chatParagraphSpacingRatio, MIN_CHAT_PARAGRAPH_SPACING_RATIO, MAX_CHAT_PARAGRAPH_SPACING_RATIO, .65f)

internal fun AdvancedAppearanceSetting.normalized(): AdvancedAppearanceSetting {
    val safe = copy(
        micaTintStrength = finiteAppearanceValue(micaTintStrength, 0f, 1f, .45f),
        liquidGlass = liquidGlass.copy(custom = liquidGlass.custom.normalized()),
        globalBackground = globalBackground?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2048 },
        globalBackgroundOpacity = finiteAppearanceValue(globalBackgroundOpacity, 0f, 1f, 1f),
        globalBackgroundBlurRadius = finiteAppearanceValue(globalBackgroundBlurRadius, 0f, MAX_GLOBAL_BACKGROUND_BLUR_RADIUS, 20f),
        pageSurfaceBlurRadius = finiteAppearanceValue(pageSurfaceBlurRadius, 0f, MAX_GLOBAL_BACKGROUND_BLUR_RADIUS, 20f),
        pageLiquidGlassBlurRadius = finiteAppearanceValue(pageLiquidGlassBlurRadius, 0f, MAX_LIQUID_GLASS_BLUR_RADIUS, 0f),
        pageSurfaceOpacity = finiteAppearanceValue(pageSurfaceOpacity, 0f, 1f, .68f),
        overlaySurfaceOpacity = finiteAppearanceValue(overlaySurfaceOpacity, 0f, 1f, .82f),
        overlaySurfaceBlurRadius = finiteAppearanceValue(overlaySurfaceBlurRadius, 0f, 40f, 20f),
        overlayLiquidGlassBlurRadius = finiteAppearanceValue(overlayLiquidGlassBlurRadius, 0f, MAX_LIQUID_GLASS_BLUR_RADIUS, 0f),
        navigationGlassOpacity = finiteAppearanceValue(navigationGlassOpacity, 0f, 1f, .72f),
        navigationGlassBlurRadius = finiteAppearanceValue(navigationGlassBlurRadius, 0f, MAX_NAVIGATION_GLASS_BLUR_RADIUS, 24f),
        navigationLiquidGlassBlurRadius = finiteAppearanceValue(navigationLiquidGlassBlurRadius, 0f, MAX_LIQUID_GLASS_BLUR_RADIUS, 0f),
        chatDockGlassOpacity = finiteAppearanceValue(chatDockGlassOpacity, 0f, 1f, .58f),
        chatDockGlassBlurRadius = finiteAppearanceValue(chatDockGlassBlurRadius, 0f, MAX_LIQUID_GLASS_BLUR_RADIUS, 0f),
        chatBubbleSurfaceOpacity = chatBubbleSurfaceOpacity?.takeIf { it.isFinite() }?.coerceIn(0f, 1f),
        chatBubbleBlurRadius = chatBubbleBlurRadius?.takeIf { it.isFinite() }?.coerceIn(0f, 40f),
        chatTextLineHeightRatio = normalizedChatTextLineHeightRatio(),
        chatParagraphSpacingRatio = normalizedChatParagraphSpacingRatio(),
        richContentSurfaceOpacity = finiteAppearanceValue(richContentSurfaceOpacity, 0f, 1f, .62f),
        richContentSurfaceBlurRadius = richContentSurfaceBlurRadius?.takeIf { it.isFinite() }?.coerceIn(0f, 40f),
        chatSuggestionSurfaceOpacity = finiteAppearanceValue(chatSuggestionSurfaceOpacity, .2f, 1f, .62f),
        chatSuggestionBorderOpacity = finiteAppearanceValue(chatSuggestionBorderOpacity, 0f, 1f, .72f),
        chatSuggestionMaxHeight = finiteAppearanceValue(
            chatSuggestionMaxHeight, MIN_CHAT_SUGGESTION_HEIGHT, MAX_CHAT_SUGGESTION_HEIGHT, 128f,
        ),
        inspirationAppearance = inspirationAppearance.normalized(),
        colorContrast = finiteAppearanceValue(colorContrast, -1f, 1f, 0f),
    )
    return if (safe == this) this else safe
}

internal fun DisplaySetting.normalizedAppearanceInputs(): DisplaySetting {
    val safe = copy(
        topBarBlurRadius = finiteAppearanceValue(topBarBlurRadius, 0f, 40f, 20f),
        topBarSurfaceOpacity = finiteAppearanceValue(topBarSurfaceOpacity, 0f, 1f, .65f),
        inputBlurRadius = finiteAppearanceValue(inputBlurRadius, 0f, 40f, 12f),
        inputSurfaceOpacity = finiteAppearanceValue(inputSurfaceOpacity, 0f, 1f, .55f),
    )
    return if (safe == this) this else safe
}

fun Settings.isGlobalBackgroundActive(): Boolean = advancedAppearanceSetting.run {
    enableAdvancedAppearance && enableGlobalBackground && !globalBackground.isNullOrBlank()
}

internal fun Settings.isGlobalBackgroundVisible(): Boolean =
    isGlobalBackgroundActive() && advancedAppearanceSetting.globalBackgroundOpacity > 0f

internal fun Settings.isPageBackgroundActive(): Boolean =
    isGlobalBackgroundVisible() && !advancedAppearanceSetting.pageSurfaceStyle.opaqueMaterial

fun Settings.isGlobalBackgroundAppliedToChat(): Boolean =
    isGlobalBackgroundVisible() && advancedAppearanceSetting.applyGlobalBackgroundToChat

fun Settings.isFullscreenPreviewBackgroundActive(): Boolean =
    isPageBackgroundActive() && advancedAppearanceSetting.applyGlobalBackgroundToFullscreenPreview

fun Settings.configuredAssistantBackgroundCount(): Int =
    assistants.count { !it.background.isNullOrBlank() || it.useGradientBackground }

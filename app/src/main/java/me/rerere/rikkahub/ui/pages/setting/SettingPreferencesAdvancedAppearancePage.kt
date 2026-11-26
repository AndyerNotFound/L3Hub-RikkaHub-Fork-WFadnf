package me.rerere.rikkahub.ui.pages.setting

import android.app.WallpaperManager
import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.datastore.AppearanceColorStyle
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.data.datastore.ChatBubbleStyle
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleTarget
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.GradientRendererMode
import me.rerere.rikkahub.data.datastore.MAX_CHAT_PARAGRAPH_SPACING_RATIO
import me.rerere.rikkahub.data.datastore.MAX_CHAT_SUGGESTION_HEIGHT
import me.rerere.rikkahub.data.datastore.MAX_CHAT_TEXT_LINE_HEIGHT_RATIO
import me.rerere.rikkahub.data.datastore.MAX_GLOBAL_BACKGROUND_BLUR_RADIUS
import me.rerere.rikkahub.data.datastore.MAX_LIQUID_GLASS_BLUR_RADIUS
import me.rerere.rikkahub.data.datastore.MAX_NAVIGATION_GLASS_BLUR_RADIUS
import me.rerere.rikkahub.data.datastore.MIN_CHAT_PARAGRAPH_SPACING_RATIO
import me.rerere.rikkahub.data.datastore.MIN_CHAT_SUGGESTION_HEIGHT
import me.rerere.rikkahub.data.datastore.MIN_CHAT_TEXT_LINE_HEIGHT_RATIO
import me.rerere.rikkahub.data.datastore.RichContentStyle
import me.rerere.rikkahub.data.datastore.TextColorMode
import me.rerere.rikkahub.data.datastore.applyDisplaySettingEdit
import me.rerere.rikkahub.data.datastore.configuredAssistantBackgroundCount
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.LiquidGlassPreset
import me.rerere.rikkahub.data.model.LiquidGlassStyle
import me.rerere.rikkahub.data.model.LiquidGlassRenderer
import me.rerere.rikkahub.data.model.MAX_LIQUID_GLASS_REFRACTION
import me.rerere.rikkahub.data.model.edit
import me.rerere.rikkahub.data.model.parameters
import me.rerere.rikkahub.data.model.usesMaterial3Style
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AppSlider
import me.rerere.rikkahub.ui.components.message.messageBubbleMaterial
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.LocalTopBarHazeState
import me.rerere.rikkahub.ui.components.ui.animatedFormItem
import me.rerere.rikkahub.ui.components.ui.animatedItem
import me.rerere.rikkahub.ui.components.ui.animatedSwitchItem
import me.rerere.rikkahub.ui.components.ui.resolvedLiveAppearanceRadius
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.components.ui.topBarMaterial
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.navigation.appearancePageContainerColor
import me.rerere.rikkahub.ui.style.DropdownMenu
import me.rerere.rikkahub.ui.style.LargeFlexibleTopAppBar
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.style.TopAppBar
import me.rerere.rikkahub.ui.theme.AppearanceBackdrop
import me.rerere.rikkahub.ui.theme.LocalAppearanceImage
import me.rerere.rikkahub.ui.theme.MAX_APPEARANCE_IMAGE_BYTES
import me.rerere.rikkahub.ui.theme.isAppearanceImage
import me.rerere.rikkahub.ui.theme.sampleAppearanceImageBytes
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.SeparateWindowContent
import me.rerere.ui.toast.ToastType
import me.rerere.rikkahub.ui.pages.setting.MessageBubbleStyleSettingsSection
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

@Composable
fun SettingPreferencesAdvancedAppearancePage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val appearance = settings.advancedAppearanceSetting
    val display = settings.displaySetting
    val context = LocalContext.current
    val files: FilesManager = koinInject()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var importing by remember { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(false) }
    val imageState = LocalAppearanceImage.current
    val image = imageState?.image
    val missingImage = imageState?.resolved == true && image == null
    val pictureControls = appearance.enableGlobalBackground && image != null
    LaunchedEffect(missingImage, appearance.enableGlobalBackground, appearance.enableAdvancedAppearance) {
        if (missingImage || !appearance.enableGlobalBackground || !appearance.enableAdvancedAppearance) preview = false
    }
    val accent = image?.colors?.accent
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val canRefract = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val glassLive = appearance.liquidGlass.renderer != LiquidGlassRenderer.STANDARD
    val pageStyle = appearance.pageSurfaceStyle
    val topBarHazeState = LocalTopBarHazeState.current
    val imageRequest = remember { PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) }

    fun update(change: AdvancedAppearanceSetting.() -> AdvancedAppearanceSetting) = vm.updateSettings { latest ->
        latest.copy(advancedAppearanceSetting = latest.advancedAppearanceSetting.change())
    }
    fun updateDisplay(change: DisplaySetting.() -> DisplaySetting) = vm.updateSettings { latest ->
        latest.applyDisplaySettingEdit(change)
    }
    fun importImage(uri: Uri?, wallpaper: Boolean) {
        if (uri == null || importing) return
        importing = true
        scope.launch {
            try {
                if (wallpaper) {
                    val seed = withContext(Dispatchers.IO) {
                        val imageData = readAppearanceImage(context.contentResolver, uri)
                        val sample = sampleAppearanceImageBytes(imageData.bytes)
                        requireNotNull(sample?.accent ?: sample?.average)
                    }
                    val wallpaperId = runCatching {
                        context.getSystemService(WallpaperManager::class.java)?.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
                    }.getOrNull()?.takeIf { it > 0 }
                    update {
                        copy(wallpaperTextSeedArgb = seed.toLong() and 0xffffffffL,
                            wallpaperTextSeedWallpaperId = wallpaperId)
                    }
                } else {
                    val picture = withContext(Dispatchers.IO) { readAppearanceImage(context.contentResolver, uri) }
                    val suffix = MimeTypeMap.getSingleton().getExtensionFromMimeType(picture.mimeType)
                    val name = if (suffix == null) "background" else "background.$suffix"
                    val stored = files.saveManagedFromBytes(FileFolders.UPLOAD, picture.bytes, name, picture.mimeType)
                    update { copy(globalBackground = stored.relativePath, enableGlobalBackground = true) }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                toaster.show(context.getString(R.string.aa_import_failed), type = ToastType.Error)
            } finally {
                importing = false
            }
        }
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { importImage(it, false) }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { importImage(it, true) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = appearancePageContainerColor(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.aa_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().then(
                topBarHazeState?.let { Modifier.hazeSource(it, zIndex = 1f) } ?: Modifier
            ),
            contentPadding = padding + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("enabled") {
                CardGroup {
                    toggle(R.string.aa_enabled, appearance.enableAdvancedAppearance) { update { copy(enableAdvancedAppearance = it) } }
                }
            }
            item("messageStyle") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(R.string.aa_message_appearance),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                    MessageBubbleStyleSettingsSection(
                        displaySetting = display,
                        advancedAppearance = appearance,
                        onDisplaySettingChange = { change ->
                            vm.updateSettings { latest -> latest.applyDisplaySettingEdit(change) }
                        },
                    )
                }
            }
            if (!appearance.enableAdvancedAppearance) return@LazyColumn

            item("background") {
                CardGroup(title = { Text(stringResource(R.string.aa_background)) }) {
                    toggle(R.string.aa_background, appearance.enableGlobalBackground) { update { copy(enableGlobalBackground = it) } }
                    val pickLabel = when {
                        importing -> R.string.aa_importing
                        image == null -> R.string.aa_choose_image
                        else -> R.string.aa_replace_image
                    }
                    animatedItem(visible = { appearance.enableGlobalBackground }, onClick = if (importing) null else {
                        { backgroundPicker.launch(imageRequest) }
                    }) { Text(stringResource(pickLabel)) }
                    animatedItem(visible = { missingImage }) {
                        Text(stringResource(R.string.aa_background_missing))
                    }
                    animatedFormItem(visible = { pictureControls }) {
                        if (image != null) {
                            AppearanceBackdrop(image, appearance, Modifier.fillMaxWidth().height(100.dp).clip(MaterialTheme.shapes.medium))
                        }
                    }
                    animatedItem(visible = { pictureControls }, onClick = { preview = true }) {
                        Text(stringResource(R.string.aa_background_preview))
                    }
                    animatedItem(visible = { appearance.globalBackground != null && !importing }, onClick = {
                        preview = false
                        update { copy(globalBackground = null) }
                    }) {
                        Text(stringResource(R.string.aa_remove_image))
                    }
                    slider(R.string.aa_image_opacity, appearance.globalBackgroundOpacity, 0f..1f, visible = pictureControls) {
                        update { copy(globalBackgroundOpacity = it) }
                    }
                    slider(R.string.aa_background_blur, appearance.globalBackgroundBlurRadius, blurRange(MAX_GLOBAL_BACKGROUND_BLUR_RADIUS),
                        R.string.aa_dp, visible = pictureControls, enabled = canBlur) { update { copy(globalBackgroundBlurRadius = it) } }
                    toggle(R.string.aa_background_chat, appearance.applyGlobalBackgroundToChat, visible = pictureControls,
                        detail = {
                            if (appearance.globalBackgroundOpacity == 0f) {
                                Text(stringResource(R.string.aa_background_chat_hidden))
                            } else {
                                Text(stringResource(R.string.aa_assistant_background_count, settings.configuredAssistantBackgroundCount()))
                            }
                        }) {
                        update { copy(applyGlobalBackgroundToChat = it) }
                    }
                    toggle(
                        R.string.aa_background_preview_override,
                        appearance.applyGlobalBackgroundToFullscreenPreview,
                        visible = pictureControls,
                        enabled = !pageStyle.opaqueMaterial,
                        detail = if (pageStyle.opaqueMaterial) {
                            { Text(stringResource(R.string.aa_preview_requires_surface)) }
                        } else null,
                    ) {
                        update { copy(applyGlobalBackgroundToFullscreenPreview = it) }
                    }
                }
            }
            item("composer") {
                val inputStyle = appearance.composerSurfaceStyle ?: BackgroundSurfaceStyle.LIQUID_GLASS.takeIf { appearance.liquidGlass.applyToComposer }
                CardGroup(title = { Text(stringResource(R.string.aa_input)) }) {
                    choice(R.string.aa_input_surface, appearance.composerSurfaceStyle?.label() ?: R.string.aa_inherit,
                        listOf(null to R.string.aa_inherit) + surfaceOptions()) { update { copy(composerSurfaceStyle = it) } }
                    slider(R.string.aa_input_opacity, display.inputSurfaceOpacity, 0f..1f, visible = inputStyle != null && !inputStyle.opaqueMaterial,
                        supportingContent = hint(R.string.aa_opacity_floor_hint)) {
                        updateDisplay { copy(inputSurfaceOpacity = it) }
                    }
                    slider(R.string.aa_input_radius, display.inputBlurRadius, blurRange(40f), R.string.aa_dp,
                        visible = inputStyle?.usesBlur == true &&
                            (inputStyle != BackgroundSurfaceStyle.LIQUID_GLASS || !appearance.liquidGlass.usesMaterial3Style()),
                        enabled = canBlur && appearance.enableInputPerformanceEffects &&
                            (inputStyle != BackgroundSurfaceStyle.LIQUID_GLASS || (glassLive && appearance.liquidGlass.renderer != LiquidGlassRenderer.STANDARD)),
                        supportingContent = hint(R.string.aa_blur_radius_hint)) { updateDisplay { copy(inputBlurRadius = it) } }
                    toggle(
                        R.string.aa_dock,
                        appearance.enableChatDockGlass,
                        detail = { Text(stringResource(R.string.aa_dock_requires_toolbar)) },
                    ) { update { copy(enableChatDockGlass = it) } }
                    choice(R.string.aa_dock_surface, appearance.chatDockSurfaceStyle.label(), surfaceOptions(), visible = appearance.enableChatDockGlass) {
                        update { copy(chatDockSurfaceStyle = it) }
                    }
                    slider(R.string.aa_dock_opacity, appearance.chatDockGlassOpacity, 0f..1f, visible = appearance.enableChatDockGlass && !appearance.chatDockSurfaceStyle.opaqueMaterial,
                        supportingContent = hint(R.string.aa_opacity_floor_hint)) {
                        update { copy(chatDockGlassOpacity = it) }
                    }
                    animatedItem(visible = { appearance.enableChatDockGlass && appearance.chatDockSurfaceStyle.opaqueMaterial }) {
                        Text(stringResource(R.string.aa_solid_hint))
                    }
                    slider(
                        R.string.aa_dock_blur,
                        appearance.chatDockGlassBlurRadius,
                        blurRange(MAX_LIQUID_GLASS_BLUR_RADIUS),
                        R.string.aa_dp,
                        visible = appearance.enableChatDockGlass && appearance.chatDockSurfaceStyle.usesBlur &&
                            (appearance.chatDockSurfaceStyle != BackgroundSurfaceStyle.LIQUID_GLASS || !appearance.liquidGlass.usesMaterial3Style()),
                        enabled = canBlur && appearance.enableChatDockPerformanceEffects &&
                            (appearance.chatDockSurfaceStyle != BackgroundSurfaceStyle.LIQUID_GLASS ||
                                (glassLive && appearance.liquidGlass.renderer != LiquidGlassRenderer.STANDARD)),
                        supportingContent = hint(R.string.aa_blur_radius_hint),
                    ) {
                        update { copy(chatDockGlassBlurRadius = it) }
                    }
                }
            }
            item("topBar") {
                val topBarStyle = appearance.topBarMaterial(display)?.style ?: pageStyle
                val canBlurTopBar = canBlur && appearance.enableTopBarPerformanceEffects &&
                    (topBarStyle != BackgroundSurfaceStyle.LIQUID_GLASS || glassLive)
                CardGroup(title = { Text(stringResource(R.string.aa_topbar)) }) {
                    toggle(R.string.aa_topbar_background, display.showTopBarBackground) { updateDisplay { copy(showTopBarBackground = it) } }
                    // 整条底关闭时，聊天顶栏的按钮和胶囊仍使用这里的模糊设置。
                    toggle(R.string.aa_topbar_blur, display.enableTopBarBlur,
                        visible = topBarStyle.usesBlur,
                        enabled = canBlurTopBar,
                        detail = if (display.showTopBarBackground) null else hint(R.string.aa_topbar_capsule_hint)) {
                        updateDisplay { copy(enableTopBarBlur = it) }
                    }
                    slider(R.string.aa_surface_opacity, display.topBarSurfaceOpacity, 0f..1f, visible = !topBarStyle.opaqueMaterial) {
                        updateDisplay { copy(topBarSurfaceOpacity = it) }
                    }
                    slider(R.string.aa_surface_blur, display.topBarBlurRadius, blurRange(40f), R.string.aa_dp,
                        visible = topBarStyle.usesBlur && display.enableTopBarBlur &&
                            (topBarStyle != BackgroundSurfaceStyle.LIQUID_GLASS || !appearance.liquidGlass.usesMaterial3Style()),
                        enabled = canBlurTopBar,
                        supportingContent = hint(R.string.aa_blur_radius_hint)) {
                        updateDisplay { copy(topBarBlurRadius = it) }
                    }
                }
            }
            item("cards") {
                CardGroup(title = { Text(stringResource(R.string.aa_cards)) }) {
                    choice(R.string.aa_surface, appearance.pageSurfaceStyle.label(), surfaceOptions()) { update { copy(pageSurfaceStyle = it) } }
                    animatedItem(visible = { appearance.pageSurfaceStyle.opaqueMaterial }) {
                        Text(stringResource(R.string.aa_solid_hint))
                    }
                    slider(R.string.aa_surface_opacity, appearance.pageSurfaceOpacity, 0f..1f, visible = !appearance.pageSurfaceStyle.opaqueMaterial,
                        supportingContent = hint(R.string.aa_opacity_floor_hint)) {
                        update { copy(pageSurfaceOpacity = it) }
                    }
                    slider(R.string.aa_surface_blur, appearance.pageSurfaceBlurRadius, blurRange(MAX_GLOBAL_BACKGROUND_BLUR_RADIUS), R.string.aa_dp,
                        visible = pageStyle == BackgroundSurfaceStyle.FROSTED || pageStyle == BackgroundSurfaceStyle.ACRYLIC,
                        enabled = canBlur,
                        supportingContent = hint(R.string.aa_blur_radius_hint)) {
                        update { copy(pageSurfaceBlurRadius = it) }
                    }
                    slider(R.string.aa_refraction_blur, appearance.pageLiquidGlassBlurRadius, blurRange(MAX_LIQUID_GLASS_BLUR_RADIUS), R.string.aa_dp,
                        visible = pageStyle == BackgroundSurfaceStyle.LIQUID_GLASS && !appearance.liquidGlass.usesMaterial3Style(),
                        enabled = canBlur && glassLive,
                        supportingContent = hint(R.string.aa_blur_radius_hint)) {
                        update { copy(pageLiquidGlassBlurRadius = it) }
                    }
                    val usesMica = pageStyle == BackgroundSurfaceStyle.MICA ||
                        appearance.composerSurfaceStyle == BackgroundSurfaceStyle.MICA ||
                        appearance.overlaySurfaceStyle == BackgroundSurfaceStyle.MICA ||
                        (appearance.enableNavigationGlass && appearance.navigationSurfaceStyle == BackgroundSurfaceStyle.MICA)
                    slider(R.string.aa_mica_tint, appearance.micaTintStrength, 0f..1f, visible = usesMica) {
                        update { copy(micaTintStrength = it) }
                    }
                }
            }
            item("overlays") {
                CardGroup(title = { Text(stringResource(R.string.aa_overlays)) }) {
                    val style = appearance.overlaySurfaceStyle
                    choice(R.string.aa_surface, style.label(), surfaceOptions()) { update { copy(overlaySurfaceStyle = it) } }
                    slider(R.string.aa_surface_opacity, appearance.overlaySurfaceOpacity, 0f..1f, visible = !style.opaqueMaterial,
                        supportingContent = hint(R.string.aa_opacity_floor_hint)) {
                        update { copy(overlaySurfaceOpacity = it) }
                    }
                    animatedItem(visible = { !style.opaqueMaterial }) {
                        Text(stringResource(R.string.aa_overlay_fallback))
                    }
                    animatedItem(visible = { style.opaqueMaterial }) {
                        Text(stringResource(R.string.aa_solid_hint))
                    }
                    slider(R.string.aa_surface_blur, appearance.overlaySurfaceBlurRadius, blurRange(40f), R.string.aa_dp,
                        visible = style.usesBlur && style != BackgroundSurfaceStyle.LIQUID_GLASS,
                        enabled = canBlur && appearance.enableOverlayPerformanceEffects,
                        supportingContent = hint(R.string.aa_blur_radius_hint)) {
                        update { copy(overlaySurfaceBlurRadius = it) }
                    }
                    slider(R.string.aa_refraction_blur, appearance.overlayLiquidGlassBlurRadius, blurRange(MAX_LIQUID_GLASS_BLUR_RADIUS), R.string.aa_dp,
                        visible = style == BackgroundSurfaceStyle.LIQUID_GLASS && !appearance.liquidGlass.usesMaterial3Style(),
                        enabled = canBlur && glassLive && appearance.liquidGlass.renderer != LiquidGlassRenderer.STANDARD && appearance.enableOverlayPerformanceEffects,
                        supportingContent = hint(R.string.aa_blur_radius_hint)) {
                        update { copy(overlayLiquidGlassBlurRadius = it) }
                    }
                }
            }
            item("navigation") {
                CardGroup(title = { Text(stringResource(R.string.aa_navigation)) }) {
                    val style = appearance.navigationSurfaceStyle
                    val enabled = appearance.enableNavigationGlass
                    toggle(R.string.aa_navigation_enabled, enabled) { update { copy(enableNavigationGlass = it) } }
                    choice(R.string.aa_surface, style.label(), surfaceOptions(), visible = enabled) { update { copy(navigationSurfaceStyle = it) } }
                    slider(R.string.aa_surface_opacity, appearance.navigationGlassOpacity, 0f..1f, visible = enabled && !style.opaqueMaterial,
                        supportingContent = hint(R.string.aa_opacity_floor_hint)) {
                        update { copy(navigationGlassOpacity = it) }
                    }
                    animatedItem(visible = { enabled && style.opaqueMaterial }) {
                        Text(stringResource(R.string.aa_solid_hint))
                    }
                    slider(R.string.aa_surface_blur, appearance.navigationGlassBlurRadius, blurRange(MAX_NAVIGATION_GLASS_BLUR_RADIUS), R.string.aa_dp,
                        visible = enabled && style.usesBlur && style != BackgroundSurfaceStyle.LIQUID_GLASS,
                        enabled = canBlur && appearance.enableNavigationPerformanceEffects,
                        supportingContent = hint(R.string.aa_blur_radius_hint)) {
                        update { copy(navigationGlassBlurRadius = it) }
                    }
                    slider(R.string.aa_refraction_blur, appearance.navigationLiquidGlassBlurRadius, blurRange(MAX_LIQUID_GLASS_BLUR_RADIUS), R.string.aa_dp,
                        visible = enabled && style == BackgroundSurfaceStyle.LIQUID_GLASS && !appearance.liquidGlass.usesMaterial3Style(),
                        enabled = canBlur && glassLive && appearance.liquidGlass.renderer != LiquidGlassRenderer.STANDARD && appearance.enableNavigationPerformanceEffects,
                        supportingContent = hint(R.string.aa_blur_radius_hint)) {
                        update { copy(navigationLiquidGlassBlurRadius = it) }
                    }
                }
            }
            item("liquidGlass") {
                val glass = appearance.liquidGlass
                val params = glass.parameters()
                val usesSaltStyle = glass.style == LiquidGlassStyle.SALT
                val presets = LiquidGlassPreset.entries.zip(listOf(R.string.aa_soft, R.string.aa_standard, R.string.aa_clear, R.string.aa_custom))
                val renderers = LiquidGlassRenderer.entries.zip(listOf(R.string.aa_auto, R.string.aa_static, R.string.aa_agsl))
                val glassStyles = LiquidGlassStyle.entries.zip(listOf(R.string.aa_glass_style_salt, R.string.aa_glass_style_original))
                CardGroup(title = { Text(stringResource(R.string.aa_liquid_glass)) }) {
                    choice(R.string.aa_glass_style, glassStyles.toMap().getValue(glass.style), glassStyles,
                        supportingContent = hint(R.string.aa_glass_style_hint)) {
                        update { copy(liquidGlass = liquidGlass.copy(style = it)) }
                    }
                    choice(R.string.aa_glass_preset, presets.toMap().getValue(glass.preset), presets, visible = usesSaltStyle) { update { copy(liquidGlass = liquidGlass.copy(preset = it)) } }
                    choice(R.string.aa_renderer, renderers.toMap().getValue(glass.renderer), renderers, enabled = canRefract) {
                        update { copy(liquidGlass = liquidGlass.copy(renderer = it)) }
                    }
                    toggle(R.string.aa_glass_input, glass.applyToComposer, visible = appearance.composerSurfaceStyle == null) {
                        update { copy(liquidGlass = liquidGlass.copy(applyToComposer = it)) }
                    }
                    slider(R.string.aa_glass_highlight, params.highlight, 0f.. .8f, visible = usesSaltStyle) { update { copy(liquidGlass = liquidGlass.edit { copy(highlight = it) }) } }
                    slider(R.string.aa_glass_tint, params.tint, 0f.. .2f, visible = usesSaltStyle) { update { copy(liquidGlass = liquidGlass.edit { copy(tint = it) }) } }
                    slider(R.string.aa_glass_edge, params.edgeWidth, .5f..4f, R.string.aa_dp, visible = usesSaltStyle) { update { copy(liquidGlass = liquidGlass.edit { copy(edgeWidth = it) }) } }
                    slider(R.string.aa_glass_refraction, params.refraction, 0f..MAX_LIQUID_GLASS_REFRACTION, R.string.aa_ratio,
                        visible = usesSaltStyle, enabled = canRefract && glass.renderer != LiquidGlassRenderer.STANDARD,
                        supportingContent = hint(R.string.aa_refraction_hint)) {
                        update { copy(liquidGlass = liquidGlass.edit { copy(refraction = it) }) }
                    }
                    slider(R.string.aa_glass_shadow, params.innerShadow, 0f.. .25f, visible = usesSaltStyle) { update { copy(liquidGlass = liquidGlass.edit { copy(innerShadow = it) }) } }
                }
            }
            item("chat") {
                val bubbles = ChatBubbleStyle.entries.zip(listOf(R.string.aa_frosted, R.string.aa_outlined, R.string.aa_liquid_glass))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CardGroup(title = { Text(stringResource(R.string.aa_chat_text)) }) {
                        choice(R.string.aa_bubbles, bubbles.toMap().getValue(appearance.chatBubbleStyle), bubbles) { update { copy(chatBubbleStyle = it) } }
                        val bubbleOpacityDefault = when (appearance.chatBubbleStyle) {
                            ChatBubbleStyle.FROSTED -> .46f + display.bubbleOpacity * .36f
                            ChatBubbleStyle.OUTLINED -> .16f + display.bubbleOpacity * .12f
                            ChatBubbleStyle.LIQUID_GLASS -> .62f + display.bubbleOpacity * .24f
                        }
                        val bubbleRoles = listOf(
                            ChatBubbleStyleTarget.USER to display.userChatBubbleStyleOverrides,
                            ChatBubbleStyleTarget.ASSISTANT to display.assistantChatBubbleStyleOverrides,
                        )
                        val inheritedOpacityRole = when (display.chatMessageBackgroundStyle) {
                            me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle.DEFAULT -> ChatBubbleStyleTarget.USER
                            me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle.FROSTED ->
                                bubbleRoles.firstOrNull { it.second.frostedOpacity == null }?.first
                            me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle.SOLID -> null
                        }
                        val inheritedRadiusRole = when (display.chatMessageBackgroundStyle) {
                            me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle.DEFAULT -> ChatBubbleStyleTarget.USER
                            me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle.FROSTED ->
                                bubbleRoles.firstOrNull { it.second.blurSigma == null }?.first
                            me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle.SOLID -> null
                        }
                        val inheritedOpacityMaterial = inheritedOpacityRole?.let { role ->
                            appearance.messageBubbleMaterial(
                                display, role, resolved = null, streaming = false,
                            )
                        }
                        val inheritedRadiusMaterial = inheritedRadiusRole?.let { role ->
                            appearance.messageBubbleMaterial(
                                display, role, resolved = null, streaming = false,
                            )
                        }
                        slider(R.string.aa_surface_opacity, inheritedOpacityMaterial?.opacity ?: bubbleOpacityDefault, 0f..1f,
                            visible = inheritedOpacityRole != null,
                            supportingContent = hint(R.string.aa_bubble_inherit_hint)) {
                            update { copy(chatBubbleSurfaceOpacity = it) }
                        }
                        slider(
                            R.string.aa_surface_blur,
                            inheritedRadiusMaterial?.radius ?: if (appearance.chatBubbleStyle == ChatBubbleStyle.LIQUID_GLASS) 24f else 18f,
                            blurRange(40f), R.string.aa_dp,
                            visible = inheritedRadiusRole != null && appearance.chatBubbleStyle != ChatBubbleStyle.OUTLINED &&
                                !(appearance.chatBubbleStyle == ChatBubbleStyle.LIQUID_GLASS && appearance.liquidGlass.usesMaterial3Style()),
                            enabled = canBlur && appearance.enableBubblePerformanceEffects &&
                                (appearance.chatBubbleStyle != ChatBubbleStyle.LIQUID_GLASS ||
                                    (glassLive && appearance.liquidGlass.renderer != LiquidGlassRenderer.STANDARD)),
                            supportingContent = hint(R.string.aa_blur_radius_hint),
                        ) { update { copy(chatBubbleBlurRadius = it) } }
                        toggle(R.string.aa_readability, appearance.enableChatTextReadability) { update { copy(enableChatTextReadability = it) } }
                        slider(R.string.aa_line_height, appearance.chatTextLineHeightRatio, MIN_CHAT_TEXT_LINE_HEIGHT_RATIO..MAX_CHAT_TEXT_LINE_HEIGHT_RATIO, R.string.aa_ratio) {
                            update { copy(chatTextLineHeightRatio = it) }
                        }
                        slider(R.string.aa_paragraph_spacing, appearance.chatParagraphSpacingRatio, MIN_CHAT_PARAGRAPH_SPACING_RATIO..MAX_CHAT_PARAGRAPH_SPACING_RATIO, R.string.aa_ratio) {
                            update { copy(chatParagraphSpacingRatio = it) }
                        }
                    }
                }
            }
            item("richContent") {
                val rich = RichContentStyle.entries.zip(listOf(R.string.aa_translucent, R.string.aa_outlined, R.string.aa_liquid_glass))
                CardGroup(title = { Text(stringResource(R.string.aa_rich_content)) }) {
                    choice(R.string.aa_surface, rich.toMap().getValue(appearance.richContentStyle), rich) { update { copy(richContentStyle = it) } }
                    slider(R.string.aa_surface_opacity, appearance.richContentSurfaceOpacity, 0f..1f) { update { copy(richContentSurfaceOpacity = it) } }
                    slider(
                        R.string.aa_surface_blur,
                        appearance.richContentSurfaceBlurRadius ?: if (appearance.richContentStyle == RichContentStyle.LIQUID_GLASS) 16f else 8f,
                        blurRange(40f), R.string.aa_dp,
                        visible = appearance.richContentStyle != RichContentStyle.OUTLINED &&
                            !(appearance.richContentStyle == RichContentStyle.LIQUID_GLASS && appearance.liquidGlass.usesMaterial3Style()),
                        enabled = canBlur && appearance.enableRichContentPerformanceEffects &&
                            (appearance.richContentStyle != RichContentStyle.LIQUID_GLASS || glassLive),
                    ) { update { copy(richContentSurfaceBlurRadius = it) } }
                }
            }
            item("suggestions") {
                val enabled = appearance.enableChatSuggestionSurface
                CardGroup(title = { Text(stringResource(R.string.aa_suggestions)) }) {
                    toggle(R.string.aa_suggestion_surface, enabled) { update { copy(enableChatSuggestionSurface = it) } }
                    slider(R.string.aa_surface_opacity, appearance.chatSuggestionSurfaceOpacity, .2f..1f, visible = enabled,
                        supportingContent = hint(R.string.aa_suggestion_opacity_hint)) {
                        update { copy(chatSuggestionSurfaceOpacity = it) }
                    }
                    slider(R.string.aa_border_opacity, appearance.chatSuggestionBorderOpacity, 0f..1f, visible = enabled) { update { copy(chatSuggestionBorderOpacity = it) } }
                    slider(
                        R.string.aa_suggestion_height,
                        appearance.chatSuggestionMaxHeight,
                        MIN_CHAT_SUGGESTION_HEIGHT..MAX_CHAT_SUGGESTION_HEIGHT,
                        R.string.aa_dp,
                        visible = enabled,
                    ) {
                        update { copy(chatSuggestionMaxHeight = it) }
                    }
                }
            }
            item("inspiration") {
                val card = appearance.inspirationAppearance
                CardGroup(title = { Text(stringResource(R.string.aa_inspiration)) }) {
                    toggle(R.string.aa_follow_rich, card.followRichContent) { update { copy(inspirationAppearance = inspirationAppearance.copy(followRichContent = it)) } }
                    slider(R.string.aa_surface_opacity, card.surfaceOpacity, .2f..1f, visible = !card.followRichContent,
                        supportingContent = hint(R.string.aa_suggestion_opacity_hint)) {
                        update { copy(inspirationAppearance = inspirationAppearance.copy(surfaceOpacity = it)) }
                    }
                    slider(R.string.aa_border_opacity, card.borderOpacity, 0f..1f, visible = !card.followRichContent) {
                        update { copy(inspirationAppearance = inspirationAppearance.copy(borderOpacity = it)) }
                    }
                    slider(R.string.aa_corner_radius, card.cornerRadius, 0f..28f, R.string.aa_dp, visible = !card.followRichContent) {
                        update { copy(inspirationAppearance = inspirationAppearance.copy(cornerRadius = it)) }
                    }
                }
            }
            item("color") {
                val modes = TextColorMode.entries.zip(listOf(R.string.aa_text_theme, R.string.aa_text_clear, R.string.aa_text_background, R.string.aa_text_wallpaper))
                val styles = AppearanceColorStyle.entries.zip(listOf(R.string.aa_tonal_spot, R.string.aa_neutral, R.string.aa_vibrant, R.string.aa_expressive, R.string.aa_monochrome))
                val wallpaper = appearance.textColorMode == TextColorMode.SYSTEM_WALLPAPER
                CardGroup(title = { Text(stringResource(R.string.aa_color)) }) {
                    toggle(R.string.aa_auto_accent, appearance.enableAutoAccent, enabled = pictureControls) { update { copy(enableAutoAccent = it) } }
                    animatedItem(visible = { pictureControls && appearance.enableAutoAccent }) {
                        if (accent == null) {
                            Text(stringResource(R.string.aa_no_background_accent))
                        } else {
                            Box(Modifier.fillMaxWidth().height(16.dp).background(Color(accent), CircleShape))
                        }
                    }
                    choice(R.string.aa_text_mode, modes.toMap().getValue(appearance.textColorMode), modes) { update { copy(textColorMode = it) } }
                    animatedItem(visible = { wallpaper }, onClick = if (importing) null else { { wallpaperPicker.launch(imageRequest) } }) {
                        Text(stringResource(if (importing) R.string.aa_importing else R.string.aa_wallpaper_image))
                    }
                    animatedItem(visible = { wallpaper && appearance.wallpaperTextSeedArgb != null }, onClick = {
                        update { copy(wallpaperTextSeedArgb = null, wallpaperTextSeedWallpaperId = null) }
                    }) { Text(stringResource(R.string.aa_wallpaper_reset)) }
                    choice(R.string.aa_color_style, styles.toMap().getValue(appearance.colorStyle), styles) { update { copy(colorStyle = it) } }
                    slider(R.string.aa_contrast, appearance.colorContrast, -1f..1f, R.string.aa_ratio) { update { copy(colorContrast = it) } }
                }
            }
            item("performance") {
                val gradient = GradientRendererMode.entries.zip(listOf(R.string.aa_auto, R.string.aa_agsl, R.string.aa_kotlin))
                CardGroup(title = { Text(stringResource(R.string.aa_performance)) }) {
                    animatedItem(visible = { !canRefract }) {
                        Text(stringResource(if (canBlur) R.string.aa_no_refraction else R.string.aa_no_live_blur))
                    }
                    toggle(R.string.aa_reduce_motion, appearance.respectSystemReducedMotion) { update { copy(respectSystemReducedMotion = it) } }
                    toggle(R.string.aa_adaptive, appearance.liquidGlass.adaptivePerformance, enabled = canBlur,
                        detail = hint(R.string.aa_adaptive_hint)) {
                        update { copy(liquidGlass = liquidGlass.copy(adaptivePerformance = it)) }
                    }
                    toggle(R.string.aa_input_effects, appearance.enableInputPerformanceEffects) { update { copy(enableInputPerformanceEffects = it) } }
                    toggle(R.string.aa_topbar_effects, appearance.enableTopBarPerformanceEffects, enabled = canBlur) { update { copy(enableTopBarPerformanceEffects = it) } }
                    toggle(R.string.aa_dock_effects, appearance.enableChatDockPerformanceEffects) { update { copy(enableChatDockPerformanceEffects = it) } }
                    toggle(R.string.aa_overlay_effects, appearance.enableOverlayPerformanceEffects) { update { copy(enableOverlayPerformanceEffects = it) } }
                    toggle(R.string.aa_navigation_effects, appearance.enableNavigationPerformanceEffects) { update { copy(enableNavigationPerformanceEffects = it) } }
                    toggle(R.string.aa_bubble_effects, appearance.enableBubblePerformanceEffects) { update { copy(enableBubblePerformanceEffects = it) } }
                    toggle(R.string.aa_rich_effects, appearance.enableRichContentPerformanceEffects) { update { copy(enableRichContentPerformanceEffects = it) } }
                    toggle(R.string.aa_gradient_effects, appearance.enableGradientPerformanceEffects) { update { copy(enableGradientPerformanceEffects = it) } }
                    choice(R.string.aa_gradient_renderer, gradient.toMap().getValue(appearance.gradientRendererMode), gradient, enabled = canRefract) {
                        update { copy(gradientRendererMode = it) }
                    }
                }
            }
        }
    }
    if (image != null && preview && pictureControls && appearance.enableAdvancedAppearance) {
        Dialog(onDismissRequest = { preview = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            SeparateWindowContent {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    TopAppBar(title = { Text(stringResource(R.string.aa_background_preview)) }, actions = {
                        TextButton(onClick = { preview = false }) { Text(stringResource(R.string.aa_close_preview)) }
                    })
                    AppearanceBackdrop(image, appearance, Modifier.fillMaxSize())
                }
            }
        }
    }
}

private fun CardGroupScope.toggle(
    @StringRes title: Int, value: Boolean, visible: Boolean? = null, enabled: Boolean = true,
    detail: (@Composable () -> Unit)? = null, onChange: (Boolean) -> Unit,
) {
    val headline: @Composable () -> Unit = { Text(stringResource(title)) }
    if (visible == null) switchItem(value, onChange, enabled = enabled, supportingContent = detail, headlineContent = headline)
    else animatedSwitchItem({ visible }, value, onChange, enabled = enabled, supportingContent = detail, headlineContent = headline)
}

private fun CardGroupScope.slider(
    @StringRes title: Int, value: Float, range: ClosedFloatingPointRange<Float>,
    @StringRes format: Int = R.string.aa_percent, visible: Boolean = true, enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    onChange: (Float) -> Unit,
) {
    val shown = value.coerceIn(range)
    animatedFormItem(
        visible = { visible },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = supportingContent,
        trailingContent = {
            val label = if (format == R.string.aa_percent) stringResource(format, (shown * 100f).roundToInt()) else stringResource(format, shown)
            CardGroupValue(label)
        },
    ) {
        AppSlider(value = shown, onValueChange = onChange, enabled = enabled, valueRange = range)
    }
}

private fun blurRange(max: Float): ClosedFloatingPointRange<Float> {
    val supported = resolvedLiveAppearanceRadius(max, Build.VERSION.SDK_INT, effects = true)
    return 0f..(supported.takeIf { it > 0f } ?: max)
}

/** 表单项下面的辅助说明；可以直接交给 supportingContent / detail。 */
private fun hint(@StringRes text: Int): @Composable () -> Unit = { Text(stringResource(text)) }

private fun <T> CardGroupScope.choice(
    @StringRes title: Int, @StringRes current: Int, options: List<Pair<T, Int>>, visible: Boolean = true,
    enabled: Boolean = true, supportingContent: (@Composable () -> Unit)? = null, onChange: (T) -> Unit,
) = animatedFormItem(
    visible = { visible },
    headlineContent = { Text(stringResource(title)) },
    supportingContent = supportingContent,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(enabled = enabled, onClick = { expanded = true }) { Text(stringResource(current)) }
        DropdownMenu(expanded = expanded && enabled && visible, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { expanded = false; onChange(value) })
            }
        }
    }
}

private fun surfaceOptions() = BackgroundSurfaceStyle.entries.map { it to it.label() }

@StringRes
private fun BackgroundSurfaceStyle.label(): Int = when (this) {
    BackgroundSurfaceStyle.OPAQUE -> R.string.aa_opaque
    BackgroundSurfaceStyle.TRANSLUCENT -> R.string.aa_translucent
    BackgroundSurfaceStyle.FROSTED -> R.string.aa_frosted
    BackgroundSurfaceStyle.LIQUID_GLASS -> R.string.aa_liquid_glass
    BackgroundSurfaceStyle.ACRYLIC -> R.string.aa_acrylic
    BackgroundSurfaceStyle.MICA -> R.string.aa_mica
}

private data class ImportedAppearanceImage(val bytes: ByteArray, val mimeType: String)

private fun readAppearanceImage(resolver: ContentResolver, uri: Uri): ImportedAppearanceImage {
    require(uri.scheme == ContentResolver.SCHEME_CONTENT)
    val bytes = requireNotNull(resolver.openInputStream(uri)).use { input ->
        val buffer = ByteArray(32 * 1024)
        val output = ByteArrayOutputStream()
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() <= MAX_APPEARANCE_IMAGE_BYTES - count)
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
    val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, info)
    require(info.isAppearanceImage())
    val longestSide = maxOf(info.outWidth, info.outHeight)
    val sample = Integer.highestOneBit(longestSide / 128).coerceAtLeast(1)
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    // A valid header alone does not mean the image bytes will decode.
    val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
    bitmap.recycle()
    return ImportedAppearanceImage(bytes, requireNotNull(info.outMimeType))
}

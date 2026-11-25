package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.isGlobalBackgroundAppliedToChat
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.CroppedImage
import me.rerere.rikkahub.ui.components.ui.LocalAdvancedAppearanceBusy
import me.rerere.rikkahub.ui.components.ui.LocalAdvancedAppearancePowerSave
import me.rerere.rikkahub.ui.components.ui.VideoSurface
import me.rerere.rikkahub.ui.theme.AppearanceBackdrop
import me.rerere.rikkahub.ui.theme.LocalAnimationsEnabled
import me.rerere.rikkahub.ui.theme.LocalAppearanceImage
import me.rerere.rikkahub.ui.theme.LocalUiSlots
import me.rerere.rikkahub.ui.theme.readableBackdropColor

@Composable
fun AssistantBackground(
    setting: Settings,
    modifier: Modifier,
    // 背景图上的全屏渐变遮罩：竖屏留着压住亮部、保证正文可读；横屏三栏里去掉
    scrim: Boolean = true,
    // 生成中停掉网格背景的漂移：它是所有模糊效果的源，相位一动整屏模糊就要重算
    animateGradient: Boolean = true,
    assistant: Assistant = setting.getCurrentAssistant(),
) {
    val appearance = setting.advancedAppearanceSetting
    val advanced = appearance.enableAdvancedAppearance
    val base = LocalUiSlots.current.pageBackground
    val readable = advanced && appearance.enableChatTextReadability
    val image = if (setting.isGlobalBackgroundAppliedToChat()) LocalAppearanceImage.current?.image else null
    // Capture the theme base as well, so translucent images and the no-image state have a backdrop.
    val backdrop = modifier.fillMaxSize().background(base)
    if (image != null) {
        Box(backdrop) {
            AppearanceBackdrop(image, appearance, Modifier.fillMaxSize())
            if (scrim || readable) {
                Box(Modifier.matchParentSize().background(chatBackdropScrim(base, readable)))
            }
        }
        return
    }
    if (assistant.useGradientBackground) {
        val animate = animateGradient && (!advanced || (appearance.enableGradientPerformanceEffects &&
            LocalAnimationsEnabled.current && !LocalAdvancedAppearancePowerSave.current &&
            !(LocalAdvancedAppearanceBusy.current && appearance.liquidGlass.adaptivePerformance)))
        MeshGradientBackground(modifier = backdrop, animate = animate,
            renderer = appearance.gradientRendererMode.takeIf { advanced }) {
            if (readable) Box(Modifier.matchParentSize().background(chatBackdropScrim(base, true)))
        }
        return
    }
    if (assistant.background != null) {
        val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
        val blurRadius = (assistant.backgroundBlur.coerceIn(0f, 1f) * 24f).dp
        val blurRadiusPx = with(LocalDensity.current) { blurRadius.toPx() }
        Box(modifier = backdrop) {
            when {
                assistant.backgroundIsVideo -> VideoSurface(
                    model = assistant.background,
                    modifier = Modifier.fillMaxSize(),
                    maxVideoSize = 1280,
                    alpha = backgroundOpacity,
                    blurRadiusPx = blurRadiusPx,
                )

                assistant.backgroundCrop != null -> CroppedImage(
                    model = assistant.background,
                    crop = assistant.backgroundCrop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(backgroundOpacity)
                        .then(
                            if (blurRadius > 0.dp) {
                                Modifier.blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                            } else {
                                Modifier
                            }
                        ),
                    cover = true,
                    requestSize = 2048,
                )

                else -> AsyncImage(
                    model = assistant.background,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(backgroundOpacity)
                        .then(
                            if (blurRadius > 0.dp) {
                                Modifier.blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                            } else {
                                Modifier
                            }
                        )
                )
            }

            if (scrim || readable) {
                // 全屏渐变遮罩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            chatBackdropScrim(base, readable)
                        )
                )
            }
        }
    } else {
        Box(backdrop)
    }
}

@Composable
private fun chatBackdropScrim(base: Color, readable: Boolean): Brush {
    if (!readable) return Brush.verticalGradient(listOf(base.copy(alpha = .2f), base.copy(alpha = .5f)))
    val top = readableBackdropColor(base, MaterialTheme.colorScheme.onBackground, minimum = .58f)
    return Brush.verticalGradient(listOf(top, top.copy(alpha = maxOf(.74f, top.alpha))))
}

/** Read visibility/scroll booleans, not the IME animation frames or scroll offsets. */
@Composable
internal fun chatAppearanceBusy(enabled: Boolean, list: LazyListState, generating: Boolean): Boolean {
    if (!enabled) return false
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val busy by remember(ime, density, list) {
        derivedStateOf { list.isScrollInProgress || ime.getBottom(density) > 0 }
    }
    return generating || busy
}

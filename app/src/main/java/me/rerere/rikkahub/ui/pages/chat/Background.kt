package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.ui.components.ui.CroppedImage
import me.rerere.rikkahub.ui.components.ui.VideoSurface
import me.rerere.rikkahub.ui.theme.LocalUiSlots

@Composable
fun AssistantBackground(
    setting: Settings,
    modifier: Modifier,
    // 背景图上的全屏渐变遮罩：竖屏留着压住亮部、保证正文可读；横屏三栏里去掉
    scrim: Boolean = true,
) {
    val assistant = setting.getCurrentAssistant()
    if (assistant.useGradientBackground) {
        MeshGradientBackground(modifier = modifier)
        return
    }
    if (assistant.background != null) {
        val backgroundColor = LocalUiSlots.current.pageBackground
        val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
        val blurRadius = (assistant.backgroundBlur.coerceIn(0f, 1f) * 24f).dp
        val blurRadiusPx = with(LocalDensity.current) { blurRadius.toPx() }
        Box(modifier = modifier) {
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

            if (scrim) {
                // 全屏渐变遮罩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    backgroundColor.copy(alpha = 0.2f),
                                    backgroundColor.copy(alpha = 0.5f)
                                )
                            )
                        )
                )
            }
        }
    }
}

package me.rerere.rikkahub.ui.pages.chat

import android.os.Build
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleTarget
import me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.LiquidGlassRenderer
import me.rerere.rikkahub.data.model.parameters
import me.rerere.rikkahub.data.model.usesMaterial3Style
import me.rerere.rikkahub.ui.components.message.messageBubbleMaterial
import me.rerere.rikkahub.ui.components.message.thinkingBubbleMaterial
import me.rerere.rikkahub.ui.components.richtext.richContentMaterial
import me.rerere.rikkahub.ui.components.ui.AppearanceMaterial
import me.rerere.rikkahub.ui.components.ui.LocalAdvancedAppearancePowerSave
import me.rerere.rikkahub.ui.components.ui.composerMaterial
import me.rerere.rikkahub.ui.components.ui.navigationMaterial
import me.rerere.rikkahub.ui.components.ui.overlayMaterial
import me.rerere.rikkahub.ui.components.ui.resolvedLiveAppearanceRadius
import me.rerere.rikkahub.ui.components.ui.topBarMaterial
import me.rerere.ui.components.LocalSeparateWindow

/** Whether the chat background and message list need to be recorded as Haze sources. */
internal data class ChatHazeCapturePlan(
    val background: Boolean,
    val chrome: Boolean,
    val topBarSamplesMessages: Boolean = false,
)

/**
 * Decide source capture from actual active materials and the same SDK/power/busy gates as rendering.
 * A zero-radius ordinary blur has no backdrop effect; Salt refraction is independent of blur radius.
 */
internal fun AppearanceMaterial.needsChatBackdrop(
    appearance: AdvancedAppearanceSetting,
    sdk: Int,
    powerSave: Boolean,
    busy: Boolean,
    separateWindow: Boolean,
): Boolean {
    if (!style.usesBlur || !effects || sdk < Build.VERSION_CODES.S || powerSave || separateWindow) return false
    if (busy && appearance.liquidGlass.adaptivePerformance && !allowDuringBusy) return false
    val material3 = style == BackgroundSurfaceStyle.LIQUID_GLASS && appearance.liquidGlass.usesMaterial3Style()
    val realtime = effects && !(style == BackgroundSurfaceStyle.LIQUID_GLASS &&
        appearance.liquidGlass.renderer == LiquidGlassRenderer.STANDARD)
    val radius = resolvedLiveAppearanceRadius(
        if (material3) 16f else this.radius,
        sdk,
        realtime,
    )
    val saltRefraction = realtime && style == BackgroundSurfaceStyle.LIQUID_GLASS && !material3 &&
        sdk >= Build.VERSION_CODES.TIRAMISU && appearance.liquidGlass.parameters().refraction > 0f
    return saltRefraction || radius > 0f
}

/** Material-driven Haze source plan for the chat page; no recording when every surface is static. */
internal fun Settings.chatHazeCapturePlan(
    sdk: Int,
    powerSave: Boolean,
    busy: Boolean,
    separateWindow: Boolean,
): ChatHazeCapturePlan {
    val appearance = advancedAppearanceSetting
    val display = displaySetting
    // Independent frosted messages can also sample the backdrop with advanced appearance off.
    val bodyMaterials = listOfNotNull(
        appearance.messageBubbleMaterial(display, ChatBubbleStyleTarget.USER, null, streaming = false),
        appearance.messageBubbleMaterial(display, ChatBubbleStyleTarget.ASSISTANT, null, streaming = false)
            .takeIf { display.showAssistantBubble },
        appearance.richContentMaterial(streaming = false),
        appearance.thinkingBubbleMaterial(display, null, streaming = false),
        appearance.navigationMaterial().takeIf { appearance.enableAdvancedAppearance },
    )
    fun AppearanceMaterial.active() = needsChatBackdrop(appearance, sdk, powerSave, busy, separateWindow)
    val background = bodyMaterials.any { it.active() }
    if (!appearance.enableAdvancedAppearance) return ChatHazeCapturePlan(background, chrome = false)

    val topBarMaterial = appearance.topBarMaterial(display)
    val topBarSamplesMessages = topBarMaterial?.active() == true
    val chromeMaterials = listOfNotNull(
        appearance.composerMaterial(display),
        appearance.overlayMaterial(),
        // 悬浮工具栏换成 M3 FloatingToolbar 之后不再接玻璃底板，
        // 这里也不再为它申请 haze 源（省掉一份没人用的模糊开销）。
        // Either the whole top bar or its controls consume this source.
        topBarMaterial,
    )
    return ChatHazeCapturePlan(
        background = background,
        chrome = chromeMaterials.any { it.active() },
        topBarSamplesMessages = topBarSamplesMessages,
    )
}

internal data class ChatHazeCaptureState(
    val plan: ChatHazeCapturePlan,
    val busy: Boolean,
)

@Composable
internal fun rememberChatHazeCaptureState(
    settings: Settings,
    list: LazyListState,
    generating: Boolean,
): ChatHazeCaptureState {
    val advanced = settings.advancedAppearanceSetting.enableAdvancedAppearance
    val busy = chatAppearanceBusy(
        enabled = advanced || settings.displaySetting.chatMessageBackgroundStyle == ChatMessageBackgroundStyle.FROSTED,
        list = list,
        generating = generating,
    )
    val plan = settings.chatHazeCapturePlan(
        sdk = Build.VERSION.SDK_INT,
        powerSave = LocalAdvancedAppearancePowerSave.current,
        busy = busy,
        separateWindow = LocalSeparateWindow.current,
    )
    return remember(plan, busy) { ChatHazeCaptureState(plan, busy) }
}

internal fun Modifier.chatHazeSources(
    backgroundState: HazeState?,
    chromeState: HazeState?,
): Modifier = then(if (backgroundState != null) Modifier.hazeSource(backgroundState) else Modifier)
    .then(if (chromeState != null) Modifier.hazeSource(chromeState) else Modifier)

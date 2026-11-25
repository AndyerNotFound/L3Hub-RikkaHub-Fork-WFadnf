package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleOverrides
import me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.data.model.LiquidGlassRenderer
import me.rerere.rikkahub.data.model.LiquidGlassSettings
import me.rerere.rikkahub.data.model.LiquidGlassStyle
import me.rerere.rikkahub.data.model.LiquidGlassPreset
import me.rerere.rikkahub.data.model.LiquidGlassParameters
import me.rerere.rikkahub.ui.components.ui.AppearanceMaterial
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHazeSamplingTest {
    private fun AppearanceMaterial.needs(
        appearance: AdvancedAppearanceSetting = AdvancedAppearanceSetting(enableAdvancedAppearance = true),
        sdk: Int = 33,
        powerSave: Boolean = false,
        busy: Boolean = false,
    ) = needsChatBackdrop(appearance, sdk, powerSave, busy, separateWindow = false)

    @Test
    fun `ordinary blur only records when a live positive radius is available`() {
        val frosted = AppearanceMaterial(BackgroundSurfaceStyle.FROSTED, opacity = .5f, radius = 16f)
        assertFalse(frosted.needs(sdk = 30))
        assertTrue(frosted.needs(sdk = 31))
        assertTrue(frosted.needs(sdk = 33))
        assertFalse(frosted.copy(radius = 0f).needs())
        assertFalse(frosted.needs(powerSave = true))
        assertFalse(frosted.needs(busy = true))
        assertTrue(frosted.needs(appearance = AdvancedAppearanceSetting(
            enableAdvancedAppearance = true,
            liquidGlass = LiquidGlassSettings(adaptivePerformance = false),
        ), busy = true))
    }

    @Test
    fun `hidden assistant-only frosted bubble does not keep chat background capture active`() {
        val settings = Settings(
            displaySetting = DisplaySetting(
                chatMessageBackgroundStyle = ChatMessageBackgroundStyle.FROSTED,
                showAssistantBubble = false,
                userChatBubbleStyleOverrides = ChatBubbleStyleOverrides(blurSigma = 0f),
                assistantChatBubbleStyleOverrides = ChatBubbleStyleOverrides(blurSigma = 8f),
            ),
        )

        val plan = settings.chatHazeCapturePlan(
            sdk = 33,
            powerSave = false,
            busy = false,
            separateWindow = false,
        )

        assertFalse(plan.background)
        assertFalse(plan.chrome)
    }

    @Test
    fun `Salt refraction is independent from ordinary blur radius`() {
        val salt = AppearanceMaterial(BackgroundSurfaceStyle.LIQUID_GLASS, opacity = .5f, radius = 0f)
        val appearance = AdvancedAppearanceSetting(
            enableAdvancedAppearance = true,
            liquidGlass = LiquidGlassSettings(
                preset = LiquidGlassPreset.CUSTOM,
                custom = LiquidGlassParameters(refraction = 6f),
                renderer = LiquidGlassRenderer.AUTO,
                style = LiquidGlassStyle.SALT,
            ),
        )
        assertFalse(salt.needs(appearance, sdk = 32))
        assertTrue(salt.needs(appearance, sdk = 33))
        assertFalse(salt.needs(appearance.copy(liquidGlass = appearance.liquidGlass.copy(renderer = LiquidGlassRenderer.STANDARD))))
    }

    @Test
    fun `RikkaHub glass has fixed radius while refraction stays renderer gated`() {
        val original = AppearanceMaterial(BackgroundSurfaceStyle.LIQUID_GLASS, opacity = .5f, radius = 0f)
        val appearance = AdvancedAppearanceSetting(
            enableAdvancedAppearance = true,
            liquidGlass = LiquidGlassSettings(style = LiquidGlassStyle.MATERIAL3),
        )
        assertFalse(original.needs(appearance, sdk = 30))
        assertTrue(original.needs(appearance, sdk = 31))
        assertTrue(original.needs(appearance, sdk = 33))
        assertFalse(original.needs(appearance.copy(liquidGlass = appearance.liquidGlass.copy(renderer = LiquidGlassRenderer.STANDARD))))
    }
}

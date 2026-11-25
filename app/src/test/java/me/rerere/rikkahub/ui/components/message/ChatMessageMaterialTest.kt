package me.rerere.rikkahub.ui.components.message

import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.datastore.ChatBubbleStyle
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleOverrides
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleTarget
import me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.RichContentStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMessageMaterialTest {
    @Test
    fun `frosted role overrides win over advanced defaults`() {
        val appearance = AdvancedAppearanceSetting(
            enableAdvancedAppearance = true,
            chatBubbleStyle = ChatBubbleStyle.FROSTED,
            chatBubbleSurfaceOpacity = .8f,
            chatBubbleBlurRadius = 12f,
        )
        val display = DisplaySetting(
            chatMessageBackgroundStyle = ChatMessageBackgroundStyle.FROSTED,
            userChatBubbleStyleOverrides = ChatBubbleStyleOverrides(
                frostedOpacity = .2f,
                blurSigma = 4f,
            ),
        )
        val material = requireNotNull(appearance.messageBubbleMaterial(
            display,
            ChatBubbleStyleTarget.USER,
            resolved = null,
            streaming = false,
        ))
        assertEquals(.2f, material.opacity, 0f)
        assertEquals(6f, material.radius, 0f)
    }

    @Test
    fun `default style ignores role overrides and follows material`() {
        val appearance = AdvancedAppearanceSetting(
            enableAdvancedAppearance = true,
            chatBubbleStyle = ChatBubbleStyle.FROSTED,
            chatBubbleBlurRadius = 9f,
        )
        val display = DisplaySetting(
            chatMessageBackgroundStyle = ChatMessageBackgroundStyle.DEFAULT,
            bubbleOpacity = .5f,
            userChatBubbleStyleOverrides = ChatBubbleStyleOverrides(frostedOpacity = .05f, blurSigma = 1f),
        )
        val material = requireNotNull(appearance.messageBubbleMaterial(
            display,
            ChatBubbleStyleTarget.USER,
            resolved = null,
            streaming = false,
        ))
        assertEquals(.64f, material.opacity, .0001f)
        assertEquals(9f, material.radius, 0f)
    }

    @Test
    fun `empty frosted role uses the material opacity when no migrated alpha exists`() {
        val appearance = AdvancedAppearanceSetting(enableAdvancedAppearance = true)
        val display = DisplaySetting(
            chatMessageBackgroundStyle = ChatMessageBackgroundStyle.FROSTED,
            bubbleOpacity = .5f,
            userChatBubbleStyleOverrides = ChatBubbleStyleOverrides(),
        )

        val material = requireNotNull(appearance.messageBubbleMaterial(
            display, ChatBubbleStyleTarget.USER, resolved = null, streaming = false,
        ))

        assertEquals(.64f, material.opacity, .0001f)
        assertEquals(18f, material.radius, 0f)
    }

    @Test
    fun `empty frosted bubble roles inherit migrated opacity and twelve dp radius`() {
        val appearance = AdvancedAppearanceSetting(
            enableAdvancedAppearance = true,
            chatBubbleStyle = ChatBubbleStyle.FROSTED,
            chatBubbleSurfaceOpacity = .12f,
            chatBubbleBlurRadius = 12f,
        )
        val display = DisplaySetting(
            chatMessageBackgroundStyle = ChatMessageBackgroundStyle.FROSTED,
            userChatBubbleStyleOverrides = ChatBubbleStyleOverrides(),
            assistantChatBubbleStyleOverrides = ChatBubbleStyleOverrides(),
        )

        listOf(ChatBubbleStyleTarget.USER, ChatBubbleStyleTarget.ASSISTANT).forEach { target ->
            val material = requireNotNull(appearance.messageBubbleMaterial(display, target, null, streaming = false))
            assertEquals(.12f, material.opacity, 0f)
            assertEquals(12f, material.radius, 0f)
        }
    }

    @Test
    fun `empty thinking role inherits rich content opacity and sixteen dp radius`() {
        val appearance = AdvancedAppearanceSetting(
            enableAdvancedAppearance = true,
            richContentStyle = RichContentStyle.TRANSLUCENT,
            richContentSurfaceOpacity = .14f,
            richContentSurfaceBlurRadius = 16f,
        )
        val display = DisplaySetting(
            chatMessageBackgroundStyle = ChatMessageBackgroundStyle.FROSTED,
            customizeThinkingToolCards = true,
            thinkingToolCardStyleOverrides = ChatBubbleStyleOverrides(),
        )

        val material = requireNotNull(appearance.thinkingBubbleMaterial(display, resolved = null, streaming = false))
        assertEquals(.14f, material.opacity, 0f)
        assertEquals(16f, material.radius, 0f)
    }

    @Test
    fun `thinking cards inherit rich content rather than body bubble settings`() {
        val appearance = AdvancedAppearanceSetting(
            enableAdvancedAppearance = true,
            chatBubbleSurfaceOpacity = .9f,
            chatBubbleBlurRadius = 39f,
            richContentStyle = RichContentStyle.TRANSLUCENT,
            richContentSurfaceOpacity = .4f,
            richContentSurfaceBlurRadius = 7f,
            enableRichContentPerformanceEffects = false,
        )
        val display = DisplaySetting(
            chatMessageBackgroundStyle = ChatMessageBackgroundStyle.FROSTED,
            customizeThinkingToolCards = true,
            thinkingToolCardStyleOverrides = ChatBubbleStyleOverrides(frostedOpacity = .3f, blurSigma = 3f),
        )
        val material = requireNotNull(appearance.thinkingBubbleMaterial(display, resolved = null, streaming = false))
        assertEquals(.3f, material.opacity, 0f)
        assertEquals(4.5f, material.radius, 0f)
        assertEquals(false, material.effects)

        val solid = display.copy(chatMessageBackgroundStyle = ChatMessageBackgroundStyle.SOLID)
        assertNull(appearance.thinkingBubbleMaterial(solid, resolved = null, streaming = false))
    }
}

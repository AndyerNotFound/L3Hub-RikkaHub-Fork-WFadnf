package me.rerere.rikkahub.ui.components.message

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleOverrides
import me.rerere.rikkahub.data.datastore.ChatBubbleStyleTarget
import me.rerere.rikkahub.data.datastore.ChatMessageBackgroundStyle
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.ui.resolveBubbleStyle
import me.rerere.rikkahub.ui.components.ui.resolveMessageStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 消息样式的接线与边界。
 *
 * 气泡现在只剩底色、描边、圆角三类可调项：这里钉住「默认档不覆盖预设」「磨砂/实色各自的
 * 默认透明度」「按角色的覆盖项优先」，以及边框宽度对非法值与超限值的退回。
 */
class ChatMessageMaterialTest {
    private val scheme = lightColorScheme()

    /**
     * Compose 的 Color 把 alpha 按 8 位量化存储：.66 落到 168/255 = 0.6588，.3 落到
     * 77/255 = 0.3020。断言按一个量化步长（1/255）留余量，而不是用 1e-3 硬比。
     */
    private val alphaStep = 1f / 255f

    @Test
    fun `default message style leaves the preset untouched`() {
        val display = DisplaySetting(
            chatMessageBackgroundStyle = ChatMessageBackgroundStyle.DEFAULT,
            assistantChatBubbleStyleOverrides = ChatBubbleStyleOverrides(frostedOpacity = .2f),
        )

        assertNull(resolveMessageStyle(scheme, display, ChatBubbleStyleTarget.ASSISTANT))
    }

    @Test
    fun `frosted and solid keep their own opacity defaults`() {
        val frosted = resolveBubbleStyle(scheme, ChatMessageBackgroundStyle.FROSTED, ChatBubbleStyleOverrides())
        val solid = resolveBubbleStyle(scheme, ChatMessageBackgroundStyle.SOLID, ChatBubbleStyleOverrides())

        assertEquals(.66f, frosted.background.alpha, alphaStep)
        assertEquals(1f, solid.background.alpha, alphaStep)
        assertEquals(16f, frosted.radius.value, 1e-6f)
    }

    @Test
    fun `per role overrides win over the style default`() {
        val display = DisplaySetting(
            chatMessageBackgroundStyle = ChatMessageBackgroundStyle.FROSTED,
            assistantChatBubbleStyleOverrides = ChatBubbleStyleOverrides(
                frostedOpacity = .3f,
                borderWidth = 2f,
                cornerRadius = 8f,
            ),
        )

        val resolved = requireNotNull(resolveMessageStyle(scheme, display, ChatBubbleStyleTarget.ASSISTANT))
        assertEquals(.3f, resolved.background.alpha, alphaStep)
        assertEquals(2f, resolved.borderWidth.value, 1e-6f)
        assertEquals(8f, resolved.radius.value, 1e-6f)
    }

    @Test
    fun `border width clamps non finite and oversized input`() {
        assertEquals(1f, resolvedMessageBorderWidth(Float.NaN.dp).value, 1e-6f)
        assertEquals(8f, resolvedMessageBorderWidth(40f.dp).value, 1e-6f)
        assertEquals(2f, resolvedMessageBorderWidth(2f.dp).value, 1e-6f)
        assertEquals(0f, resolvedMessageBorderWidth(0f.dp).value, 1e-6f)
    }
}

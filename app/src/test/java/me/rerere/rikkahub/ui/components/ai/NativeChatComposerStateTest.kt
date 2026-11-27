package me.rerere.rikkahub.ui.components.ai

import androidx.compose.ui.text.TextRange
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionItem
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionList
import me.rerere.rikkahub.ui.hooks.ChatInputState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

/** JVM state-boundary tests; native IME/composing and layout still need device coverage. */
class NativeChatComposerStateTest {
    @Test
    fun `native edits preserve reverse selection and do not replace attachments`() {
        val attachment = UIMessagePart.Image("file:///managed/image.png")
        val state = ChatInputState().apply { messageContent = listOf(attachment) }

        applyNativeTextEdit(state, "hello", 4, 1)

        assertEquals("hello", state.textContent.text.toString())
        assertEquals(TextRange(4, 1), state.textContent.selection)
        assertEquals(listOf(attachment), state.messageContent)
    }

    @Test
    fun `selection-only change keeps text and clamps each end independently`() {
        val state = ChatInputState().apply { setMessageText("hello") }

        applyNativeSelectionEdit(state, 99, -5)

        assertEquals("hello", state.textContent.text.toString())
        assertEquals(TextRange(5, 0), state.textContent.selection)
    }

    @Test
    fun `same-text native echo does not reset reverse selection`() {
        val state = ChatInputState()
        applyNativeTextEdit(state, "hello", 5, 2)
        applyNativeTextEdit(state, "hello", 5, 2)

        assertEquals(TextRange(5, 2), state.textContent.selection)
    }

    @Test
    fun `native body edit preserves reasoning and message identity`() {
        val id = Uuid.random()
        val reasoning = UIMessagePart.Reasoning("existing thought")
        val state = ChatInputState().apply {
            editingMessage = id
            setContents(listOf(reasoning, UIMessagePart.Text("old answer")))
        }

        applyNativeTextEdit(state, "new answer", 10, 10)

        assertEquals(id, state.editingMessage)
        assertEquals("existing thought", state.editingReasoning!!.text.toString())
        assertEquals(listOf(reasoning, UIMessagePart.Text("new answer")), state.getContents())
    }

    @Test
    fun `shared completion applies range and preserves attachments`() {
        val attachment = UIMessagePart.Document("file:///managed/file.txt", "file.txt", "text/plain")
        val state = ChatInputState().apply {
            setMessageText("open @sr now")
            messageContent = listOf(attachment)
        }
        val item = ChatCompletionItem(label = "src", insertText = "@src/")
        val controller = ChatCompletionController()
        controller.publish(ChatCompletionList("workspace", TextRange(5, 8), listOf(item)))

        controller.apply(state, item)

        assertEquals("open @src/ now", state.textContent.text.toString())
        assertEquals(TextRange(10), state.textContent.selection)
        assertEquals(listOf(attachment), state.messageContent)
        assertNull(controller.visible)
    }

    @Test
    fun `dismissed completion cannot mutate the draft`() {
        val state = ChatInputState().apply { setMessageText("draft") }
        val item = ChatCompletionItem("replacement", "replacement")
        val controller = ChatCompletionController()
        controller.publish(ChatCompletionList("workspace", TextRange(0, 5), listOf(item)))
        controller.dismiss()

        controller.apply(state, item)

        assertEquals("draft", state.textContent.text.toString())
        assertNull(controller.visible)
    }
}

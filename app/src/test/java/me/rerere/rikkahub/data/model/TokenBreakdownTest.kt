package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBreakdownTest {
    @Test
    fun `prompt buckets always add up to the provider total`() {
        val messages = listOf(
            UIMessage.user("hello world"),
            UIMessage.assistant("a long answer"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                parts = listOf(
                    UIMessagePart.Tool(
                        toolCallId = "1",
                        toolName = "search",
                        input = """{"q":"kotlin"}""",
                        output = listOf(UIMessagePart.Text("result text")),
                    ),
                ),
                usage = TokenUsage(promptTokens = 1_000, completionTokens = 120),
                lastUsage = TokenUsage(promptTokens = 1_000, completionTokens = 120),
            ),
        )

        val breakdown = buildTokenBreakdown(messages, messages.last().lastUsage!!)

        assertEquals(1_000, breakdown.promptBuckets.sumOf { it.tokens })
        assertEquals(120, breakdown.completionBuckets.sumOf { it.tokens })
        assertTrue(breakdown.promptBuckets.any { it.destination == TokenDestination.SYSTEM })
        assertTrue(breakdown.promptBuckets.any { it.destination == TokenDestination.TOOL })
    }

    @Test
    fun `scales content estimates down when they exceed the real prompt`() {
        val messages = listOf(
            UIMessage.user("字".repeat(10_000)),
            UIMessage.assistant("ok"),
        )

        val breakdown = buildTokenBreakdown(
            messages,
            TokenUsage(promptTokens = 100, completionTokens = 10),
        )

        assertEquals(100, breakdown.promptBuckets.sumOf { it.tokens })
        assertTrue(breakdown.promptBuckets.none { it.destination == TokenDestination.SYSTEM })
    }

    @Test
    fun `splits completion into thinking and reply`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Reasoning("think"),
                UIMessagePart.Text("answer"),
            ),
            usage = TokenUsage(promptTokens = 10, completionTokens = 100),
            lastUsage = TokenUsage(promptTokens = 10, completionTokens = 100),
        )

        val breakdown = buildTokenBreakdown(listOf(message), message.lastUsage!!)

        val thinking = breakdown.completionBuckets.first { it.destination == TokenDestination.REASONING }
        val reply = breakdown.completionBuckets.first { it.destination == TokenDestination.OUTPUT }
        assertEquals(50, thinking.tokens)
        assertEquals(50, reply.tokens)
    }

    @Test
    fun `cached tokens are a subset of prompt tokens`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("answer")),
            usage = TokenUsage(promptTokens = 100, completionTokens = 10, cachedTokens = 500),
        )

        val breakdown = buildTokenBreakdown(listOf(message), message.usage!!)

        assertEquals(100, breakdown.cachedTokens)
        assertEquals(100, breakdown.promptBuckets.sumOf { it.tokens })
    }

    @Test
    fun `empty usage produces no buckets`() {
        val breakdown = buildTokenBreakdown(emptyList(), TokenUsage())

        assertTrue(!breakdown.hasUsage)
        assertTrue(breakdown.promptBuckets.isEmpty())
        assertTrue(breakdown.completionBuckets.isEmpty())
    }
}

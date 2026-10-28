package me.rerere.rikkahub.data.sync.importer

import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownChatImporterTest {
    @Test
    fun `parses the app export format`() {
        val markdown = """
            # 日志分析

            *Exported on 2026-01-01 12:00*

            **User**:

            帮我看看这个报错

            **Assistant**:

            好的，先贴日志。
        """.trimIndent()

        val parsed = MarkdownChatImporter.parse(markdown)

        assertEquals("日志分析", parsed.title)
        assertEquals(2, parsed.messages.size)
        assertEquals(MessageRole.USER, parsed.messages[0].role)
        assertEquals("帮我看看这个报错", parsed.messages[0].toText())
        assertEquals(MessageRole.ASSISTANT, parsed.messages[1].role)
        assertEquals("好的，先贴日志。", parsed.messages[1].toText())
    }

    @Test
    fun `parses plain markers and keeps multiline bodies`() {
        val markdown = """
            User: line one
            line two
            Assistant: reply
        """.trimIndent()

        val parsed = MarkdownChatImporter.parse(markdown)

        assertEquals(2, parsed.messages.size)
        assertEquals("line one\nline two", parsed.messages[0].toText())
        assertEquals("reply", parsed.messages[1].toText())
    }

    @Test
    fun `does not split on markers inside code fences`() {
        val markdown = """
            **User**:
            看这段：

            ```
            User: 这不是新的消息
            Assistant: 也不是
            ```

            **Assistant**:
            收到
        """.trimIndent()

        val parsed = MarkdownChatImporter.parse(markdown)

        assertEquals(2, parsed.messages.size)
        assertTrue(parsed.messages[0].toText().contains("这不是新的消息"))
        assertEquals("收到", parsed.messages[1].toText())
    }

    @Test
    fun `supports heading style markers`() {
        val markdown = """
            ## User
            你好
            ## Assistant
            嗨
        """.trimIndent()

        val parsed = MarkdownChatImporter.parse(markdown)

        assertEquals(2, parsed.messages.size)
        assertEquals(MessageRole.USER, parsed.messages[0].role)
        assertEquals("嗨", parsed.messages[1].toText())
    }
}

package me.rerere.rikkahub.data.sync.importer

import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlChatImporterTest {
    @Test
    fun `parses attribute style messages`() {
        val xml = """
            <chat title="日志分析">
              <message role="user">帮我看看这个报错</message>
              <message role="assistant"><content>好的，先贴日志。</content></message>
            </chat>
        """.trimIndent()

        val chats = XmlChatImporter.parse(xml)

        assertEquals(1, chats.size)
        assertEquals("日志分析", chats.first().title)
        assertEquals(2, chats.first().messages.size)
        assertEquals(MessageRole.USER, chats.first().messages[0].role)
        assertEquals("帮我看看这个报错", chats.first().messages[0].toText())
        assertEquals(MessageRole.ASSISTANT, chats.first().messages[1].role)
        assertEquals("好的，先贴日志。", chats.first().messages[1].toText())
    }

    @Test
    fun `parses nested role tags`() {
        val xml = """
            <conversation>
              <user>你好</user>
              <assistant>嗨</assistant>
            </conversation>
        """.trimIndent()

        val chats = XmlChatImporter.parse(xml)

        assertEquals(2, chats.first().messages.size)
        assertEquals(MessageRole.USER, chats.first().messages[0].role)
        assertEquals("你好", chats.first().messages[0].toText())
        assertEquals(MessageRole.ASSISTANT, chats.first().messages[1].role)
    }

    @Test
    fun `parses multiple conversations`() {
        val xml = """
            <conversations>
              <conversation title="A">
                <message role="user">a</message>
              </conversation>
              <conversation title="B">
                <message role="user">b</message>
                <message role="assistant">bb</message>
              </conversation>
            </conversations>
        """.trimIndent()

        val chats = XmlChatImporter.parse(xml)

        assertEquals(2, chats.size)
        assertEquals("A", chats[0].title)
        assertEquals("B", chats[1].title)
        assertEquals(2, chats[1].messages.size)
    }

    @Test
    fun `does not treat role metadata as message content`() {
        val xml = """
            <chat>
              <message>
                <role>assistant</role>
                <text>第一行
            第二行</text>
              </message>
            </chat>
        """.trimIndent()

        val message = XmlChatImporter.parse(xml).first().messages.first()

        assertEquals(MessageRole.ASSISTANT, message.role)
        assertEquals("第一行\n第二行", message.toText())
    }

    @Test
    fun `rejects doctype declarations`() {
        val xml = """
            <!DOCTYPE chat [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <chat><message role="user">&xxe;</message></chat>
        """.trimIndent()

        val failed = runCatching { XmlChatImporter.parse(xml) }.isFailure

        assertTrue(failed)
    }
}

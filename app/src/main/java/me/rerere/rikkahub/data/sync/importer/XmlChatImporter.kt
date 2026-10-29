package me.rerere.rikkahub.data.sync.importer

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 从 XML 聊天记录里解析会话。
 *
 * 兼容下面几种常见写法：
 * ```
 * <chat title="标题">
 *   <message role="user">你好</message>
 *   <message role="assistant"><content>你好呀</content></message>
 * </chat>
 *
 * <conversation>
 *   <user>你好</user>
 *   <assistant>你好呀</assistant>
 * </conversation>
 * ```
 * 根节点下可以有多个 conversation/chat/session，会分别导入。
 */
object XmlChatImporter {
    data class ParsedChat(
        val title: String,
        val messages: List<UIMessage>,
    )

    private val conversationTags = setOf("conversation", "chat", "session", "dialog")
    private val messageTags = setOf("message", "msg", "item", "turn")
    private val roleTags = setOf("user", "human", "assistant", "ai", "bot", "system")
    private val roleAttributeNames = listOf("role", "from", "sender", "author", "speaker", "type")
    private val roleChildNames = setOf("role", "from", "sender", "author", "speaker")
    private val contentChildNames = setOf("content", "text", "body", "message", "parts", "value")
    private val titleChildNames = setOf("title", "name", "subject", "topic")
    private val titleAttributeNames = listOf("title", "name", "subject", "topic")

    fun parse(xml: String): List<ParsedChat> {
        val root = parseDocument(xml)
        val conversations = root.childElements()
            .filter { it.tagName.lowercase() in conversationTags }
            .ifEmpty { listOf(root) }

        return conversations.mapNotNull { element ->
            val messages = readMessages(element)
            if (messages.isEmpty()) {
                null
            } else {
                ParsedChat(
                    title = readTitle(element) ?: readTitle(root).orEmpty(),
                    messages = messages,
                )
            }
        }
    }

    private fun parseDocument(xml: String): Element {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true)) {
            "DOCTYPE is not allowed in chat XML"
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { isXIncludeAware = false }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(InputSource(StringReader(xml)))
        return document.documentElement ?: error("XML has no root element")
    }

    private fun readMessages(container: Element): List<UIMessage> {
        val result = mutableListOf<UIMessage>()
        container.childElements().forEach { element ->
            val tag = element.tagName.lowercase()
            when {
                tag in messageTags -> readMessage(element)?.let(result::add)
                tag in roleTags -> readMessage(element, fallbackRole = tag)?.let(result::add)
            }
        }
        return result
    }

    private fun readMessage(element: Element, fallbackRole: String? = null): UIMessage? {
        val role = readRole(element, fallbackRole) ?: return null
        val text = readContent(element).trim()
        if (text.isEmpty()) return null
        return UIMessage(
            role = role,
            parts = listOf(UIMessagePart.Text(text)),
        )
    }

    private fun readRole(element: Element, fallbackRole: String?): MessageRole? {
        val raw = roleAttributeNames.firstNotNullOfOrNull { name ->
            element.getAttribute(name).takeIf { it.isNotBlank() }
        } ?: element.childElements()
            .firstOrNull { it.tagName.lowercase() in roleChildNames }
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackRole
            ?: return MessageRole.USER

        return when (raw.lowercase()) {
            "user", "human", "用户", "客户" -> MessageRole.USER
            "assistant", "ai", "bot", "model", "助手", "机器人" -> MessageRole.ASSISTANT
            "system", "系统" -> MessageRole.SYSTEM
            else -> null
        }
    }

    private fun readContent(element: Element): String {
        val contentChild = element.childElements()
            .firstOrNull { it.tagName.lowercase() in contentChildNames }
        if (contentChild != null) return contentChild.textContent.orEmpty()

        val builder = StringBuilder()
        val nodes = element.childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) ?: continue
            when (node.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> builder.append(node.nodeValue.orEmpty())
                Node.ELEMENT_NODE -> {
                    val child = node as Element
                    if (child.tagName.lowercase() !in roleChildNames) {
                        builder.append(child.textContent.orEmpty())
                    }
                }
            }
        }
        return builder.toString()
    }

    private fun readTitle(element: Element): String? {
        titleAttributeNames.firstNotNullOfOrNull { name ->
            element.getAttribute(name).takeIf { it.isNotBlank() }
        }?.let { return it.trim() }
        return element.childElements()
            .firstOrNull { it.tagName.lowercase() in titleChildNames }
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node != null && node.nodeType == Node.ELEMENT_NODE) {
                result.add(node as Element)
            }
        }
        return result
    }
}

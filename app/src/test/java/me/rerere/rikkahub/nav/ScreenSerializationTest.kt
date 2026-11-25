package me.rerere.rikkahub.nav

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导航栈靠 [Screen] 的序列化名与字段名恢复：进程死亡后重开，磁盘上就是这段 JSON。
 *
 * 这里盯两件容易悄悄变的事——`WebView` 的 `offlineMessagePreview` 在旧记录里缺省为 false
 * （老记录仍然是执行 HTML），以及各路由的序列化名不能随手改（改了等于丢掉整条历史）。
 *
 * 多态序列化只在静态类型是 [Screen] / [DetailDestination] 时写出 `type` 字段，所以下面
 * 一律先赋给接口类型的变量再编码。
 */
class ScreenSerializationTest {

    private fun Screen.encode(): String = JsonInstant.encodeToString(this)

    private fun Screen.typeName(): String =
        JsonInstant.parseToJsonElement(encode()).jsonObject["type"]!!.jsonPrimitive.content

    @Test
    fun `web view defaults describe an html page`() {
        val screen = Screen.WebView()

        assertEquals("", screen.url)
        assertEquals("", screen.contentId)
        assertFalse(screen.offlineMessagePreview)
    }

    @Test
    fun `web view round trips both modes`() {
        val html: Screen = Screen.WebView(url = "https://example.com", contentId = "abc")
        val preview: Screen = Screen.WebView(contentId = "abc", offlineMessagePreview = true)

        assertEquals(html, JsonInstant.decodeFromString<Screen>(html.encode()))
        assertEquals(preview, JsonInstant.decodeFromString<Screen>(preview.encode()))
        assertNotEquals(html, preview)
    }

    // 新增字段之前写下的记录：没有 offlineMessagePreview，读回来必须是「执行 HTML」而不是预览
    @Test
    fun `legacy web view record reads as an html page`() {
        val legacy = """{"type":"me.rerere.rikkahub.Screen.WebView","url":"https://example.com","contentId":"abc"}"""

        val screen = JsonInstant.decodeFromString<Screen>(legacy)

        assertEquals(Screen.WebView(url = "https://example.com", contentId = "abc"), screen)
        assertFalse((screen as Screen.WebView).offlineMessagePreview)
    }

    @Test
    fun `legacy web view record without url reads as empty url`() {
        val legacy = """{"type":"me.rerere.rikkahub.Screen.WebView","contentId":"abc"}"""

        val screen = JsonInstant.decodeFromString<Screen>(legacy) as Screen.WebView

        assertEquals("", screen.url)
        assertEquals("abc", screen.contentId)
        assertFalse(screen.offlineMessagePreview)
    }

    // 两种模式在盘上必须分得开：预览页是 contentId + true，普通页是 url + false
    @Test
    fun `serialized web view keeps the preview flag distinguishable`() {
        val preview: Screen = Screen.WebView(contentId = "abc", offlineMessagePreview = true)
        val html: Screen = Screen.WebView(url = "https://example.com")

        val previewJson = JsonInstant.parseToJsonElement(preview.encode()).jsonObject
        val htmlJson = JsonInstant.parseToJsonElement(html.encode()).jsonObject

        assertEquals("true", previewJson["offlineMessagePreview"]?.jsonPrimitive?.content)
        assertEquals("false", htmlJson["offlineMessagePreview"]?.jsonPrimitive?.content)
        assertEquals("abc", previewJson["contentId"]?.jsonPrimitive?.content)
        assertEquals("https://example.com", htmlJson["url"]?.jsonPrimitive?.content)
    }

    // 序列化名就是落盘格式：这些字符串变了，升级后恢复出来的历史会认不出来
    @Test
    fun `screen serial names stay stable`() {
        assertEquals("me.rerere.rikkahub.Screen.WebView", Screen.WebView().typeName())
        assertEquals("me.rerere.rikkahub.Screen.Chat", Screen.Chat(id = "c").typeName())
        assertEquals(
            "me.rerere.rikkahub.Screen.SettingPreferencesAdvancedAppearance",
            Screen.SettingPreferencesAdvancedAppearance.typeName(),
        )
        assertEquals("me.rerere.rikkahub.Screen.Setting", Screen.Setting.typeName())
    }

    @Test
    fun `chat round trips with optional fields omitted`() {
        val bare: Screen = Screen.Chat(id = "chat-1")
        val full: Screen = Screen.Chat(id = "chat-1", text = "hi", files = listOf("a.txt"), nodeId = "n-1")

        assertEquals(bare, JsonInstant.decodeFromString<Screen>(bare.encode()))
        assertEquals(full, JsonInstant.decodeFromString<Screen>(full.encode()))
    }

    // 旧记录里没有 nodeId：读回来是 null，不影响其它字段
    @Test
    fun `legacy chat record without node id still decodes`() {
        val legacy = """{"type":"me.rerere.rikkahub.Screen.Chat","id":"chat-1","text":"hi"}"""

        val decoded = JsonInstant.decodeFromString<Screen>(legacy) as Screen.Chat

        assertEquals("chat-1", decoded.id)
        assertEquals("hi", decoded.text)
        assertNull(decoded.nodeId)
        assertTrue(decoded.files.isEmpty())
    }

    @Test
    fun `detail destination wraps a screen and survives serialization`() {
        val page: DetailDestination = DetailDestination.Page(Screen.SettingPreferencesAdvancedAppearance)
        val none: DetailDestination = DetailDestination.None

        assertEquals(page, JsonInstant.decodeFromString<DetailDestination>(JsonInstant.encodeToString(page)))
        assertEquals(none, JsonInstant.decodeFromString<DetailDestination>(JsonInstant.encodeToString(none)))
    }

    // 右列的空槽位与「装着某一页」在盘上要分得开，否则恢复出来右列会多出或少掉一层
    @Test
    fun `detail destination page keeps its screen`() {
        val page: DetailDestination = DetailDestination.Page(Screen.WebView(contentId = "abc", offlineMessagePreview = true))

        val decoded = JsonInstant.decodeFromString<DetailDestination>(JsonInstant.encodeToString(page))

        assertEquals(page, decoded)
        assertNotEquals(DetailDestination.None, decoded)
    }
}

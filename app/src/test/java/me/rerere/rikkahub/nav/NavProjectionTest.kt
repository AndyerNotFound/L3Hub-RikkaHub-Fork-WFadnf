package me.rerere.rikkahub.nav

import me.rerere.rikkahub.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavProjectionTest {

    private val chat = Screen.Chat(id = "chat-1")
    private val setting = Screen.Setting
    private val providers = Screen.SettingProvider
    private val providerDetail = Screen.SettingProviderDetail(providerId = "p-1")
    private val models = Screen.SettingModels
    private val modelEditor = Screen.ModelEditor(providerId = "p-1", modelId = "m-1")

    /** 分区首屏：Provider 与 Models 是，ProviderDetail 与 ModelEditor 不是 */
    private val isColumnHost: (Screen) -> Boolean =
        { it == providers || it == models || it == setting }

    @Test
    fun `single never splits`() {
        val projection = NavProjection.single(listOf(setting, providers, providerDetail))
        assertEquals(listOf(setting, providers, providerDetail), projection.root)
        assertTrue(projection.detail.isEmpty())
    }

    @Test
    fun `no column host keeps everything on the main column`() {
        val projection = NavProjection.masterDetail(listOf(chat, setting, models), { _ -> false })
        assertEquals(listOf(chat, setting, models), projection.root)
        assertTrue(projection.detail.isEmpty())
    }

    // 栈顶就是分区首屏：右列没有下钻页，槽位不该占宽度
    @Test
    fun `top being a column host leaves the right column empty`() {
        val projection = NavProjection.masterDetail(listOf(chat, setting, providers), isColumnHost)
        assertEquals(listOf(chat, setting, providers), projection.root)
        assertTrue(projection.detail.isEmpty())
        assertEquals(providers, projection.middle)
        assertEquals(providers, projection.top)
    }

    @Test
    fun `drill down from a host moves the page to the right column`() {
        val projection = NavProjection.masterDetail(
            listOf(chat, setting, providers, providerDetail),
            isColumnHost,
        )
        assertEquals(listOf(chat, setting, providers), projection.root)
        assertEquals(listOf(providerDetail), projection.detail)
        assertEquals(providerDetail, projection.top)
    }

    // 右列只画栈顶，被压住的中间页仍留在栈里，返回一格就露出来
    @Test
    fun `deeper drill down keeps the pressed pages in the right column stack`() {
        val projection = NavProjection.masterDetail(
            listOf(chat, setting, models, modelEditor, providerDetail),
            isColumnHost,
        )
        assertEquals(listOf(chat, setting, models), projection.root)
        assertEquals(listOf(modelEditor, providerDetail), projection.detail)
        assertEquals(models, projection.middle)
        assertEquals(providerDetail, projection.top)
    }

    @Test
    fun `the last column host wins`() {
        val projection = NavProjection.masterDetail(
            listOf(setting, providers, models, providerDetail),
            isColumnHost,
        )
        assertEquals(listOf(setting, providers, models), projection.root)
        assertEquals(listOf(providerDetail), projection.detail)
    }

    // 横竖屏只是铺开方式不同：同一份历史在两边的栈底与栈顶必须一致
    @Test
    fun `single and master detail share the same history ends`() {
        val logical = listOf(chat, setting, providers, providerDetail)
        val single = NavProjection.single(logical)
        val widened = NavProjection.masterDetail(logical, isColumnHost)
        assertEquals(single.middle, widened.top)
        assertEquals(single.root.first(), widened.root.first())
    }

    // 高级外观页挂在「偏好设置」这一节下面：它自己不是分区首屏，所以落在右列
    @Test
    fun `advanced appearance pane drills into the right column`() {
        val preferences = Screen.SettingPreferences
        val advancedAppearance = Screen.SettingPreferencesAdvancedAppearance
        val host: (Screen) -> Boolean = { it == providers || it == models || it == setting || it == preferences }

        val projection = NavProjection.masterDetail(
            listOf(chat, setting, preferences, advancedAppearance),
            host,
        )

        assertEquals(listOf(chat, setting, preferences), projection.root)
        assertEquals(listOf(advancedAppearance), projection.detail)
        assertEquals(preferences, projection.middle)
        assertEquals(advancedAppearance, projection.top)
    }

    @Test
    fun `returning to a section host empties the right column`() {
        val preferences = Screen.SettingPreferences
        val advancedAppearance = Screen.SettingPreferencesAdvancedAppearance
        val host: (Screen) -> Boolean = { it == setting || it == preferences }

        // 下钻到高级外观页：它落在右列
        assertEquals(
            listOf(advancedAppearance),
            NavProjection.masterDetail(listOf(setting, preferences, advancedAppearance), host).detail,
        )

        // 返回一格，栈顶重新变成分区首屏：右列不占宽度
        val backToHost = NavProjection.masterDetail(listOf(setting, preferences), host)
        assertTrue(backToHost.detail.isEmpty())
        assertEquals(preferences, backToHost.top)
    }

    // 预览页与普通网页是同一个路由、只差一个标记：投影里它们都只是历史里的一页，不额外分裂
    @Test
    fun `web view preview and html pages project like any other page`() {
        val html = Screen.WebView(url = "https://example.com")
        val preview = Screen.WebView(contentId = "abc", offlineMessagePreview = true)

        val projection = NavProjection.single(listOf(chat, html, preview))

        assertEquals(listOf(chat, html, preview), projection.root)
        assertEquals(preview, projection.top)
        assertTrue(projection.detail.isEmpty())
        // 两个路由值不相等：内容 id 与预览标记参与相等判断，历史不会把它们当成同一页
        assertTrue(html != preview)
    }

    @Test
    fun `a web view page is never a column host in the settings rail`() {
        val preview = Screen.WebView(contentId = "abc", offlineMessagePreview = true)

        val projection = NavProjection.masterDetail(listOf(chat, setting, preview), isColumnHost)

        // WebView 不是分区首屏：它跟在 Setting 后面落进右列
        assertEquals(listOf(chat, setting), projection.root)
        assertEquals(listOf(preview), projection.detail)
    }
}

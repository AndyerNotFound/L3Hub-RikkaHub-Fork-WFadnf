package me.rerere.ai.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型编辑页按 [supportsBuiltInTool] 决定列出哪些内置工具开关。
 *
 * 列出来却发不出去的开关会直接把功能弄坏：模型上留着 web_search 时，请求里没有这个工具，
 * 客户端搜索又会因为「模型已经有内置搜索」而不挂载。
 */
class ProviderBuiltInToolSupportTest {
    @Test
    fun `account providers only send tools their protocol implements`() {
        val deepSeek = ProviderSetting.DeepSeekAccount()
        // 走 Messages 协议：搜索是客户端工具，账号类型不能声称自己发得出去
        assertFalse(deepSeek.supportsBuiltInSearch)
        assertFalse(deepSeek.supportsBuiltInTool(BuiltInTools.Search))
        assertFalse(deepSeek.supportsBuiltInTool(BuiltInTools.UrlContext))
        assertFalse(deepSeek.supportsBuiltInTool(BuiltInTools.ImageGeneration))

        // ChatGPT 走 Responses 协议：搜索与出图都认，结构化 URL 抓取不认
        val chatGpt = ProviderSetting.ChatGPT()
        assertTrue(chatGpt.supportsBuiltInTool(BuiltInTools.Search))
        assertFalse(chatGpt.supportsBuiltInTool(BuiltInTools.UrlContext))
        assertTrue(chatGpt.supportsBuiltInTool(BuiltInTools.ImageGeneration))

        val grok = ProviderSetting.Grok()
        assertTrue(grok.supportsBuiltInTool(BuiltInTools.Search))
        assertFalse(grok.supportsBuiltInTool(BuiltInTools.UrlContext))
    }

    @Test
    fun `url context is gemini only`() {
        assertTrue(ProviderSetting.Google().supportsBuiltInTool(BuiltInTools.Search))
        assertTrue(ProviderSetting.Google().supportsBuiltInTool(BuiltInTools.UrlContext))
        assertFalse(ProviderSetting.Google().supportsBuiltInTool(BuiltInTools.ImageGeneration))

        assertTrue(ProviderSetting.Claude().supportsBuiltInTool(BuiltInTools.Search))
        assertFalse(ProviderSetting.Claude().supportsBuiltInTool(BuiltInTools.UrlContext))
        assertFalse(ProviderSetting.Claude().supportsBuiltInTool(BuiltInTools.ImageGeneration))
    }

    @Test
    fun `openai image generation follows the response api switch`() {
        val completions = ProviderSetting.OpenAI()
        assertFalse(completions.supportsBuiltInTool(BuiltInTools.Search))
        assertFalse(completions.supportsBuiltInTool(BuiltInTools.ImageGeneration))

        val responses = ProviderSetting.OpenAI(useResponseApi = true)
        assertTrue(responses.supportsBuiltInTool(BuiltInTools.Search))
        assertTrue(responses.supportsBuiltInTool(BuiltInTools.ImageGeneration))
        assertFalse(responses.supportsBuiltInTool(BuiltInTools.UrlContext))
    }
}

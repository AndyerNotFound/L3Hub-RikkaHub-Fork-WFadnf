package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.availableKeys
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 模型按 Key 分组路由：同名的模型各绑各的 Key，请求不能串到别的分组去。
 */
class ModelKeyRoutingTest {

    private fun provider(
        models: List<Model>,
        apiKey: String = "sk-one\nsk-two",
        disabledApiKeys: List<String> = emptyList(),
    ) = ProviderSetting.OpenAI(
        models = models,
        apiKey = apiKey,
        disabledApiKeys = disabledApiKeys,
        baseUrl = "https://api.example.com/v1",
    )

    /** 走一遍请求层的入口，拿实际会用的 Key */
    private fun keysFor(model: Model, provider: ProviderSetting): List<String> {
        val routed = model.findProvider(listOf(provider))
        return (routed as ProviderSetting.ApiKey).availableKeys
    }

    @Test
    fun `未绑定 Key 的模型沿用提供商的全部启用 Key`() {
        val model = Model(modelId = "gpt-4o", displayName = "GPT-4o")
        assertEquals(listOf("sk-one", "sk-two"), keysFor(model, provider(listOf(model))))
    }

    @Test
    fun `绑定单把 Key 的模型只用那把它`() {
        val model = Model(modelId = "gpt-4o", displayName = "GPT-4o", apiKeys = listOf("sk-two"))
        assertEquals(listOf("sk-two"), keysFor(model, provider(listOf(model))))
    }

    @Test
    fun `同名模型按分组各走各的 Key`() {
        val first = Model(modelId = "deepseek-chat", displayName = "DeepSeek Chat", apiKeys = listOf("sk-one"))
        val second = Model(modelId = "deepseek-chat", displayName = "DeepSeek Chat", apiKeys = listOf("sk-two"))
        val provider = provider(listOf(first, second))

        assertEquals(listOf("sk-one"), keysFor(first, provider))
        assertEquals(listOf("sk-two"), keysFor(second, provider))
    }

    @Test
    fun `绑定的 Key 被删掉时退回全部可用 Key`() {
        val model = Model(modelId = "gpt-4o", displayName = "GPT-4o", apiKeys = listOf("sk-gone"))
        assertEquals(listOf("sk-one", "sk-two"), keysFor(model, provider(listOf(model))))
    }

    @Test
    fun `绑定的 Key 被停用时退回其余可用 Key`() {
        val model = Model(modelId = "gpt-4o", displayName = "GPT-4o", apiKeys = listOf("sk-one"))
        val provider = provider(
            models = listOf(model),
            disabledApiKeys = listOf("sk-one"),
        )
        assertEquals(listOf("sk-two"), keysFor(model, provider))
    }

    @Test
    fun `模型绑定多把 Key 时在这几把之间轮询`() {
        val model = Model(
            modelId = "gpt-4o",
            displayName = "GPT-4o",
            apiKeys = listOf("sk-one", "sk-two"),
        )
        val provider = provider(models = listOf(model), apiKey = "sk-one\nsk-two\nsk-three")
        assertEquals(listOf("sk-one", "sk-two"), keysFor(model, provider))
    }

    @Test
    fun `覆盖配置优先于 Key 分组`() {
        val model = Model(
            modelId = "gpt-4o",
            displayName = "GPT-4o",
            apiKeys = listOf("sk-two"),
            providerOverwrite = ProviderSetting.OpenAI(
                apiKey = "sk-override",
                baseUrl = "https://api.example.com/v1",
            ),
        )
        val routed = model.findProvider(listOf(provider(listOf(model))))
        assertEquals(listOf("sk-override"), (routed as ProviderSetting.ApiKey).availableKeys)
    }
}

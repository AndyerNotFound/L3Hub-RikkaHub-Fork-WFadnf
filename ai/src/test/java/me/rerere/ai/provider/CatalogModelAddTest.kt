package me.rerere.ai.provider

import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 从远端目录添加模型：同一分组重复勾选不新增，别的分组已有同一个模型时沿用它的价目。
 */
class CatalogModelAddTest {

    private val provider = ProviderSetting.DeepSeekAccount()

    @Test
    fun `同一分组重复添加只保留一条`() {
        val model = Model(modelId = "deepseek-chat", displayName = "DeepSeek Chat")
        val once = provider.addCatalogModel(model)
        val twice = once.addCatalogModel(model.copy())
        assertEquals(1, twice.models.size)
    }

    @Test
    fun `别的分组添加同一个模型时沿用已有价目`() {
        val priced = Model(
            modelId = "deepseek-chat",
            displayName = "DeepSeek Chat",
            pricing = ModelPricing(input = 2.0, output = 8.0),
        )
        val base = provider.addCatalogModel(priced)
        val other = base.addCatalogModel(
            Model(
                modelId = "deepseek-chat",
                displayName = "DeepSeek Chat",
                accountIds = listOf(Uuid.random()),
            )
        )
        // 另一个账号分组里是独立的一条，但价目不能因为重加一次就归零
        assertEquals(2, other.models.size)
        assertEquals(2.0, other.models[1].pricing.input, 0.0)
        assertEquals(8.0, other.models[1].pricing.output, 0.0)
    }

    @Test
    fun `模型自带价目时用它自己的`() {
        val base = provider.addCatalogModel(
            Model(
                modelId = "deepseek-chat",
                pricing = ModelPricing(input = 1.0),
            )
        )
        val other = base.addCatalogModel(
            Model(
                modelId = "deepseek-chat",
                accountIds = listOf(Uuid.random()),
                pricing = ModelPricing(input = 5.0),
            )
        )
        assertEquals(5.0, other.models[1].pricing.input, 0.0)
    }
}

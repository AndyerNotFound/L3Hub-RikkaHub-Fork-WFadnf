package me.rerere.rikkahub.ui.components.ai

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.availableKeys
import me.rerere.ai.provider.maskedKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型页的分组归属：一页一把启用的 Key，最后一页收没有绑定 Key 的模型。
 */
class ModelKeyGroupTest {

    private fun provider(
        apiKey: String = "sk-one\nsk-two",
        disabledApiKeys: List<String> = emptyList(),
        apiKeyLabels: Map<String, String> = emptyMap(),
    ) = ProviderSetting.OpenAI(
        apiKey = apiKey,
        disabledApiKeys = disabledApiKeys,
        apiKeyLabels = apiKeyLabels,
        baseUrl = "https://api.example.com/v1",
    )

    @Test
    fun `分组名优先取 Key 的备注，没有备注用掩码`() {
        val provider = provider(apiKeyLabels = mapOf("sk-one" to "主账号"))
        assertEquals(
            listOf("主账号", maskedKey("sk-two")),
            provider.keyGroups().map { it.name },
        )
    }

    @Test
    fun `只有一把 Key 时不分组`() {
        assertTrue(provider(apiKey = "sk-only").keyGroups().isEmpty())
        assertTrue(provider(apiKey = "").keyGroups().isEmpty())
    }

    @Test
    fun `停用的 Key 不单独分组`() {
        // 三把 Key、停用中间一把：剩下两把才有分组条，停用的那把不出现
        val provider = provider(
            apiKey = "sk-one\nsk-two\nsk-three",
            disabledApiKeys = listOf("sk-two"),
        )
        assertEquals(listOf("sk-one", "sk-three"), provider.keyGroups().map { it.key })
    }

    @Test
    fun `绑定有效的模型归自己那一页，其余归全部 Key 页`() {
        val keys = provider().availableKeys
        val bound = Model(modelId = "m", displayName = "m", apiKeys = listOf("sk-one"))
        val shared = Model(modelId = "m2", displayName = "m2")
        val orphan = Model(modelId = "m3", displayName = "m3", apiKeys = listOf("sk-gone"))

        assertTrue(bound.belongsToKeyGroup("sk-one", keys))
        assertFalse(bound.belongsToKeyGroup("sk-two", keys))
        assertFalse(bound.belongsToKeyGroup(null, keys))

        assertTrue(shared.belongsToKeyGroup(null, keys))
        assertTrue(orphan.belongsToKeyGroup(null, keys))
    }

    @Test
    fun `卡片标签只在多把 Key 时出现`() {
        val model = Model(modelId = "m", displayName = "m")
        assertNull(model.keyGroupLabel(provider(apiKey = "sk-only"), "全部 Key", "Key 不可用"))

        assertEquals(
            "主账号",
            model.copy(apiKeys = listOf("sk-one")).keyGroupLabel(
                provider(apiKeyLabels = mapOf("sk-one" to "主账号")),
                "全部 Key",
                "Key 不可用",
            ),
        )
        assertEquals("全部 Key", model.keyGroupLabel(provider(), "全部 Key", "Key 不可用"))
        assertEquals(
            "Key 不可用",
            model.copy(apiKeys = listOf("sk-gone")).keyGroupLabel(provider(), "全部 Key", "Key 不可用"),
        )
    }

    @Test
    fun `覆写了提供商的模型不显示分组标签`() {
        val model = Model(
            modelId = "m",
            displayName = "m",
            apiKeys = listOf("sk-one"),
            providerOverwrite = ProviderSetting.OpenAI(baseUrl = "https://api.example.com/v1"),
        )
        assertNull(model.keyGroupLabel(provider(), "全部 Key", "Key 不可用"))
    }
}

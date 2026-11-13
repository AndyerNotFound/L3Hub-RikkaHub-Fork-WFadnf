package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsMergeTest {
    @Test
    fun `merge keeps current scalar values and adds missing items`() {
        val existing = Assistant(id = Uuid.random(), name = "existing")
        val missing = Assistant(id = Uuid.random(), name = "missing")
        val existingModel = Model(modelId = "current", displayName = "current")
        val missingModel = Model(modelId = "missing", displayName = "missing")
        val providerId = Uuid.random()
        val currentProvider = ProviderSetting.OpenAI(
            id = providerId,
            name = "current provider",
            models = listOf(existingModel),
            apiKey = "current key",
        )
        val backupProvider = ProviderSetting.OpenAI(
            id = providerId,
            name = "backup provider",
            models = listOf(existingModel.copy(displayName = "backup"), missingModel),
            apiKey = "backup key",
        )
        val newProvider = ProviderSetting.OpenAI(name = "new provider")

        val current = Settings(
            developerMode = true,
            assistants = listOf(existing),
            providers = listOf(currentProvider),
        )
        val backup = Settings(
            developerMode = false,
            assistants = listOf(existing.copy(name = "backup"), missing),
            providers = listOf(backupProvider, newProvider),
        )

        val merged = current.mergedWith(backup)

        // 单值设置与同 id 的条目都以当前为准
        assertEquals(true, merged.developerMode)
        assertEquals(listOf("existing", "missing"), merged.assistants.map { it.name })
        assertEquals(listOf("current provider", "new provider"), merged.providers.map { it.name })
        val provider = merged.providers.first()
        assertEquals("current", provider.models.first().displayName)
        assertEquals(listOf("current", "missing"), provider.models.map { it.modelId })
    }

    @Test
    fun `merge keeps current provider credentials`() {
        val provider = ProviderSetting.OpenAI(name = "OpenAI", apiKey = "current key")
        val backup = Settings(
            providers = listOf(provider.copy(apiKey = "backup key")),
        )

        val merged = Settings(providers = listOf(provider), developerMode = true).mergedWith(backup)

        assertEquals("current key", (merged.providers.single() as ProviderSetting.OpenAI).apiKey)
    }
}

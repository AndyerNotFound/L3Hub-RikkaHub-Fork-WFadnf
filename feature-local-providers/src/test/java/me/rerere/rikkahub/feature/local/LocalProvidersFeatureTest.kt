package me.rerere.rikkahub.feature.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.extension.ExtensionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalProvidersFeatureTest {
    @Test
    fun `registers both existing local provider types`() {
        val registry = ExtensionRegistry()
        var factoryCalls = 0
        val feature = LocalProvidersFeature {
            factoryCalls += 1
            FakeLocalProvider()
        }

        registry.register(feature)
        registry.freeze()

        assertEquals(2, factoryCalls)
        assertEquals("local-providers", feature.descriptor.id)
        assertEquals(setOf("gguf", "onnx"), registry.providerSnapshot().keys)
        assertTrue(registry.providerSnapshot().values.all { it is FakeLocalProvider })
    }

    private class FakeLocalProvider : Provider<ProviderSetting.LocalServer> {
        override suspend fun listModels(providerSetting: ProviderSetting.LocalServer): List<Model> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.LocalServer,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): TextGenerationResult = error("Not used by feature tests")

        override suspend fun streamText(
            providerSetting: ProviderSetting.LocalServer,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): Flow<StreamChunk> = emptyFlow()
    }
}

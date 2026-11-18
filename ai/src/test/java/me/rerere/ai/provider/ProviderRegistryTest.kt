package me.rerere.ai.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderRegistryTest {
    @Test
    fun `returns the exact registered provider`() {
        val registry = ProviderRegistry()
        val provider = FakeProvider()

        registry.register("local", provider)

        assertSame(provider, registry.get("local"))
    }

    @Test
    fun `rejects blank names`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ProviderRegistry().register(" ", FakeProvider())
        }

        assertEquals("Provider name must not be blank", error.message)
    }

    @Test
    fun `rejects duplicates instead of silently replacing a provider`() {
        val registry = ProviderRegistry()
        registry.register("local", FakeProvider())

        val error = assertThrows(IllegalArgumentException::class.java) {
            registry.register("local", FakeProvider())
        }

        assertEquals("Provider is already registered: local", error.message)
    }

    @Test
    fun `unknown providers keep the existing error contract`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ProviderRegistry().get("missing")
        }

        assertEquals("Provider not found: missing", error.message)
    }

    @Test
    fun `preassembled local contributions resolve through existing settings`() {
        val gguf = FakeLocalProvider()
        val onnx = FakeLocalProvider()
        val manager = ProviderManager(
            mapOf(
                "gguf" to gguf,
                "onnx" to onnx,
            ),
        )

        assertSame(gguf, manager.getProviderByType(ProviderSetting.GGUF()))
        assertSame(onnx, manager.getProviderByType(ProviderSetting.ONNX()))
    }

    private class FakeLocalProvider : Provider<ProviderSetting.LocalServer> {
        override suspend fun listModels(providerSetting: ProviderSetting.LocalServer): List<Model> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.LocalServer,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): TextGenerationResult = error("Not used by registry tests")

        override suspend fun streamText(
            providerSetting: ProviderSetting.LocalServer,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): Flow<StreamChunk> = emptyFlow()
    }

    private class FakeProvider : Provider<ProviderSetting.OpenAI> {
        override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): TextGenerationResult = error("Not used by registry tests")

        override suspend fun streamText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): Flow<StreamChunk> = emptyFlow()
    }
}

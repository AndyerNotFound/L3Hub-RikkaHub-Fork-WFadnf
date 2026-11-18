package me.rerere.rikkahub.extension

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionRegistryTest {
    @Test
    fun `feature contributes an ordered provider snapshot`() {
        val gguf = FakeProvider()
        val onnx = FakeProvider()
        val registry = ExtensionRegistry()

        registry.register(
            feature("local-providers") {
                registerProvider("gguf", gguf)
                registerProvider("onnx", onnx)
            },
        )
        registry.freeze()

        assertEquals(listOf("local-providers"), registry.featureSnapshot().map { it.id })
        assertEquals(listOf("gguf", "onnx"), registry.providerSnapshot().keys.toList())
        assertSame(gguf, registry.providerSnapshot().getValue("gguf"))
        assertSame(onnx, registry.providerSnapshot().getValue("onnx"))
    }

    @Test
    fun `duplicate feature ids are rejected`() {
        val registry = ExtensionRegistry()
        registry.register(feature("duplicate") { })

        assertThrows(IllegalArgumentException::class.java) {
            registry.register(feature("duplicate") { })
        }
    }

    @Test
    fun `duplicate provider types identify the original owner`() {
        val registry = ExtensionRegistry()
        registry.register(feature("first") { registerProvider("local", FakeProvider()) })

        val error = assertThrows(IllegalArgumentException::class.java) {
            registry.register(feature("second") { registerProvider("local", FakeProvider()) })
        }

        assertTrue(error.message.orEmpty().contains("already registered by feature 'first'"))
    }

    @Test
    fun `failed feature registration rolls back its providers`() {
        val registry = ExtensionRegistry()
        val replacement = FakeProvider()

        assertThrows(IllegalStateException::class.java) {
            registry.register(
                feature("broken") {
                    registerProvider("local", FakeProvider())
                    error("Registration failed")
                },
            )
        }
        registry.register(feature("working") { registerProvider("local", replacement) })
        registry.freeze()

        assertEquals(listOf("working"), registry.featureSnapshot().map { it.id })
        assertSame(replacement, registry.providerSnapshot().getValue("local"))
    }

    @Test
    fun `contributions cannot be consumed or changed around the freeze boundary`() {
        val registry = ExtensionRegistry()
        registry.register(feature("local") { registerProvider("local", FakeProvider()) })

        assertThrows(IllegalStateException::class.java) { registry.providerSnapshot() }

        registry.freeze()

        assertThrows(IllegalStateException::class.java) {
            registry.register(feature("late") { })
        }
    }

    @Test
    fun `providers cannot bypass feature registration`() {
        val error = assertThrows(IllegalStateException::class.java) {
            ExtensionRegistry().registerProvider("local", FakeProvider())
        }

        assertEquals("Providers must be registered from L3Feature.register", error.message)
    }

    @Test
    fun `host provider collisions identify the contributing feature`() {
        val registry = ExtensionRegistry()
        registry.register(feature("custom-openai") { registerProvider("openai", FakeProvider()) })

        val error = assertThrows(IllegalArgumentException::class.java) {
            registry.freeze(reservedProviderTypes = ProviderManager.BUILT_IN_PROVIDER_TYPES)
        }

        assertTrue(error.message.orEmpty().contains("contributed by feature 'custom-openai'"))
        assertTrue(error.message.orEmpty().contains("conflicts with a host built-in provider"))
    }

    private fun feature(id: String, contributions: ExtensionRegistry.() -> Unit): L3Feature =
        object : L3Feature {
            override val descriptor = FeatureDescriptor(id)

            override fun register(registry: ExtensionRegistry) {
                registry.contributions()
            }
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

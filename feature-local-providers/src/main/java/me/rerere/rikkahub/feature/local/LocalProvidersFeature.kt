package me.rerere.rikkahub.feature.local

import android.content.Context
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.providers.local.LocalServerProvider
import me.rerere.ai.provider.providers.openai.OpenAIProvider
import me.rerere.rikkahub.extension.ExtensionRegistry
import me.rerere.rikkahub.extension.FeatureDescriptor
import me.rerere.rikkahub.extension.L3Feature
import okhttp3.OkHttpClient

/** Provides the existing local OpenAI-compatible backends through the extension API. */
class LocalProvidersFeature internal constructor(
    private val providerFactory: () -> Provider<ProviderSetting.LocalServer>,
) : L3Feature {
    constructor(client: OkHttpClient, context: Context) : this(
        providerFactory = { LocalServerProvider(OpenAIProvider(client, context)) },
    )

    override val descriptor = FeatureDescriptor(id = "local-providers")

    override fun register(registry: ExtensionRegistry) {
        registry.registerProvider(GGUF, providerFactory())
        registry.registerProvider(ONNX, providerFactory())
    }

    companion object {
        const val GGUF = "gguf"
        const val ONNX = "onnx"
    }
}

package me.rerere.ai.provider

import android.content.Context
import me.rerere.ai.provider.providers.chatgpt.ChatGPTProvider
import me.rerere.ai.provider.providers.claude.ClaudeProvider
import me.rerere.ai.provider.providers.google.GoogleProvider
import me.rerere.ai.provider.providers.grok.GrokProvider
import me.rerere.ai.provider.providers.openai.OpenAIProvider
import me.rerere.ai.util.ImageEncodeCache
import okhttp3.OkHttpClient

/** Resolves built-in and AAR-contributed providers. */
class ProviderManager private constructor(
    private val providers: ProviderRegistry,
) {
    /** Creates a manager from preassembled contributions for JVM tests. */
    internal constructor(providerContributions: Map<String, Provider<*>>) : this(ProviderRegistry()) {
        providerContributions.forEach { (name, provider) ->
            registerProvider(name, provider)
        }
    }

    constructor(
        client: OkHttpClient,
        context: Context,
        accountCredentials: AccountCredentialProvider,
        extensionProviders: Map<String, Provider<*>> = emptyMap(),
    ) : this(ProviderRegistry()) {
        ImageEncodeCache.init(context.cacheDir)

        registerProvider(OPENAI, KeyFailoverProvider(OpenAIProvider(client, context)))
        registerProvider(GOOGLE, KeyFailoverProvider(GoogleProvider(client, context)))
        registerProvider(CLAUDE, KeyFailoverProvider(ClaudeProvider(client, context)))
        registerProvider(CHATGPT, ChatGPTProvider(client, accountCredentials))
        registerProvider(GROK, GrokProvider(client, accountCredentials))

        extensionProviders.forEach { (name, provider) ->
            registerProvider(name, provider)
        }
    }

    /** Registers a provider without silently replacing an existing registration. */
    fun registerProvider(name: String, provider: Provider<*>) {
        providers.register(name, provider)
    }

    /** Returns a provider or throws [IllegalArgumentException] when its name is unknown. */
    fun getProvider(name: String): Provider<*> = providers.get(name)

    /** Resolves the provider type used by the existing persisted settings. */
    fun <T : ProviderSetting> getProviderByType(setting: T): Provider<T> {
        @Suppress("UNCHECKED_CAST")
        return when (setting) {
            is ProviderSetting.OpenAI -> getProvider(OPENAI)
            is ProviderSetting.Google -> getProvider(GOOGLE)
            is ProviderSetting.Claude -> getProvider(CLAUDE)
            is ProviderSetting.ChatGPT -> getProvider(CHATGPT)
            is ProviderSetting.Grok -> getProvider(GROK)
        } as Provider<T>
    }

    companion object {
        private const val OPENAI = "openai"
        private const val GOOGLE = "google"
        private const val CLAUDE = "claude"
        private const val CHATGPT = "chatgpt"
        private const val GROK = "grok"

        val BUILT_IN_PROVIDER_TYPES: Set<String> = setOf(
            OPENAI,
            GOOGLE,
            CLAUDE,
            CHATGPT,
            GROK,
        )
    }
}

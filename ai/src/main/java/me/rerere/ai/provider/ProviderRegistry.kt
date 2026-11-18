package me.rerere.ai.provider

/** Process-local provider registry with deterministic duplicate handling. */
internal class ProviderRegistry {
    private val providers = linkedMapOf<String, Provider<*>>()

    fun register(name: String, provider: Provider<*>) {
        require(name.isNotBlank()) { "Provider name must not be blank" }
        require(name !in providers) { "Provider is already registered: $name" }
        providers[name] = provider
    }

    fun get(name: String): Provider<*> =
        providers[name] ?: throw IllegalArgumentException("Provider not found: $name")
}

package me.rerere.rikkahub.extension

import me.rerere.ai.provider.Provider

/**
 * Collects feature contributions during startup and freezes them before use.
 * Registration is deliberately single-threaded and is not a runtime plugin API.
 */
class ExtensionRegistry {
    private val features = linkedMapOf<String, FeatureDescriptor>()
    private val providers = linkedMapOf<String, Provider<*>>()
    private val providerOwners = mutableMapOf<String, String>()
    private var registeringFeature: FeatureDescriptor? = null
    private var frozen = false

    fun register(feature: L3Feature) {
        checkNotFrozen()
        check(registeringFeature == null) { "Nested feature registration is not supported" }

        val descriptor = feature.descriptor
        require(descriptor.id !in features) { "Feature '${descriptor.id}' is already registered" }

        registeringFeature = descriptor
        try {
            feature.register(this)
            features[descriptor.id] = descriptor
        } catch (error: Throwable) {
            val contributedTypes = providerOwners.filterValues { it == descriptor.id }.keys
            contributedTypes.forEach { type ->
                providers.remove(type)
                providerOwners.remove(type)
            }
            throw error
        } finally {
            registeringFeature = null
        }
    }

    fun registerProvider(type: String, provider: Provider<*>) {
        checkNotFrozen()
        val owner = checkNotNull(registeringFeature) {
            "Providers must be registered from L3Feature.register"
        }
        require(type.isNotBlank()) { "Provider type must not be blank" }
        require(type == type.trim()) { "Provider type must not have surrounding whitespace" }

        val previousOwner = providerOwners[type]
        require(previousOwner == null) {
            "Provider type '$type' is already registered by feature '$previousOwner'"
        }
        providers[type] = provider
        providerOwners[type] = owner.id
    }

    fun freeze(reservedProviderTypes: Set<String> = emptySet()): ExtensionRegistry = apply {
        check(registeringFeature == null) { "Cannot freeze during feature registration" }
        val conflictingType = providerOwners.keys.firstOrNull { it in reservedProviderTypes }
        require(conflictingType == null) {
            val owner = providerOwners.getValue(conflictingType!!)
            "Provider type '$conflictingType' contributed by feature '$owner' conflicts with a host built-in provider"
        }
        frozen = true
    }

    fun providerSnapshot(): Map<String, Provider<*>> {
        check(frozen) { "Freeze the extension registry before consuming contributions" }
        return LinkedHashMap(providers)
    }

    fun featureSnapshot(): List<FeatureDescriptor> {
        check(frozen) { "Freeze the extension registry before consuming contributions" }
        return features.values.toList()
    }

    private fun checkNotFrozen() {
        check(!frozen) { "The extension registry is frozen" }
    }
}

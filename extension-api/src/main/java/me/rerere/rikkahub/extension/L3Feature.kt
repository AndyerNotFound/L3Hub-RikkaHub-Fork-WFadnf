package me.rerere.rikkahub.extension

/** Stable identity of a feature packaged into the host. */
data class FeatureDescriptor(
    val id: String,
) {
    init {
        require(id.isNotBlank()) { "Feature id must not be blank" }
        require(id == id.trim()) { "Feature id must not have surrounding whitespace" }
    }
}

/** A build-time feature contribution. AAR features execute inside the host process. */
interface L3Feature {
    val descriptor: FeatureDescriptor

    fun register(registry: ExtensionRegistry)
}

package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.datastore.finiteAppearanceValue

@Serializable
data class InspirationAppearance(
    val followRichContent: Boolean = true,
    val surfaceOpacity: Float = .62f,
    val borderOpacity: Float = .34f,
    val cornerRadius: Float = 12f,
) {
    fun normalized() = copy(
        surfaceOpacity = finiteAppearanceValue(surfaceOpacity, .2f, 1f, .62f),
        borderOpacity = finiteAppearanceValue(borderOpacity, 0f, 1f, .34f),
        cornerRadius = finiteAppearanceValue(cornerRadius, 0f, 28f, 12f),
    )
}

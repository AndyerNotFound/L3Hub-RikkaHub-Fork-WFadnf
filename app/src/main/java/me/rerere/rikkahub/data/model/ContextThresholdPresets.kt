package me.rerere.rikkahub.data.model

import me.rerere.ai.provider.Model

/**
 * 上下文预警门限的预设。
 *
 * 现在所有模型统一用一组值：WARN 384K、MAX 512K，不再按型号分档。
 * 只在模型还没有手动设置过门限时生效（见 [withContextPresets]）。
 */
object ContextThresholdPresets {
    data class Preset(val warn: Int, val max: Int)

    val default = Preset(warn = 393_216, max = 524_288)

    val fallback = default

    @Suppress("UNUSED_PARAMETER")
    fun resolve(modelId: String): Preset = default
}

/**
 * 自动模式下（[Model.contextThresholdsAuto]）给还没设置过门限的模型填预设。
 * 只要用户填过其中一个，就完全尊重用户的值。
 */
fun Model.withContextPresets(): Model {
    if (!contextThresholdsAuto) return this
    if (warnContextTokens != null || maxContextTokens != null) return this
    val preset = ContextThresholdPresets.resolve(modelId)
    return copy(warnContextTokens = preset.warn, maxContextTokens = preset.max)
}

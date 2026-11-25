package me.rerere.rikkahub.data.model

import me.rerere.ai.provider.Model
import me.rerere.ai.registry.ModelRegistry

private const val DEFAULT_CONTEXT_MAX = 524_288
private const val MAX_WINDOW_PERCENT = 90
private val DEFAULT_CONTEXT_LENGTH = Model().contextLength

/** 自动预填只作用于未设 MAX 的模型，不重算已保存的值。 */
internal fun Model.withContextMaxPreset(): Model =
    if (contextThresholdsAuto && maxContextTokens == null) applyContextMaxPreset() else this

/** 新预设给小窗口留出空间；用户点击「使用预设」时也走这套计算。 */
internal fun Model.applyContextMaxPreset(): Model {
    val resolved = withResolvedContextLength()
    val window = resolved.contextLength
    val max = if (window > 0) {
        val windowMax = (window.toLong() * MAX_WINDOW_PERCENT / 100).toInt()
        minOf(DEFAULT_CONTEXT_MAX, windowMax).coerceAtLeast(1)
    } else {
        DEFAULT_CONTEXT_MAX
    }
    return resolved.copy(maxContextTokens = max, contextThresholdsAuto = true)
}

/** 模型还没有可靠窗口时，优先使用模型目录的窗口。 */
private fun Model.withResolvedContextLength(): Model {
    if (contextLength > 0 && contextLength != DEFAULT_CONTEXT_LENGTH) return this
    val resolved = ModelRegistry.MODEL_CONTEXT_LENGTH.getData(modelId) ?: return this
    if (resolved == contextLength) return this
    return copy(contextLength = resolved)
}

package me.rerere.rikkahub.data.model

import me.rerere.ai.provider.Model

/**
 * 按模型名预填上下文预警门限。
 *
 * WARN 是回复质量开始下滑的点，MAX 是性能骤降点。型号命中不了的用 [fallback]。
 * 只在模型还没有手动设置过门限时生效（见 [withContextPresets]）。
 */
object ContextThresholdPresets {
    data class Preset(val warn: Int, val max: Int)

    val fallback = Preset(warn = 131_072, max = 196_608)

    private val claudeVersion = Regex("""claude[^0-9]*(\d+)""")
    private val gptVersion = Regex("""\bgpt[-_ ]?(\d+)""")

    // 后面的 b 是参数量（qwen-32b），不是版本号
    private val qwenVersion = Regex("""\bqwen[-_ ]?(\d+)(?![0-9b])""")
    private val glmVersion = Regex("""\bglm[-_ ]?(\d+)""")
    private val kimiVersion = Regex("""\bkimi[^0-9]*k(\d+)""")
    private val stepVersion = Regex("""\bstep(?:fun)?[-_ ]?(\d+)""")
    private val deepSeekVersion = Regex("""\bdeepseek[^0-9]*v(\d+(?:\.\d+)?)""")

    fun resolve(modelId: String): Preset {
        val id = modelId.lowercase()

        if (id.contains("gpt-oss") || id.contains("gptoss")) return Preset(65_536, 131_072)
        if (id.contains("nemotron")) return Preset(131_072, 196_608)
        if (id.contains("muse")) return Preset(147_456, 221_184)

        kimiVersion.find(id)?.groupValues?.get(1)?.toIntOrNull()?.let { version ->
            if (version >= 3) return Preset(262_144, 327_680)
        }
        deepSeekVersion.find(id)?.groupValues?.get(1)?.toDoubleOrNull()?.let { version ->
            return when {
                version >= 4.1 -> Preset(172_032, 221_184)
                version >= 4.0 -> Preset(196_608, 262_144)
                version >= 3.0 -> Preset(131_072, 131_072)
                else -> fallback
            }
        }
        glmVersion.find(id)?.groupValues?.get(1)?.toIntOrNull()?.let { version ->
            if (version >= 5) return Preset(147_456, 221_184)
        }
        qwenVersion.find(id)?.groupValues?.get(1)?.toIntOrNull()?.let { version ->
            if (version >= 3) return Preset(196_608, 262_144)
        }
        gptVersion.find(id)?.groupValues?.get(1)?.toIntOrNull()?.let { version ->
            if (version >= 5) return Preset(196_608, 262_144)
        }
        claudeVersion.find(id)?.groupValues?.get(1)?.toIntOrNull()?.let { version ->
            return when {
                version >= 5 -> Preset(221_184, 327_680)
                version >= 4 -> Preset(147_456, 221_184)
                else -> fallback
            }
        }
        stepVersion.find(id)?.groupValues?.get(1)?.toIntOrNull()?.let { version ->
            return when {
                version >= 5 -> Preset(196_608, 327_680)
                version >= 3 -> Preset(147_456, 221_184)
                else -> fallback
            }
        }
        return fallback
    }
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

package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.provider.Model
import kotlin.math.roundToInt

/** 自动压缩的预警线：上下文占用到达模型配置的 WARN 或 MAX 门限 */
@Serializable
enum class AutoCompressThreshold {
    WARN,
    MAX,
}

/** 自动压缩触发比例的可选范围，设置页的滑块也用它 */
val AUTO_COMPRESS_THRESHOLD_RANGE = 0.5f..0.95f

/**
 * 预警线的实际取值。
 *
 * 用户填过的门限优先：WARN/MAX 缺一个就退回另一个，和以前一致，手填的值一律不动。
 * 两个都没填时按模型窗口兜底（WARN 75%、MAX 90%）。
 *
 * 自动填充的门限（[Model.contextThresholdsAuto]）还会按窗口收窄：它可能是按 1M 窗口写下的旧值，
 * 超过真实窗口的门限等于没有门限。收窄比例和预设一致（WARN 75%、MAX 90%）。
 * 手填的值不收窄——那是用户自己的选择。
 */
fun Model.autoCompressLimit(threshold: AutoCompressThreshold): Int? {
    val warn = warnContextTokens?.takeIf { it > 0 }
    val max = maxContextTokens?.takeIf { it > 0 }
    val window = contextLength.takeIf { it > 0 }
    val percent = when (threshold) {
        AutoCompressThreshold.WARN -> WARN_WINDOW_PERCENT
        AutoCompressThreshold.MAX -> MAX_WINDOW_PERCENT
    }
    val ceiling = window?.let { (it * percent / 100).coerceAtLeast(1) }
    val configured = when (threshold) {
        AutoCompressThreshold.WARN -> warn ?: max
        AutoCompressThreshold.MAX -> max ?: warn
    }
    if (configured != null) {
        return if (contextThresholdsAuto && ceiling != null) minOf(configured, ceiling) else configured
    }
    return ceiling
}

/**
 * 自动压缩的触发点：助手设的触发比例与选中预警线取先到的一个。
 *
 * 比例管「离窗口还有多少」，预警线管「模型性能开始下滑的点」，两者都可能是用户更在意的那个，
 * 所以谁先到就按谁压。都没有（没窗口也没门限）时返回 null，调用方按不压缩处理。
 */
fun Model.autoCompressTriggerTokens(assistant: Assistant): Int? = listOfNotNull(
    contextLength.takeIf { it > 0 }?.let { (it * assistant.autoCompressRatio).roundToInt() },
    autoCompressLimit(assistant.autoCompressThreshold),
).minOrNull()

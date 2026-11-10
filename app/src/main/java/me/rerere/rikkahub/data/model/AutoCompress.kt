package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.provider.Model

/** 自动压缩的触发点：上下文占用到达模型配置的 WARN 或 MAX 门限 */
@Serializable
enum class AutoCompressThreshold {
    WARN,
    MAX,
}

/**
 * 压缩后保留多少最近上下文。
 *
 * 现在只按消息条数保留。按 token 数保留的算法已经移除：那套算法要在消息内部找 part 边界，
 * 切出来的历史是残缺的一轮。保留 [TOKENS] 只是为了旧设置文件还能解码，运行时按默认条数处理。
 */
@Serializable
enum class AutoCompressTarget {
    MESSAGES,
    TOKENS,
}

/** 自动压缩默认保留的消息条数 */
internal const val AUTO_COMPRESS_DEFAULT_KEEP_MESSAGES = 8

/** 自动压缩保留的消息条数；旧版本按 token 数配置的助手退回默认条数 */
val Assistant.autoCompressKeepMessages: Int
    get() = if (autoCompressTarget == AutoCompressTarget.MESSAGES) {
        autoCompressTargetValue.coerceAtLeast(1)
    } else {
        AUTO_COMPRESS_DEFAULT_KEEP_MESSAGES
    }

/**
 * 自动压缩实际使用的门限。
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

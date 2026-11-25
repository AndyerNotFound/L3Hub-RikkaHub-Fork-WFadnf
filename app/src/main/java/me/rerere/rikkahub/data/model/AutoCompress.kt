package me.rerere.rikkahub.data.model

import me.rerere.ai.provider.Model
import kotlin.math.roundToInt

/** 自动压缩触发比例的可选范围，设置页的滑块也用它 */
val AUTO_COMPRESS_RATIO_RANGE = 0.5f..0.95f

/** 自动压缩的触发点按模型 MAX 计算；没有有效 MAX 时不触发。 */
fun Model.autoCompressTriggerTokens(assistant: Assistant): Int? =
    maxContextTokens
        ?.takeIf { it > 0 }
        ?.let { (it * assistant.autoCompressRatio).roundToInt() }

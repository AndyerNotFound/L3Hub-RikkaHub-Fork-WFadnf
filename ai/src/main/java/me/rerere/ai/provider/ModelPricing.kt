package me.rerere.ai.provider

import kotlinx.serialization.Serializable
import me.rerere.ai.core.TokenUsage

/**
 * 模型的计费单价。
 *
 * 每项都是「每 [priceUnitTokens] 个 token 的价格」：十进制口径填每 1M token 的价格，
 * 二进制口径填每 Mi token 的价格。0 表示未单独定价，计算时会回落到输入价。
 */
@Serializable
data class ModelPricing(
    // 未命中缓存的输入 token
    val input: Double = 0.0,
    val output: Double = 0.0,
    // 缓存命中与缓存读取是同一批 token 的两种叫法，读取价优先
    val cacheHit: Double = 0.0,
    val cacheWrite: Double = 0.0,
    val cacheRead: Double = 0.0,
    // 每次请求的固定费用，多步工具调用按步数计
    val perCall: Double = 0.0,
) {
    val isConfigured: Boolean
        get() = input > 0 || output > 0 || cacheHit > 0 || cacheWrite > 0 ||
            cacheRead > 0 || perCall > 0
}

/** 一次（或累计）用量的费用明细，单位和模型单价一致 */
data class UsageCost(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cacheHit: Double = 0.0,
    val cacheWrite: Double = 0.0,
    val cacheRead: Double = 0.0,
    val perCall: Double = 0.0,
) {
    val total: Double
        get() = input + output + cacheHit + cacheWrite + cacheRead + perCall

    operator fun plus(other: UsageCost) = UsageCost(
        input = input + other.input,
        output = output + other.output,
        cacheHit = cacheHit + other.cacheHit,
        cacheWrite = cacheWrite + other.cacheWrite,
        cacheRead = cacheRead + other.cacheRead,
        perCall = perCall + other.perCall,
    )
}

/** 十进制的 1M 与二进制口径的 1Mi 各含多少 token */
const val TOKENS_PER_MILLION = 1_000_000.0
const val TOKENS_PER_MEBI = 1_048_576.0

fun priceUnitTokens(binaryUnits: Boolean): Double =
    if (binaryUnits) TOKENS_PER_MEBI else TOKENS_PER_MILLION

/**
 * 按每 [unitTokens] 个 token 的单价估算用量费用。
 *
 * [usage] 是累计用量，[calls] 是这批用量对应的请求次数，计次费用按它相乘。
 *
 * 缓存读取与缓存命中指向同一批缓存 token：填了读取价就按读取计，否则按命中价，
 * 两者都没填时回落到输入价，避免缓存部分漏计。缓存写入同理回落到输入价。
 * 未缓存的输入要从 [TokenUsage.promptTokens] 里扣掉缓存读取和写入的部分。
 */
fun ModelPricing.estimateCost(
    usage: TokenUsage,
    unitTokens: Double = TOKENS_PER_MILLION,
    calls: Int = 1,
): UsageCost = estimateCost(
    promptTokens = usage.promptTokens.toLong(),
    completionTokens = usage.completionTokens.toLong(),
    cachedTokens = usage.cachedTokens.toLong(),
    cacheWriteTokens = usage.cacheWriteTokens.toLong(),
    unitTokens = unitTokens,
    calls = calls,
)

/** 同上，但 token 数用 [Long] 传入，汇总整段时间的用量时不会溢出 */
fun ModelPricing.estimateCost(
    promptTokens: Long,
    completionTokens: Long,
    cachedTokens: Long,
    cacheWriteTokens: Long = 0,
    unitTokens: Double = TOKENS_PER_MILLION,
    calls: Int = 1,
): UsageCost {
    if (!isConfigured || unitTokens <= 0) return UsageCost()
    val prompt = promptTokens.coerceAtLeast(0)
    // 各 provider 的缓存字段偶尔会超过 prompt 总数，按总量夹一下，避免多计
    val cached = cachedTokens.coerceIn(0, prompt)
    val written = cacheWriteTokens.coerceIn(0, prompt - cached)
    val uncached = prompt - cached - written
    val hitPrice = cacheRead.takeIf { it > 0 }
        ?: cacheHit.takeIf { it > 0 }
        ?: input
    val writePrice = cacheWrite.takeIf { it > 0 } ?: input
    return UsageCost(
        input = uncached / unitTokens * input,
        output = completionTokens.coerceAtLeast(0) / unitTokens * output,
        cacheHit = if (cacheRead > 0) 0.0 else cached / unitTokens * hitPrice,
        cacheWrite = written / unitTokens * writePrice,
        cacheRead = if (cacheRead > 0) cached / unitTokens * cacheRead else 0.0,
        perCall = perCall * calls.coerceAtLeast(1),
    )
}

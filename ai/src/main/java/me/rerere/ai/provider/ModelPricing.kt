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
    // 变价规则：按顺序匹配，第一条启用的规则命中后整张价目表替换为规则里的价格
    val rules: List<PriceRule> = emptyList(),
) {
    /** 基础价目里是否有任何非零价格，不含变价规则 */
    val hasPrice: Boolean
        get() = input > 0 || output > 0 || cacheHit > 0 || cacheWrite > 0 ||
            cacheRead > 0 || perCall > 0

    /** 基础价目或任一启用的变价规则配置了价格 */
    val isConfigured: Boolean
        get() = hasPrice || rules.any { it.enabled && it.price.isConfigured }

    fun priceTable(): PriceTable = PriceTable(
        input = input,
        output = output,
        cacheHit = cacheHit,
        cacheWrite = cacheWrite,
        cacheRead = cacheRead,
        perCall = perCall,
    )

    /**
     * 按顺序找出第一条命中的启用规则；命中后返回只带规则价格的价目，否则返回基础价目。
     * 规则价格本身不再携带规则，避免嵌套。
     */
    fun applyRules(context: PriceRuleContext): ModelPricing {
        val rule = rules.firstOrNull { it.enabled && it.matches(context) } ?: return this
        return ModelPricing(
            input = rule.price.input,
            output = rule.price.output,
            cacheHit = rule.price.cacheHit,
            cacheWrite = rule.price.cacheWrite,
            cacheRead = rule.price.cacheRead,
            perCall = rule.price.perCall,
        )
    }
}

/** 一次（或累计）用量的费用明细，单位和模型单价一致 */
@Serializable
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
 * [context] 不为空时先按它判断并套用变价规则；规则看的是 context 里的 token 数，
 * 计费仍用本次传入的 prompt/completion/cached token 数。
 *
 * 缓存读取与缓存命中指向同一批缓存 token：填了读取价就按读取计，否则按命中价，
 * 两者都没填时回落到输入价，避免缓存部分漏计。缓存写入同理回落到输入价。
 * 未缓存的输入要从 [TokenUsage.promptTokens] 里扣掉缓存读取和写入的部分。
 */
fun ModelPricing.estimateCost(
    usage: TokenUsage,
    unitTokens: Double = TOKENS_PER_MILLION,
    calls: Int = 1,
    context: PriceRuleContext? = null,
): UsageCost = estimateCost(
    promptTokens = usage.promptTokens.toLong(),
    completionTokens = usage.completionTokens.toLong(),
    cachedTokens = usage.cachedTokens.toLong(),
    cacheWriteTokens = usage.cacheWriteTokens.toLong(),
    unitTokens = unitTokens,
    calls = calls,
    context = context,
)

/** 同上，但 token 数用 [Long] 传入，汇总整段时间的用量时不会溢出 */
fun ModelPricing.estimateCost(
    promptTokens: Long,
    completionTokens: Long,
    cachedTokens: Long,
    cacheWriteTokens: Long = 0,
    unitTokens: Double = TOKENS_PER_MILLION,
    calls: Int = 1,
    context: PriceRuleContext? = null,
): UsageCost {
    // 调用方显式传入上下文时先按它套用变价规则
    val pricing = context?.let { applyRules(it) } ?: this
    if (!pricing.hasPrice || unitTokens <= 0) return UsageCost()
    return pricing.estimateCostInternal(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        cachedTokens = cachedTokens,
        cacheWriteTokens = cacheWriteTokens,
        unitTokens = unitTokens,
        calls = calls,
    )
}

private fun ModelPricing.estimateCostInternal(
    promptTokens: Long,
    completionTokens: Long,
    cachedTokens: Long,
    cacheWriteTokens: Long,
    unitTokens: Double,
    calls: Int,
): UsageCost {
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

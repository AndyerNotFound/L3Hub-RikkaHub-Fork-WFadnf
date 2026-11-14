package me.rerere.ai.provider

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

/**
 * 变价规则命中后使用的完整价目表。
 *
 * 各项含义与 [ModelPricing] 相同：每 [priceUnitTokens] 个 token 的价格，0 表示该项不计费。
 */
@Serializable
data class PriceTable(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cacheHit: Double = 0.0,
    val cacheWrite: Double = 0.0,
    val cacheRead: Double = 0.0,
    val perCall: Double = 0.0,
) {
    val isConfigured: Boolean
        get() = input > 0 || output > 0 || cacheHit > 0 || cacheWrite > 0 ||
            cacheRead > 0 || perCall > 0
}

/** 变价规则的条件类型 */
@Serializable
enum class PriceRuleType {
    /** 按本机时区的时间段 */
    TIME_RANGE,

    /** 按本次请求的输入 token 数 */
    CONTEXT_LENGTH,

    /** 按自定义表达式 */
    EXPRESSION,
}

/** 按时间段规则的重复方式 */
@Serializable
enum class PriceRuleRepeat {
    /** 每天在时间段内生效 */
    DAILY,

    /** 每周只在所选星期（[PriceRule.repeatWeekdays]）的时间段内生效 */
    WEEKLY,
}

/**
 * 判断一条变价规则所需的一次请求上下文。
 *
 * [atMillis] 是请求发生的时间，历史用量统计要按流水自己的时间判断，不能传当前时间。
 * [promptTokens] 是请求的输入 token，也就是上下文占用。
 */
data class PriceRuleContext(
    val atMillis: Long = System.currentTimeMillis(),
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val cachedTokens: Long = 0,
    val zoneId: ZoneId = ZoneId.systemDefault(),
)

/**
 * 一条变价规则：条件命中后，整张价目表替换为 [price]。
 *
 * [startMinute] 和 [endMinute] 是本地时间的分钟数，区间为左闭右开；结束早于开始时视为跨夜，
 * 跨夜的凌晨部分按窗口开始的那天判断星期；起止相同视为全天。
 * 时间段默认每天重复，[repeat] 为 WEEKLY 时只在 [repeatWeekdays] 列出的星期生效。
 */
@Serializable
data class PriceRule(
    val id: Uuid = Uuid.random(),
    val enabled: Boolean = true,
    val type: PriceRuleType = PriceRuleType.TIME_RANGE,
    val startMinute: Int = 0,
    val endMinute: Int = 0,
    // 仅 TIME_RANGE 生效的重复方式
    val repeat: PriceRuleRepeat = PriceRuleRepeat.DAILY,
    // 仅 WEEKLY 生效的星期，ISO-8601：1 = 周一 … 7 = 周日，可多选
    val repeatWeekdays: List<Int> = emptyList(),
    // 旧字段：repeatWeekdays 为空时按单选读取，缺省为周一
    val repeatWeekday: Int = 1,
    val contextTokens: Int = 0,
    // true: promptTokens >= contextTokens；false: promptTokens < contextTokens
    val contextAtLeast: Boolean = true,
    val expression: String = "",
    val price: PriceTable = PriceTable(),
) {
    fun matches(context: PriceRuleContext): Boolean = when (type) {
        PriceRuleType.TIME_RANGE -> matchesTimeRange(context)
        PriceRuleType.CONTEXT_LENGTH -> {
            when {
                contextTokens <= 0 -> false
                contextAtLeast -> context.promptTokens >= contextTokens
                else -> context.promptTokens < contextTokens
            }
        }
        PriceRuleType.EXPRESSION -> PriceRuleExpression.matches(expression, context)
    }

    /** WEEKLY 命中的星期；没有多选记录时回退到旧的单选字段，越界按周一处理 */
    fun weekdays(): List<DayOfWeek> {
        val days = repeatWeekdays.filter { it in 1..7 }.distinct().sorted()
        if (days.isNotEmpty()) return days.map { DayOfWeek.of(it) }
        return listOf(DayOfWeek.of(repeatWeekday.coerceIn(1, 7)))
    }

    private fun matchesTimeRange(context: PriceRuleContext): Boolean {
        val at = Instant.ofEpochMilli(context.atMillis).atZone(context.zoneId)
        val date = at.toLocalDate()
        val minute = at.hour * 60 + at.minute
        val start = Math.floorMod(startMinute, MINUTES_PER_DAY)
        val end = Math.floorMod(endMinute, MINUTES_PER_DAY)
        if (start == end) return hitsWeekday(date)
        return if (start < end) {
            minute >= start && minute < end && hitsWeekday(date)
        } else {
            // 跨夜：凌晨那段属于前一天开始的窗口
            when {
                minute >= start -> hitsWeekday(date)
                minute < end -> hitsWeekday(date.minusDays(1))
                else -> false
            }
        }
    }

    private fun hitsWeekday(date: LocalDate): Boolean =
        repeat != PriceRuleRepeat.WEEKLY || date.dayOfWeek in weekdays()
}

/** 表达式里第一个无法解析的字符位置；表达式合法时返回 null。表达式为空也会返回 0。 */
fun priceRuleExpressionErrorPosition(expression: String): Int? =
    PriceRuleExpression.errorPosition(expression)

internal const val MINUTES_PER_DAY = 24 * 60

/** 自定义变价表达式的最大长度，输入框和解析器共用 */
const val MAX_PRICE_RULE_EXPRESSION_LENGTH = 512

package me.rerere.ai.provider

import me.rerere.ai.core.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class PriceRuleTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val delta = 1e-9

    private fun at(hour: Int, minute: Int = 0, day: Int = 14): Long =
        LocalDateTime.of(2026, 11, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun context(
        hour: Int,
        promptTokens: Long = 0,
        completionTokens: Long = 0,
    ) = PriceRuleContext(
        atMillis = at(hour, 0),
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        zoneId = zone,
    )

    @Test
    fun `time range matches the requested minutes and excludes the end`() {
        val rule = PriceRule(
            type = PriceRuleType.TIME_RANGE,
            startMinute = 9 * 60,
            endMinute = 18 * 60,
        )

        assertFalse(rule.matches(PriceRuleContext(atMillis = at(8, 59), zoneId = zone)))
        assertTrue(rule.matches(PriceRuleContext(atMillis = at(9, 0), zoneId = zone)))
        assertTrue(rule.matches(PriceRuleContext(atMillis = at(17, 59), zoneId = zone)))
        assertFalse(rule.matches(PriceRuleContext(atMillis = at(18, 0), zoneId = zone)))
    }

    @Test
    fun `time range crosses midnight`() {
        val rule = PriceRule(
            type = PriceRuleType.TIME_RANGE,
            startMinute = 22 * 60,
            endMinute = 6 * 60,
        )

        assertTrue(rule.matches(PriceRuleContext(atMillis = at(22, 0), zoneId = zone)))
        assertTrue(rule.matches(PriceRuleContext(atMillis = at(23, 59), zoneId = zone)))
        assertTrue(rule.matches(PriceRuleContext(atMillis = at(0, 0), zoneId = zone)))
        assertTrue(rule.matches(PriceRuleContext(atMillis = at(5, 59), zoneId = zone)))
        assertFalse(rule.matches(PriceRuleContext(atMillis = at(6, 0), zoneId = zone)))
        assertFalse(rule.matches(PriceRuleContext(atMillis = at(12, 0), zoneId = zone)))
    }

    @Test
    fun `end of day is treated as midnight`() {
        val rule = PriceRule(
            type = PriceRuleType.TIME_RANGE,
            startMinute = 22 * 60,
            endMinute = 24 * 60,
        )

        assertTrue(rule.matches(PriceRuleContext(atMillis = at(23, 59), zoneId = zone)))
        assertFalse(rule.matches(PriceRuleContext(atMillis = at(21, 59), zoneId = zone)))
    }

    @Test
    fun `equal start and end means all day`() {
        val rule = PriceRule(
            type = PriceRuleType.TIME_RANGE,
            startMinute = 0,
            endMinute = 0,
        )

        assertTrue(rule.matches(PriceRuleContext(atMillis = at(0, 0), zoneId = zone)))
        assertTrue(rule.matches(PriceRuleContext(atMillis = at(23, 59), zoneId = zone)))
    }

    @Test
    fun `weekly time range only matches the selected weekday`() {
        val rule = PriceRule(
            type = PriceRuleType.TIME_RANGE,
            startMinute = 22 * 60,
            endMinute = 6 * 60,
            repeat = PriceRuleRepeat.WEEKLY,
            repeatWeekday = DayOfWeek.MONDAY.value,
        )

        // 2026-11-16 是周一，11-17 是周二，11-18 是周三
        assertTrue(rule.matches(PriceRuleContext(atMillis = at(22, 30, day = 16), zoneId = zone)))
        // 跨夜窗口的凌晨部分仍按周一开始算
        assertTrue(rule.matches(PriceRuleContext(atMillis = at(2, 0, day = 17), zoneId = zone)))
        assertFalse(rule.matches(PriceRuleContext(atMillis = at(12, 0, day = 16), zoneId = zone)))
        assertFalse(rule.matches(PriceRuleContext(atMillis = at(22, 30, day = 17), zoneId = zone)))
        assertFalse(rule.matches(PriceRuleContext(atMillis = at(2, 0, day = 18), zoneId = zone)))
        assertFalse(rule.matches(PriceRuleContext(atMillis = at(6, 0, day = 17), zoneId = zone)))
    }

    @Test
    fun `weekly time range falls back to monday for an invalid weekday`() {
        val rule = PriceRule(
            type = PriceRuleType.TIME_RANGE,
            repeat = PriceRuleRepeat.WEEKLY,
            repeatWeekday = 0,
        )

        assertEquals(DayOfWeek.MONDAY, rule.weekday())
        assertTrue(rule.matches(PriceRuleContext(atMillis = at(12, day = 16), zoneId = zone)))
        assertFalse(rule.matches(PriceRuleContext(atMillis = at(12, day = 17), zoneId = zone)))
    }

    @Test
    fun `context length compares input tokens`() {
        val atLeast = PriceRule(
            type = PriceRuleType.CONTEXT_LENGTH,
            contextTokens = 200_000,
            contextAtLeast = true,
        )
        val lessThan = atLeast.copy(contextAtLeast = false)

        assertFalse(atLeast.matches(context(hour = 12, promptTokens = 199_999)))
        assertTrue(atLeast.matches(context(hour = 12, promptTokens = 200_000)))
        assertTrue(lessThan.matches(context(hour = 12, promptTokens = 199_999)))
        assertFalse(lessThan.matches(context(hour = 12, promptTokens = 200_000)))
    }

    @Test
    fun `zero context threshold is inert`() {
        val rule = PriceRule(
            type = PriceRuleType.CONTEXT_LENGTH,
            contextTokens = 0,
        )

        assertFalse(rule.matches(context(hour = 12, promptTokens = 0)))
        assertFalse(rule.matches(context(hour = 12, promptTokens = 1_000_000)))
    }

    @Test
    fun `expression supports time tokens and logic`() {
        val rule = PriceRule(
            type = PriceRuleType.EXPRESSION,
            expression = "hour >= 22 || hour < 8",
        )

        assertTrue(rule.matches(context(hour = 23)))
        assertTrue(rule.matches(context(hour = 7)))
        assertFalse(rule.matches(context(hour = 8)))
        assertFalse(rule.matches(context(hour = 12)))
    }

    @Test
    fun `expression supports arithmetic comparison and suffixes`() {
        val rule = PriceRule(
            type = PriceRuleType.EXPRESSION,
            expression = "promptTokens > 200k && weekday == 6",
        )

        // 2026-11-14 是周六
        assertTrue(
            rule.matches(
                PriceRuleContext(
                    atMillis = at(12, day = 14),
                    promptTokens = 300_000,
                    zoneId = zone,
                )
            )
        )
        assertFalse(
            rule.matches(
                PriceRuleContext(
                    atMillis = at(12, day = 12),
                    promptTokens = 300_000,
                    zoneId = zone,
                )
            )
        )
        assertFalse(
            rule.matches(
                PriceRuleContext(
                    atMillis = at(12, day = 14),
                    promptTokens = 199_999,
                    zoneId = zone,
                )
            )
        )
    }

    @Test
    fun `expression error position points at the broken token`() {
        assertEquals(0, priceRuleExpressionErrorPosition("promptToken > 1") ?: -1)
        assertEquals(10, priceRuleExpressionErrorPosition("1 + 2 * (3") ?: -1)
        assertNull(priceRuleExpressionErrorPosition("hour >= 8 && hour < 22"))
        assertEquals(0, priceRuleExpressionErrorPosition(""))
    }

    @Test
    fun `first enabled matching rule replaces the whole price table`() {
        val pricing = ModelPricing(
            input = 1.0,
            output = 2.0,
            rules = listOf(
                PriceRule(
                    type = PriceRuleType.TIME_RANGE,
                    startMinute = 0,
                    endMinute = 8 * 60,
                    price = PriceTable(input = 0.5, output = 1.0),
                ),
                PriceRule(
                    type = PriceRuleType.CONTEXT_LENGTH,
                    contextTokens = 1_000,
                    price = PriceTable(input = 3.0, output = 4.0),
                ),
            ),
        )

        val early = pricing.applyRules(context(hour = 3, promptTokens = 2_000))
        assertEquals(0.5, early.input, delta)
        assertEquals(1.0, early.output, delta)

        val noon = pricing.applyRules(context(hour = 12, promptTokens = 2_000))
        assertEquals(3.0, noon.input, delta)
        assertEquals(4.0, noon.output, delta)

        val small = pricing.applyRules(context(hour = 12, promptTokens = 100))
        assertEquals(1.0, small.input, delta)
        assertEquals(2.0, small.output, delta)
    }

    @Test
    fun `disabled rules are skipped`() {
        val pricing = ModelPricing(
            input = 1.0,
            rules = listOf(
                PriceRule(
                    enabled = false,
                    type = PriceRuleType.TIME_RANGE,
                    startMinute = 0,
                    endMinute = 0,
                    price = PriceTable(input = 9.0),
                ),
            ),
        )

        assertEquals(1.0, pricing.applyRules(context(hour = 12)).input, delta)
    }

    @Test
    fun `estimate cost applies the matching rule`() {
        val pricing = ModelPricing(
            input = 1.0,
            rules = listOf(
                PriceRule(
                    type = PriceRuleType.CONTEXT_LENGTH,
                    contextTokens = 1_000,
                    price = PriceTable(input = 2.0, perCall = 0.1),
                ),
            ),
        )
        val usage = TokenUsage(promptTokens = 2_000, completionTokens = 1_000)

        val base = pricing.estimateCost(usage)
        val ruled = pricing.estimateCost(
            usage = usage,
            context = PriceRuleContext(
                atMillis = at(12),
                promptTokens = usage.promptTokens.toLong(),
                completionTokens = usage.completionTokens.toLong(),
                cachedTokens = usage.cachedTokens.toLong(),
                zoneId = zone,
            ),
        )

        assertEquals(0.002, base.total, delta)
        assertEquals(0.104, ruled.total, delta)
    }

    @Test
    fun `a rule only price table still counts as configured`() {
        val pricing = ModelPricing(
            rules = listOf(PriceRule(price = PriceTable(input = 1.0))),
        )

        assertTrue(pricing.isConfigured)
        assertFalse(pricing.hasPrice)
    }
}

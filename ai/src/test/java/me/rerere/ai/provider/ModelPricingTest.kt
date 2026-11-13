package me.rerere.ai.provider

import me.rerere.ai.core.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelPricingTest {
    private val delta = 1e-9

    @Test
    fun `bills uncached input cache hit and output separately`() {
        val pricing = ModelPricing(input = 3.0, output = 15.0, cacheHit = 0.3)
        val cost = pricing.estimateCost(
            TokenUsage(
                promptTokens = 1_000_000,
                completionTokens = 100_000,
                cachedTokens = 500_000,
            )
        )

        assertEquals(1.5, cost.input, delta)
        assertEquals(1.5, cost.output, delta)
        assertEquals(0.15, cost.cacheHit, delta)
        assertEquals(3.15, cost.total, delta)
    }

    @Test
    fun `cache read price overrides cache hit price`() {
        val pricing = ModelPricing(input = 3.0, cacheHit = 0.3, cacheRead = 0.1)
        val cost = pricing.estimateCost(
            TokenUsage(promptTokens = 1_000_000, cachedTokens = 500_000)
        )

        assertEquals(0.0, cost.cacheHit, delta)
        assertEquals(0.05, cost.cacheRead, delta)
    }

    @Test
    fun `cache write tokens are billed separately from uncached input`() {
        val pricing = ModelPricing(input = 3.0, cacheWrite = 3.75)
        val cost = pricing.estimateCost(
            TokenUsage(promptTokens = 1_000_000, cachedTokens = 200_000, cacheWriteTokens = 300_000)
        )

        assertEquals(1.5, cost.input, delta)
        assertEquals(1.125, cost.cacheWrite, delta)
    }

    @Test
    fun `binary unit bills per mebi token`() {
        val pricing = ModelPricing(input = 3.0)
        val decimal = pricing.estimateCost(
            TokenUsage(promptTokens = 1_000_000),
            unitTokens = priceUnitTokens(binaryUnits = false),
        )
        val binary = pricing.estimateCost(
            TokenUsage(promptTokens = 1_000_000),
            unitTokens = priceUnitTokens(binaryUnits = true),
        )

        assertEquals(3.0, decimal.total, delta)
        assertEquals(3.0 * 1_000_000 / 1_048_576, binary.total, delta)
    }

    @Test
    fun `per call price multiplies by request count`() {
        val cost = ModelPricing(perCall = 0.02).estimateCost(TokenUsage(), calls = 3)

        assertEquals(0.06, cost.perCall, delta)
    }

    @Test
    fun `token usage and long totals produce the same cost`() {
        val pricing = ModelPricing(input = 3.0, output = 15.0, cacheHit = 0.3, perCall = 0.01)
        val usage = TokenUsage(promptTokens = 900_000, completionTokens = 120_000, cachedTokens = 400_000)
        val detailed = pricing.estimateCost(usage, calls = 2)
        val totals = pricing.estimateCost(
            promptTokens = usage.promptTokens.toLong(),
            completionTokens = usage.completionTokens.toLong(),
            cachedTokens = usage.cachedTokens.toLong(),
            calls = 2,
        )

        assertEquals(detailed, totals)
    }

    @Test
    fun `long totals are not clamped at the int boundary`() {
        val pricing = ModelPricing(input = 1.0)
        val promptTokens = Int.MAX_VALUE.toLong() + 1_500_000
        val cost = pricing.estimateCost(
            promptTokens = promptTokens,
            completionTokens = 0,
            cachedTokens = 0,
        )

        assertEquals(promptTokens / 1_000_000.0, cost.total, delta)
    }

    @Test
    fun `empty pricing costs nothing`() {
        val cost = ModelPricing().estimateCost(
            TokenUsage(promptTokens = 1_000_000, completionTokens = 1_000_000)
        )

        assertEquals(0.0, cost.total, delta)
    }
}

package me.rerere.ai.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BalanceThresholdTest {
    @Test
    fun `threshold accepts a number with an optional currency`() {
        assertEquals(BigDecimal("10"), parseBalanceThreshold("10")?.amount)
        assertNull(parseBalanceThreshold("10")?.currency)
        assertEquals(BigDecimal("10.5"), parseBalanceThreshold("CNY 10.5")?.amount)
        assertEquals("CNY", parseBalanceThreshold("CNY 10.5")?.currency)
        assertEquals("USD", parseBalanceThreshold("\$10")?.currency)
        assertEquals("USD", parseBalanceThreshold("10 USD")?.currency)
        assertEquals("USD", parseBalanceThreshold("usd 10")?.currency)
        assertEquals("CNY", parseBalanceThreshold("¥10")?.currency)
        assertNull(parseBalanceThreshold(""))
        assertNull(parseBalanceThreshold("abc"))
    }

    @Test
    fun `warning compares the primary currency when no unit is given`() {
        assertTrue(isBalanceLow("CNY 9.99", "10"))
        assertTrue(isBalanceLow("CNY 10.00", "10"))
        assertFalse(isBalanceLow("CNY 10.01", "10"))
        assertFalse(isBalanceLow("CNY 100.00 · USD 1.00", "10"))
        assertTrue(isBalanceLow("CNY 100.00 · USD 1.00", "USD 10"))
        assertFalse(isBalanceLow("CNY 1.00", "USD 10"))
    }

    @Test
    fun `balance without a currency still matches a threshold with one`() {
        assertTrue(isBalanceLow("5", "CNY 10"))
        assertFalse(isBalanceLow("5", "CNY 3"))
    }

    @Test
    fun `unparsable balance never warns`() {
        assertFalse(isBalanceLow("unlimited", "10"))
        assertFalse(isBalanceLow("CNY 1.00", ""))
    }
}

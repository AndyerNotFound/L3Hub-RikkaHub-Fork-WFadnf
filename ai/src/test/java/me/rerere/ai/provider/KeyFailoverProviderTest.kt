package me.rerere.ai.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyFailoverProviderTest {
    @Test
    fun `balances are summed per currency`() {
        assertEquals(
            "CNY 15.00 · USD 2.00",
            sumBalanceValues(listOf("CNY 10.00 · USD 2.00", "CNY 5.00")),
        )
    }

    @Test
    fun `symbol and suffix currencies keep their format`() {
        assertEquals(
            "\$12.50 · EUR 3.00",
            sumBalanceValues(listOf("\$10.00", "\$2.50", "3.00 EUR")),
        )
    }

    @Test
    fun `unparseable text falls back to concatenation`() {
        assertEquals("unknown + unlimited", sumBalanceValues(listOf("unknown", "unlimited")))
    }
}

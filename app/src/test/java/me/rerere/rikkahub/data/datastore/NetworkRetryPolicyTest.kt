package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.ai.retryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NetworkRetryPolicyTest {
    @Test
    fun `custom retry uses configured count and interval`() {
        val policy = Settings(
            networkSetting = NetworkSetting(
                retryMaxCount = 5,
                retryIntervalSeconds = 7,
                retryErrorCodes = "429, 503",
            )
        ).retryPolicy()

        assertEquals(5, policy.maxRetries)
        assertEquals(7_000L, policy.initialDelay.inWholeMilliseconds)
        assertFalse(policy.exponentialBackoff)
    }

    @Test
    fun `retry matches configured status codes in the error message`() {
        val policy = Settings(
            networkSetting = NetworkSetting(retryErrorCodes = "429,503"),
        ).retryPolicy()

        assertTrue(policy.retryOn(IOException("connection reset")))
        assertTrue(policy.retryOn(IllegalStateException("Failed to get response: 429 too many requests")))
        assertTrue(policy.retryOn(IllegalStateException("Request failed: 503 Service Unavailable")))
        assertFalse(policy.retryOn(IllegalStateException("Failed to get response: 400 bad request")))
        // 500 不能命中 1500
        assertFalse(policy.retryOn(IllegalStateException("Request failed: 1500")))
    }

    @Test
    fun `disabled retry has no attempts`() {
        val policy = Settings(networkSetting = NetworkSetting(enableAutoRetry = false)).retryPolicy()

        assertEquals(0, policy.maxRetries)
    }
}

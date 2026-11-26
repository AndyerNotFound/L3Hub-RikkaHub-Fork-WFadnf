package me.rerere.rikkahub.data.fingerprint

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.StreamChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FingerprintReplyCollectorTest {
    @Test
    fun `relaxed cap waits for complete numbers and gives usage a chance to arrive`() = runTest {
        var accepted: String? = null
        var usageSent = false
        var closed = false
        val reply = collectFingerprintReply(expectedCount = 2, allowPartial = true, onCappedReply = { accepted = it }) {
            flow {
                try {
                    emit(StreamChunk.ReasoningDelta("thinking", "42, 99,"))
                    emit(StreamChunk.TextDelta("text", "12, 3"))
                    assertNull(accepted)
                    emit(StreamChunk.TextDelta("text", "55, 99"))
                    assertEquals("12, 355", accepted)
                    delay(1_000)
                    emit(StreamChunk.Usage(TokenUsage(completionTokens = 14)))
                    usageSent = true
                    awaitCancellation()
                } finally {
                    closed = true
                }
            }
        }
        assertEquals("12, 355", reply)
        assertTrue(usageSent)
        assertTrue(closed)
        assertEquals(1_500L, testScheduler.currentTime)
    }

    @Test
    fun `strict mode preserves the full reply and accepts a normal Gemini finish`() = runTest {
        val reply = collectFingerprintReply(expectedCount = 2, allowPartial = false) {
            flowOf(StreamChunk.TextDelta("text", "12, 355, 99"), StreamChunk.Finish("STOP"))
        }
        assertEquals("12, 355, 99", reply)
    }

    @Test
    fun `an incomplete stream without a cap is rejected even in relaxed mode`() = runTest {
        val error = runCatching {
            collectFingerprintReply(expectedCount = 3, allowPartial = true) {
                flowOf(StreamChunk.TextDelta("text", "12, 355, 3"), StreamChunk.Finish("length"))
            }
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun `probe timeout closes only the stalled reply`() = runTest {
        var closed = false
        val error = runCatching {
            collectFingerprintReply(expectedCount = 3, allowPartial = false) {
                flow {
                    try {
                        emit(StreamChunk.TextDelta("text", "12, 355, "))
                        awaitCancellation()
                    } finally {
                        closed = true
                    }
                }
            }
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(closed)
        assertEquals(180_000L, testScheduler.currentTime)
    }

    @Test
    fun `user cancellation closes a capped stream without returning normally`() = runTest {
        var accepted: String? = null
        var returned = false
        var closed = false
        val detection = launch {
            collectFingerprintReply(expectedCount = 2, allowPartial = true, onCappedReply = { accepted = it }) {
                flow {
                    try {
                        emit(StreamChunk.TextDelta("text", "12, 355, "))
                        awaitCancellation()
                    } finally {
                        closed = true
                    }
                }
            }
            returned = true
        }
        runCurrent()
        assertEquals("12, 355", accepted)
        detection.cancelAndJoin()
        assertTrue(closed)
        assertFalse(returned)
    }
}

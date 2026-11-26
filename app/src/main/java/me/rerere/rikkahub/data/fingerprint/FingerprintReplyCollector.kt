package me.rerere.rikkahub.data.fingerprint

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.ai.ui.StreamChunk

private const val MAX_REPLY_CHARS = 64 * 1024
private const val REQUEST_TIMEOUT_MS = 180_000L
private const val USAGE_GRACE_MS = 1_500L
private val COMPLETE_FINISH_REASONS = setOf("stop", "end_turn", "completed")
private val REFUSED_FINISH_REASONS = setOf("refusal", "content_filter", "safety")

/** Collects visible answer text from one request. The caller supplies the usage-recording stream. */
internal suspend fun collectFingerprintReply(
    expectedCount: Int,
    allowPartial: Boolean,
    onCappedReply: (String) -> Unit = {},
    openStream: suspend () -> Flow<StreamChunk>,
): String = coroutineScope {
    require(expectedCount > 0)
    val reachedCap = CompletableDeferred<String>()
    val request = async {
        val text = StringBuilder()
        val parser = if (allowPartial) FingerprintNumberParser() else null
        var finishReason: String? = null
        var cappedReply: String? = null
        try {
            openStream().collect { chunk ->
                // The usage wrapper still sees every event during the grace period.
                if (cappedReply != null) return@collect
                when (chunk) {
                    is StreamChunk.TextDelta -> {
                        check(chunk.text.length <= MAX_REPLY_CHARS - text.length) { "Fingerprint reply is too large" }
                        text.append(chunk.text)
                        if (parser != null) {
                            parser.append(chunk.text)
                            if (parser.completedCount >= expectedCount) {
                                val capped = parser.numbers(includeTrailingNumber = false)
                                    .take(expectedCount).joinToString(", ")
                                // Preserve the accepted sample if the user stops during usage collection.
                                onCappedReply(capped)
                                cappedReply = capped
                                reachedCap.complete(capped)
                            }
                        }
                    }
                    is StreamChunk.Finish -> {
                        finishReason = chunk.finishReason?.lowercase()
                        check(finishReason !in REFUSED_FINISH_REASONS) { "Fingerprint request was refused" }
                    }
                    is StreamChunk.ToolCallStart, is StreamChunk.ToolCallDelta, is StreamChunk.ToolCallEnd,
                    is StreamChunk.ToolCallOutput, is StreamChunk.ServerToolStart,
                    is StreamChunk.ServerToolInputDelta, is StreamChunk.ServerToolInputEnd,
                    is StreamChunk.ServerToolEnd -> error("Tool use in fingerprint reply")
                    else -> Unit
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (cappedReply == null) throw error
        }
        cappedReply ?: run {
            check(finishReason in COMPLETE_FINISH_REASONS && text.isNotBlank()) { "Incomplete fingerprint reply" }
            text.toString()
        }
    }
    try {
        withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            select {
                request.onAwait { it }
                reachedCap.onAwait { reply ->
                    withTimeoutOrNull(USAGE_GRACE_MS) { request.await() }
                    reply
                }
            }
        } ?: if (reachedCap.isCompleted) reachedCap.await() else error("Fingerprint request timed out")
    } finally {
        // The request is a child of this scope. This leaves subsequent probes and user cancellation independent.
        request.cancel()
    }
}

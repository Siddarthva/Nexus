package com.example.nexusremote.util

import kotlinx.coroutines.delay

/**
 * Runs [block] up to [maxAttempts] times with exponential backoff
 * (initialDelayMs, x2, x4, ...). Stops immediately when [shouldRetry] returns false
 * for a failure, e.g. for authentication errors which must never be retried in a loop.
 */
suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 500,
    maxDelayMs: Long = 4_000,
    shouldRetry: (NexusResult.Failure) -> Boolean,
    block: suspend (attempt: Int) -> NexusResult<T>,
): NexusResult<T> {
    require(maxAttempts >= 1)
    var delayMs = initialDelayMs
    var attempt = 1
    while (true) {
        val result = block(attempt)
        if (result is NexusResult.Success) return result
        val failure = result as NexusResult.Failure
        if (attempt >= maxAttempts || !shouldRetry(failure)) return failure
        delay(delayMs)
        delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
        attempt++
    }
}

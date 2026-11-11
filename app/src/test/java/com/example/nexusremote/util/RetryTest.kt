package com.example.nexusremote.util

import com.example.nexusremote.domain.model.ErrorCode
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RetryTest {

    private val timeout = NexusResult.Failure(ErrorCode.CONNECT_TIMEOUT, "t")
    private val auth = NexusResult.Failure(ErrorCode.AUTH_REVOKED, "a")
    private val retryTransient: (NexusResult.Failure) -> Boolean = { it.errorCode == ErrorCode.CONNECT_TIMEOUT }

    @Test
    fun stopsAfterThreeAttempts() = runTest {
        var attempts = 0
        val result = retryWithBackoff(shouldRetry = retryTransient) { attempts++; timeout }
        assertEquals(3, attempts)
        assertEquals(timeout, result)
    }

    @Test
    fun backoffIsExponential() = runTest {
        val attemptTimes = mutableListOf<Long>()
        retryWithBackoff(shouldRetry = retryTransient) { attemptTimes.add(currentTime); timeout }
        assertEquals(listOf(0L, 500L, 1_500L), attemptTimes.map { it - attemptTimes.first() })
    }

    @Test
    fun backoffIsCappedAtMaxDelay() = runTest {
        val attemptTimes = mutableListOf<Long>()
        retryWithBackoff(maxAttempts = 6, initialDelayMs = 1_000, maxDelayMs = 3_000, shouldRetry = retryTransient) {
            attemptTimes.add(currentTime); timeout
        }
        val gaps = attemptTimes.zipWithNext { a, b -> b - a }
        assertEquals(listOf(1_000L, 2_000L, 3_000L, 3_000L, 3_000L), gaps)
    }

    @Test
    fun neverRetriesAnAuthFailure() = runTest {
        var attempts = 0
        val result = retryWithBackoff(shouldRetry = retryTransient) { attempts++; auth }
        assertEquals(1, attempts)
        assertEquals(auth, result)
    }

    @Test
    fun stopsRetryingWhenAnAuthFailureFollowsATransientOne() = runTest {
        val results = ArrayDeque(listOf(timeout, auth, NexusResult.unit()))
        var attempts = 0
        val result = retryWithBackoff(shouldRetry = retryTransient) { attempts++; results.removeFirst() }
        assertEquals(2, attempts)
        assertEquals(auth, result)
    }

    @Test
    fun returnsImmediatelyOnSuccess() = runTest {
        var attempts = 0
        val result = retryWithBackoff(shouldRetry = { true }) { attempts++; NexusResult.Success("ok") }
        assertEquals(1, attempts)
        assertTrue(result.isSuccess)
        assertEquals(0L, currentTime)
    }
}

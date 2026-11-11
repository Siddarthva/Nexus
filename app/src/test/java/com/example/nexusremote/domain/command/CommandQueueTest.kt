package com.example.nexusremote.domain.command

import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.ui.components.REPEAT_INITIAL_DELAY_MS
import com.example.nexusremote.ui.components.REPEAT_INTERVAL_MS
import com.example.nexusremote.util.NexusResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommandQueueTest {

    /** A send function that blocks until [release] is completed, recording every command. */
    private class GatedSender {
        val sent = mutableListOf<TvCommand>()
        var gate = CompletableDeferred<Unit>()
        var result: NexusResult<Unit> = NexusResult.unit()
        suspend fun send(command: TvCommand): NexusResult<Unit> {
            sent.add(command)
            gate.await()
            return result
        }
        fun release() = gate.complete(Unit)
    }

    private fun TestScope.queueWith(sender: GatedSender): Pair<CommandQueue, MutableList<CommandQueue.Outcome>> {
        val queue = CommandQueue(backgroundScope) { sender.send(it) }
        val outcomes = mutableListOf<CommandQueue.Outcome>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { queue.outcomes.collect { outcomes.add(it) } }
        return queue to outcomes
    }

    @Test
    fun repeatIntervalIsThrottledToAbout150ms() {
        assertEquals(150L, REPEAT_INTERVAL_MS)
        assertTrue(REPEAT_INITIAL_DELAY_MS > REPEAT_INTERVAL_MS)
    }

    @Test
    fun tapsAreSentInOrder() = runTest {
        val sender = GatedSender().apply { release() }
        val (queue, outcomes) = queueWith(sender)
        listOf(TvCommand.UP, TvCommand.SELECT, TvCommand.BACK).forEach(queue::enqueue)
        runCurrent()
        assertEquals(listOf(TvCommand.UP, TvCommand.SELECT, TvCommand.BACK), sender.sent)
        assertEquals(3, outcomes.size)
    }

    @Test
    fun repeatedPressesAreThrottledWhileTheLinkIsBusy() = runTest {
        val sender = GatedSender()
        val (queue, _) = queueWith(sender)
        queue.enqueue(TvCommand.RIGHT) // first press: in flight, blocked on the gate
        runCurrent()
        repeat(50) { queue.enqueueRepeat(TvCommand.RIGHT) } // a long hold on a slow link
        sender.release()
        runCurrent()
        // 1 tap + at most MAX_PENDING_REPEATS repeats; the other 48 are never queued.
        assertEquals(1 + CommandQueue.MAX_PENDING_REPEATS, sender.sent.size)
    }

    @Test
    fun staleRepeatsAreDroppedWhenTheUserReleases() = runTest {
        val sender = GatedSender()
        val (queue, _) = queueWith(sender)
        queue.enqueue(TvCommand.DOWN)
        runCurrent()
        queue.enqueueRepeat(TvCommand.DOWN)
        queue.enqueueRepeat(TvCommand.DOWN)
        queue.cancelRepeats() // finger lifted while two repeats are still queued
        sender.release()
        runCurrent()
        assertEquals(listOf(TvCommand.DOWN), sender.sent)
    }

    @Test
    fun repeatsFromANewHoldAreSentAfterRelease() = runTest {
        val sender = GatedSender().apply { release() }
        val (queue, _) = queueWith(sender)
        queue.cancelRepeats()
        queue.enqueueRepeat(TvCommand.VOLUME_UP)
        runCurrent()
        assertEquals(listOf(TvCommand.VOLUME_UP), sender.sent)
    }

    @Test
    fun tapsQueuedBehindAReleaseAreNotDropped() = runTest {
        val sender = GatedSender()
        val (queue, _) = queueWith(sender)
        queue.enqueue(TvCommand.UP)
        runCurrent()
        queue.enqueue(TvCommand.SELECT)
        queue.cancelRepeats()
        sender.release()
        runCurrent()
        assertEquals(listOf(TvCommand.UP, TvCommand.SELECT), sender.sent)
    }

    @Test
    fun everyTapProducesExactlyOneOutcomeEvenWhenTheQueueIsFull() = runTest {
        val sender = GatedSender()
        val (queue, outcomes) = queueWith(sender)
        val taps = 25
        repeat(taps) { queue.enqueue(TvCommand.HOME) }
        runCurrent()
        sender.release()
        runCurrent()
        assertEquals("no tap may disappear silently", taps, outcomes.size)
        val failures = outcomes.filter { it.result is NexusResult.Failure }
        failures.forEach { assertEquals(ErrorCode.COMMAND_FAILED, (it.result as NexusResult.Failure).errorCode) }
        assertEquals(taps, sender.sent.size + failures.size)
    }

    @Test
    fun failuresAreReportedAsOutcomes() = runTest {
        val sender = GatedSender().apply {
            release()
            result = NexusResult.Failure(ErrorCode.COMMAND_FAILED, "x")
        }
        val (queue, outcomes) = queueWith(sender)
        queue.enqueue(TvCommand.MUTE)
        runCurrent()
        assertEquals(TvCommand.MUTE, outcomes.single().command)
        assertTrue(outcomes.single().result is NexusResult.Failure)
    }
}

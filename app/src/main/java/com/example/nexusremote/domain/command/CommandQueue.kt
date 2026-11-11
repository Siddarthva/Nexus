package com.example.nexusremote.domain.command

import com.example.nexusremote.domain.model.ErrorCode
import com.example.nexusremote.util.NexusResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Serialises remote commands so they reach the TV in order, one at a time.
 *
 * - **Taps are never dropped silently.** Each tap produces exactly one [Outcome]: the send
 *   result, or a COMMAND_FAILED outcome if the bounded queue is full.
 * - **Auto-repeat presses are throttled and cancellable.** At most [MAX_PENDING_REPEATS]
 *   repeats may wait at once, so a slow link cannot build a backlog. Each repeat carries the
 *   "hold generation" it was created in. [cancelRepeats] (finger lifted) bumps the generation,
 *   so stale repeats still in the queue are discarded instead of sent. Repeats are an implicit
 *   stream, so dropping surplus or stale ones is intended and produces no outcome.
 */
class CommandQueue(
    scope: CoroutineScope,
    private val send: suspend (TvCommand) -> NexusResult<Unit>,
) {
    data class Outcome(val command: TvCommand, val result: NexusResult<Unit>)

    private data class Entry(val command: TvCommand, val repeatGeneration: Long?)

    private val generation = AtomicLong(0)
    private val pendingRepeats = AtomicInteger(0)
    private val channel = Channel<Entry>(capacity = CAPACITY)

    // Large enough that failure outcomes from enqueue() (which cannot suspend) are not lost.
    private val _outcomes = MutableSharedFlow<Outcome>(extraBufferCapacity = OUTCOME_BUFFER)

    /** Result of every tap, and of every repeat that was actually sent. */
    val outcomes: SharedFlow<Outcome> = _outcomes.asSharedFlow()

    init {
        scope.launch {
            for (entry in channel) {
                if (entry.repeatGeneration != null) {
                    pendingRepeats.decrementAndGet()
                    if (entry.repeatGeneration != generation.get()) continue
                }
                _outcomes.emit(Outcome(entry.command, send(entry.command)))
            }
        }
    }

    /** Queues a single press. If the queue is full, a COMMAND_FAILED outcome is reported. */
    fun enqueue(command: TvCommand) {
        if (channel.trySend(Entry(command, null)).isFailure) {
            _outcomes.tryEmit(Outcome(command, NexusResult.Failure(ErrorCode.COMMAND_FAILED, "Command queue full")))
        }
    }

    /** Queues an auto-repeat press for the current hold. Skipped if repeats are backing up. */
    fun enqueueRepeat(command: TvCommand) {
        if (pendingRepeats.get() >= MAX_PENDING_REPEATS) return
        pendingRepeats.incrementAndGet()
        if (channel.trySend(Entry(command, generation.get())).isFailure) {
            pendingRepeats.decrementAndGet()
        }
    }

    /** Call when the user lifts their finger: queued repeats from that hold are discarded. */
    fun cancelRepeats() {
        generation.incrementAndGet()
    }

    companion object {
        const val CAPACITY = 16
        const val MAX_PENDING_REPEATS = 2
        private const val OUTCOME_BUFFER = 64
    }
}

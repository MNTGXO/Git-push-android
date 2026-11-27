package com.mntx.gitpulse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Flow contract behind a real device crash: a 76-file push closed the app at file #10.
 *
 * `flow { }` forbids emitting from a child coroutine. SafeCollector compares the job that emits
 * against the job that collects and throws "Flow invariant is violated" the moment they differ.
 * That throw happens *inside the child*, so it is not an `Exception` the engine's try/catch can
 * turn into a friendly message — it tears down the scope and the process with it.
 *
 * The engine used to do exactly that in three places (push hashing, push upload, pull download).
 * These tests document the trap and pin the channel-based shape that replaced it, so the pattern
 * cannot quietly come back.
 */
class FlowInvariantTest {

    /** The old pattern, so the reason for the rewrite is executable rather than folklore. */
    @Test
    fun `emitting from a child coroutine violates the flow invariant`() {
        val progress = flow {
            emit(0) // collector's own job: allowed
            coroutineScope {
                launch(Dispatchers.Default) {
                    emit(1) // child job: forbidden
                }
            }
        }

        val error = try {
            runBlocking { progress.collect { } }
            null
        } catch (e: Throwable) {
            e
        }

        assertTrue(
            "expected 'Flow invariant is violated', got: $error",
            error is IllegalStateException &&
                error.message.orEmpty().contains("Flow invariant is violated"),
        )
    }

    /**
     * The replacement: workers `send` into the channelFlow, the builder emits. 121 workers is past
     * every `% 10` progress threshold the engine uses, i.e. the exact region that used to crash.
     */
    @Test
    fun `workers can report progress concurrently without violating the contract`() = runBlocking {
        val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

        val stream = channelFlow {
            coroutineScope {
                repeat(121) { i ->
                    // A plain `launch` body has no receiver; `send` is a ProducerScope member.
                    launch(Dispatchers.Default) { this@channelFlow.send(i) }
                }
            }
            // coroutineScope joins the children before close(), so nothing is lost.
            close()
        }

        stream.collect { seen += it }

        assertEquals("every worker's event must arrive exactly once", 121, seen.size)
        assertEquals("and the payloads must be intact", (0..120).toSet(), seen)
    }

    /** Ordering from a single producer survives the channel, which the log pane relies on. */
    @Test
    fun `a single producer keeps strict ordering`() = runBlocking {
        val seen = mutableListOf<String>()
        channelFlow {
            launch { for (i in 1..76) this@channelFlow.send("f$i") }
            close()
        }.collect { seen += it }

        assertEquals((1..76).map { "f$it" }, seen)
    }

    /**
     * Cancellation must still reach the workers: converting `flow` to `channelFlow` must not turn
     * a cancelled push into one that keeps uploading in the background.
     */
    @Test
    fun `cancelling the collector stops the workers`() = runBlocking {
        val processed = java.util.concurrent.atomic.AtomicInteger(0)
        val stream = channelFlow {
            coroutineScope {
                repeat(50) {
                    launch(Dispatchers.Default) {
                        Thread.sleep(20)
                        processed.incrementAndGet()
                        this@channelFlow.send(it)
                    }
                }
            }
            close()
        }

        val job = launch(Dispatchers.Default) { stream.collect { } }
        kotlinx.coroutines.delay(80)
        job.cancel()
        job.join()
        val atCancel = processed.get()
        kotlinx.coroutines.delay(150)

        assertTrue(
            "workers kept running after cancel: $atCancel -> ${processed.get()}",
            processed.get() <= atCancel + 2,
        )
    }
}

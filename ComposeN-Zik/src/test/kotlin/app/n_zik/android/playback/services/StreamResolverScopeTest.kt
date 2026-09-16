package app.n_zik.android.playback.services

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StreamResolverScopeTest {

    @Test
    fun `a failing child does not cancel the shared scope`() = runBlocking {
        val rootJob = requireNotNull(scope.coroutineContext[Job]) { "scope must have a root Job" }
        // Local handler keeps the simulated failure off the thread's uncaught-exception handler
        // (a JVM-global handler swap would mask unrelated failures in the shared test JVM).
        val handler = CoroutineExceptionHandler { _, _ -> }
        val failing = scope.launch(handler) { error("simulated background task failure") }
        failing.join()
        assertTrue(failing.isCancelled, "failing child must have run and failed")
        assertFalse(rootJob.isCancelled, "scope must not be cancelled by a failing child")
    }

    @Test
    fun `a later launch still runs after a sibling failure`() = runBlocking {
        val handler = CoroutineExceptionHandler { _, _ -> }
        val failing = scope.launch(handler) { error("simulated background task failure") }
        failing.join()
        val ran = CountDownLatch(1)
        scope.launch { ran.countDown() }
        // PLAYBACK is a shared single-thread dispatcher; 10s absorbs any queued test work ahead of ours.
        assertTrue(ran.await(10, TimeUnit.SECONDS), "later launch must still run after a sibling failure")
    }
}

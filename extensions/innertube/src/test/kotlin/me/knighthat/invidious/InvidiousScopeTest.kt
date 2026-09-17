package me.knighthat.invidious

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Before this fix, useUnofficialInstances's setter created a brand-new CoroutineScope(Dispatchers.IO)
 * on every toggle, never tracked or cancellable. Now it reuses a single SupervisorJob-backed scope,
 * so a fetch failure from one toggle can't take out a later one.
 */
class InvidiousScopeTest {

    @Test
    fun `a failing fetch does not cancel the shared scope`() = runBlocking {
        val rootJob = requireNotNull(Invidious.scope.coroutineContext[Job]) { "scope must have a root Job" }
        val handler = CoroutineExceptionHandler { _, _ -> }
        val failing = Invidious.scope.launch(handler) { error("simulated fetchInstances failure") }
        failing.join()
        assertTrue(failing.isCancelled, "failing child must have run and failed")
        assertFalse(rootJob.isCancelled, "scope must not be cancelled by a failing child")
    }

    @Test
    fun `a later fetch still runs after a sibling failure`() = runBlocking {
        val handler = CoroutineExceptionHandler { _, _ -> }
        val failing = Invidious.scope.launch(handler) { error("simulated fetchInstances failure") }
        failing.join()
        val ran = CountDownLatch(1)
        Invidious.scope.launch { ran.countDown() }
        assertTrue(ran.await(10, TimeUnit.SECONDS), "later launch must still run after a sibling failure")
    }
}

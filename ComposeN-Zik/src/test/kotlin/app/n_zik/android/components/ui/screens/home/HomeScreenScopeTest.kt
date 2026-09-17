package app.n_zik.android.components.ui.screens.home

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HomeScreenScopeTest {

    @Test
    fun `a failing child does not cancel the shared scope`() = runBlocking {
        val rootJob = requireNotNull(homeScreenScope.coroutineContext[Job]) { "scope must have a root Job" }
        val handler = CoroutineExceptionHandler { _, _ -> }
        val failing = homeScreenScope.launch(handler) { error("simulated back-handler failure") }
        failing.join()
        assertTrue(failing.isCancelled, "failing child must have run and failed")
        assertFalse(rootJob.isCancelled, "scope must not be cancelled by a failing child")
    }

    @Test
    fun `a later launch still runs after a sibling failure`() = runBlocking {
        val handler = CoroutineExceptionHandler { _, _ -> }
        val failing = homeScreenScope.launch(handler) { error("simulated back-handler failure") }
        failing.join()
        val ran = CountDownLatch(1)
        homeScreenScope.launch { ran.countDown() }
        assertTrue(ran.await(10, TimeUnit.SECONDS), "later launch must still run after a sibling failure")
    }
}

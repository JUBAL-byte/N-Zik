package app.n_zik.android.utils.coroutines

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber

/**
 * Runs [action] immediately, then again [intervalMs] after each execution finishes (fixed delay,
 * same semantics as `ScheduledExecutorService.scheduleWithFixedDelay`) until the calling
 * coroutine is cancelled.
 *
 * Replaces a dedicated `ScheduledThreadPool`: the loop is bound to the caller's scope, so
 * cancelling that scope (e.g. a Service `onDestroy`) stops it and leaves no thread behind.
 * An exception thrown by [action] is logged and does not stop the loop.
 */
suspend fun runPeriodically(intervalMs: Long, action: () -> Unit) {
    while (currentCoroutineContext().isActive) {
        try {
            action()
        } catch (e: Exception) {
            Timber.tag("PeriodicRunner").e(e, "Periodic action failed")
        }
        delay(intervalMs)
    }
}

package app.n_zik.android.core.coil

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Issue #606 M4 — the "clear image cache" confirmation in `DataSettings` used to call
 * `ImageCacheFactory.clearImageCache()` (which deletes the disk cache folder) on Main. It now goes
 * through [clearImageCacheOffMain], which must run the purge exactly once, off the caller thread.
 */
class ImageCacheOffMainTest {

    @Test
    fun `clearImageCacheOffMain runs the purge exactly once off the caller thread`() = runBlocking {
        val callerThread = Thread.currentThread()
        val threads = mutableListOf<Thread>()

        clearImageCacheOffMain { threads += Thread.currentThread() }

        assertEquals(1, threads.size)
        assertNotEquals(callerThread, threads.single())
    }

    @Test
    fun `clearImageCacheOffMain returns only after the purge has finished`() = runBlocking {
        var finished = false

        clearImageCacheOffMain {
            Thread.sleep(50)
            finished = true
        }

        // The settings screen bumps its cache counter right after this call: it must see a
        // completed purge, not a still-running one.
        assertEquals(true, finished)
    }
}

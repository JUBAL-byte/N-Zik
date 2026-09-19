package app.n_zik.android.components.dialog.settings

import androidx.media3.datasource.cache.Cache
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Issue #606 H3 — [clearAudioCache] is the full streaming-cache purge extracted from the three
 * settings screens (PreferredStreamClientDialog, StreamClientsSettingsDialog, AccountsSettings),
 * which used to run `cache.keys.forEach { removeResource }` on the Main thread. It must remove
 * every key off the caller's thread, and a null cache must be a safe no-op.
 *
 * Plain JUnit 5 — the media3 [Cache] interface carries no Android framework dependency, so no
 * Robolectric is required.
 */
class ClearAudioCacheOffMainTest {

    @Test
    fun `clearAudioCache removes every key off the caller thread`() = runBlocking {
        val callerThread = Thread.currentThread()
        val cache = mockk<Cache>()
        every { cache.keys } returns setOf("k1", "k2", "k3")
        val threads = CopyOnWriteArrayList<Thread>()
        every { cache.removeResource(any()) } answers {
            threads += Thread.currentThread()
        }

        withContext(NzikDispatchers.DATA) {
            clearAudioCache(cache)
        }

        assertEquals(3, threads.size)
        threads.forEach { assertNotEquals(callerThread, it) }
        verify(exactly = 3) { cache.removeResource(any()) }
    }

    @Test
    fun `clearAudioCache is a safe no-op when the cache is null`() = runBlocking {
        withContext(NzikDispatchers.DATA) {
            clearAudioCache(null)
        }
        // Reaching here without throwing is the assertion.
    }
}

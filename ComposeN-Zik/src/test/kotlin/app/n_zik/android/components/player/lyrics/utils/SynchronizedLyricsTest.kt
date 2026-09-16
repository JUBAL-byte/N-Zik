package app.n_zik.android.components.player.lyrics.utils

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SynchronizedLyricsTest {

    @Test
    fun `index starts at 0 when position is before the first sentence`() {
        val lyrics = SynchronizedLyrics(listOf(1000L to "a", 2000L to "b"))

        assertEquals(0, lyrics.index)
    }

    @Test
    fun `update moves the index forward and reports the change`() = runTest {
        val scheduler = testScheduler
        val lyrics = SynchronizedLyrics(listOf(1000L to "a", 2000L to "b"), StandardTestDispatcher(scheduler))

        assertTrue(lyrics.update(2500L))
        assertEquals(1, lyrics.index)
    }

    @Test
    fun `update reports no change when the active line stays the same`() = runTest {
        val scheduler = testScheduler
        val lyrics = SynchronizedLyrics(listOf(1000L to "a", 2000L to "b"), StandardTestDispatcher(scheduler))

        lyrics.update(1500L)
        assertFalse(lyrics.update(1700L))
        assertEquals(0, lyrics.index)
    }

    @Test
    fun `update keeps the last index when position is past the end`() = runTest {
        val scheduler = testScheduler
        val lyrics = SynchronizedLyrics(listOf(1000L to "a", 2000L to "b"), StandardTestDispatcher(scheduler))

        assertTrue(lyrics.update(99_000L))
        assertEquals(1, lyrics.index)
        assertFalse(lyrics.update(99_500L))
        assertEquals(1, lyrics.index)
    }

    @Test
    fun `scan never produces a negative index`() = runTest {
        val scheduler = testScheduler
        val lyrics = SynchronizedLyrics(listOf(5000L to "late line"), StandardTestDispatcher(scheduler))

        lyrics.update(0L)
        assertEquals(0, lyrics.index)
    }

    @Test
    fun `update applies the scan on the injected dispatcher`() = runTest {
        val tcd = StandardTestDispatcher(testScheduler)
        val lyrics = SynchronizedLyrics(listOf(1000L to "a", 2000L to "b"), tcd)

        val job = launch { lyrics.update(2500L) }

        // The scan runs on the injected dispatcher: the index is untouched until it is dispatched
        assertEquals(0, lyrics.index)
        advanceUntilIdle()
        job.join()
        assertEquals(1, lyrics.index)
    }
}

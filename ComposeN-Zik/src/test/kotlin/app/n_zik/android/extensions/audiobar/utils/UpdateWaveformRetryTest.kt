package app.n_zik.android.extensions.audiobar.utils

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers the bounded retry helper backing WaveformExtractor.updateWaveform (the manual
 * "Update waveform" menu flow). A persistent transient state must eventually be reported as
 * NotReady (not silently swallowed), while a state that resolves before the attempt budget is
 * exhausted must return that resolved result immediately, without spending remaining attempts.
 */
class UpdateWaveformRetryTest {

    @Test
    fun `returns NotReady after exhausting all attempts`() = runTest {
        var calls = 0

        val result = WaveformExtractor.retryUntilReady(maxAttempts = 3, retryDelayMs = 1) {
            calls++
            WaveformResult.NotReady
        }

        assertEquals(WaveformResult.NotReady, result)
        assertEquals(3, calls)
    }

    @Test
    fun `returns Success as soon as an attempt stops being NotReady`() = runTest {
        var calls = 0
        val success = WaveformResult.Success(listOf(1, 2, 3))

        val result = WaveformExtractor.retryUntilReady(maxAttempts = 6, retryDelayMs = 1) {
            calls++
            if (calls < 2) WaveformResult.NotReady else success
        }

        assertEquals(success, result)
        assertEquals(2, calls)
    }

    @Test
    fun `stops immediately on a fatal result without retrying`() = runTest {
        var calls = 0

        val result = WaveformExtractor.retryUntilReady(maxAttempts = 6, retryDelayMs = 1) {
            calls++
            WaveformResult.NoCache
        }

        assertEquals(WaveformResult.NoCache, result)
        assertEquals(1, calls)
    }
}

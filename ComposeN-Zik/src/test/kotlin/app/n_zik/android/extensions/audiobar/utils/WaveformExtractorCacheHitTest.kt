package app.n_zik.android.extensions.audiobar.utils

import android.content.Context
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class WaveformExtractorCacheHitTest {

    @Test
    fun `existing JSON with enough samples returns Success without touching caches`(@TempDir tempDir: File) = runTest {
        val waveformDir = File(tempDir, "waveforms").apply { mkdirs() }
        val amplitudes = List(150) { it }
        File(waveformDir, "media-1.json").writeText(Gson().toJson(amplitudes))

        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns tempDir

        // caches is intentionally empty: the JSON hit must short-circuit before it is ever read.
        val result = WaveformExtractor.getOrExtractWaveform(context, "media-1", emptyList())

        assertTrue(result is WaveformResult.Success)
        assertEquals(amplitudes, (result as WaveformResult.Success).amplitudes)
    }
}

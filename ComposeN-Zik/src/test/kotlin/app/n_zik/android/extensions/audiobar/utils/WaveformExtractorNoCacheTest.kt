package app.n_zik.android.extensions.audiobar.utils

import android.content.Context
import androidx.media3.datasource.cache.Cache
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.TreeSet

class WaveformExtractorNoCacheTest {

    @Test
    fun `caches with no spans returns NoCache`(@TempDir tempDir: File) = runTest {
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns tempDir

        val emptyCache = mockk<Cache>()
        every { emptyCache.getCachedSpans(any()) } returns TreeSet()

        val result = WaveformExtractor.getOrExtractWaveform(context, "media-2", listOf(emptyCache))

        assertEquals(WaveformResult.NoCache, result)
    }
}

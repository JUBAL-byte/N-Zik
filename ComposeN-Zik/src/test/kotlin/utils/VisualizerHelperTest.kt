package utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

/**
 * Covers issue #606's `VisualizerHelper` snapshot-injection seam: [VisualizerHelper.getFft]/
 * [VisualizerHelper.getWave] must read [VisualizerHelper.snapshotProvider] instead of calling
 * the native `Visualizer` directly, and must fall back safely (with a single warning) when
 * nothing wired the provider.
 *
 * Runs on Robolectric (JUnit4, via the project's junit-vintage-engine) because merely
 * constructing a [VisualizerHelper] touches `android.media.audiofx.Visualizer` statics
 * (`getCaptureSizeRange()`), which throw on a plain JVM unit test without a shadow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [ShadowVisualizerCaptureSizeRange::class])
class VisualizerHelperTest {

    private val capturedWarnings = mutableListOf<String>()

    private val captureTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == android.util.Log.WARN) capturedWarnings += message
        }
    }

    @Before
    fun setUp() {
        VisualizerHelper.resetForTests()
        capturedWarnings.clear()
        Timber.plant(captureTree)
    }

    @After
    fun tearDown() {
        Timber.uproot(captureTree)
        VisualizerHelper.resetForTests()
    }

    @Test
    fun `getFft returns the wired snapshot's fft array, not a native capture`() {
        val sessionId = 42
        val snapshot = VisualizerSnapshot(
            fft = byteArrayOf(1, 2, 3, 4),
            waveform = byteArrayOf(5, 6, 7, 8),
            samplingRate = 44100,
        )
        VisualizerHelper.snapshotProvider = { id -> if (id == sessionId) snapshot else null }

        val result = VisualizerHelper(sessionId).getFft()

        assertSame(
            "getFft() must hand back the exact snapshot array, never a fresh native capture",
            snapshot.fft,
            result,
        )
        assertTrue("no fallback warning expected once snapshotProvider is wired", capturedWarnings.isEmpty())
    }

    @Test
    fun `getWave returns the wired snapshot's waveform array, not a native capture`() {
        val sessionId = 7
        val snapshot = VisualizerSnapshot(
            fft = byteArrayOf(0),
            waveform = byteArrayOf(9, 9, 9),
            samplingRate = 44100,
        )
        VisualizerHelper.snapshotProvider = { snapshot }

        val result = VisualizerHelper(sessionId).getWave()

        assertSame(snapshot.waveform, result)
        assertTrue(capturedWarnings.isEmpty())
    }

    @Test
    fun `two independent helper instances on the same session observe the same snapshot instance`() {
        val sessionId = 1
        val snapshot = VisualizerSnapshot(byteArrayOf(1), byteArrayOf(2), 44100)
        VisualizerHelper.snapshotProvider = { snapshot }

        val helperA = VisualizerHelper(sessionId)
        val helperB = VisualizerHelper(sessionId)

        assertSame(
            "SeekBarVisualizer and VisualizerView must read the exact same immutable snapshot",
            helperA.getFft(),
            helperB.getFft(),
        )
    }

    @Test
    fun `getFft falls back to native capture and warns exactly once when snapshotProvider is unset`() {
        // Negative sessionId short-circuits VisualizerHelper.getSharedVisualizer() to null,
        // so the fallback exercises real production code without touching a real Visualizer.
        val helper = VisualizerHelper(-1)

        helper.getFft()
        helper.getFft()
        helper.getFft()

        assertEquals(
            "fallback must be logged once, not once per call",
            1,
            capturedWarnings.size,
        )
    }

    @Test
    fun `getWave falls back to native capture without throwing when snapshotProvider is unset`() {
        val helper = VisualizerHelper(-1)

        val result = helper.getWave()

        assertEquals(
            "no data source means the buffer stays untouched",
            0,
            result.count { it.toInt() != 0 },
        )
        assertEquals(1, capturedWarnings.size)
    }

    @Test
    fun `fallback warning latch is shared across getFft and getWave`() {
        val helper = VisualizerHelper(-1)

        helper.getFft()
        helper.getWave()

        assertEquals(1, capturedWarnings.size)
    }
}

package app.n_zik.android.widget

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #606 M4 — `NZikWidgetManager.updateWidgets()` now wraps palette extraction and bitmap
 * work (scaling, rounding, circling) in `withContext(NzikDispatchers.MEDIA)` instead of running
 * inline on whatever dispatcher the caller used (`Dispatchers.Main` for the idle widget path via
 * the `AppWidgetProvider`s, raw `Dispatchers.IO` for the playing path via
 * `PlayerServiceModern.updateWidgets()`). `extractPalette` itself (widened from `private` to
 * `internal` purely for testability) is untouched, so this test only asserts the offload: the
 * same bitmap yields the same palette whether called directly or dispatched to MEDIA, and the
 * dispatched call actually runs on a `nzik-media-*` thread.
 *
 * Robolectric is required (not a plain JVM unit test) because [NZikWidgetManager.extractPalette]
 * reads `Context.resources.configuration` and `Context.getSharedPreferences`, which need a real
 * Android environment to shadow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NZikWidgetManagerExtractPaletteOffMainTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun solidBitmap(color: Int, size: Int = 16): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { color }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /**
     * Records which thread called [getSharedPreferences] on this context, then delegates to the
     * real base context. `extractPalette` reads shared prefs synchronously as its first `Context`
     * access, so wrapping the context passed into the real, untouched production entry point
     * (`updateWidgets`) with this observes the actual thread `updateWidgets`' own
     * `withContext(NzikDispatchers.MEDIA)` block dispatches to -- unlike calling `extractPalette`
     * directly under a test-written `withContext`, which would stay green even if production
     * dropped its offload.
     */
    private class ThreadProbingContext(base: Context) : ContextWrapper(base) {
        @Volatile
        var sharedPreferencesThreadName: String? = null

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            sharedPreferencesThreadName = Thread.currentThread().name
            return super.getSharedPreferences(name, mode)
        }
    }

    @Test
    fun `updateWidgets dispatches its internal work to a nzik-media thread, not the caller's thread`() = runBlocking {
        val probeContext = ThreadProbingContext(context)
        val callerThreadName = Thread.currentThread().name
        val bitmap = solidBitmap(Color.rgb(50, 150, 250))

        // No widgets registered under Robolectric -> both internal `updateAppWidget` loops are
        // skipped, but palette extraction (and thus the `getSharedPreferences` probe) runs
        // unconditionally before that check, from inside the real `withContext(MEDIA)` block.
        NZikWidgetManager.updateWidgets(
            context = probeContext,
            title = "Title",
            artist = "Artist",
            artworkBitmap = bitmap,
            isPlaying = false,
            isLiked = false,
        )

        val threadName = probeContext.sharedPreferencesThreadName
        assertTrue("expected extractPalette's getSharedPreferences call to have been observed", threadName != null)
        assertNotEquals(
            "updateWidgets' internal work must not run on the caller's (UI-simulating) thread",
            callerThreadName,
            threadName
        )
        assertTrue(
            "expected nzik-media-* but was $threadName",
            threadName!!.startsWith("nzik-media-")
        )
    }

    @Test
    fun `extractPalette dispatched to MEDIA returns the same result as calling it directly`() = runBlocking {
        val bitmap = solidBitmap(Color.rgb(180, 90, 40))

        val direct = NZikWidgetManager.extractPalette(context, bitmap)
        val offloaded = withContext(NzikDispatchers.MEDIA) { NZikWidgetManager.extractPalette(context, bitmap) }

        assertEquals(direct, offloaded)
    }

    @Test
    fun `extractPalette runs on a nzik-media thread when dispatched to MEDIA, not the caller's thread`() = runBlocking {
        val bitmap = solidBitmap(Color.rgb(20, 140, 210))
        val callerThreadName = Thread.currentThread().name

        val executionThreadName = withContext(NzikDispatchers.MEDIA) {
            NZikWidgetManager.extractPalette(context, bitmap)
            Thread.currentThread().name
        }

        assertNotEquals(
            "extractPalette must not run on the caller's (UI-simulating) thread once dispatched to MEDIA",
            callerThreadName,
            executionThreadName
        )
        assertTrue(
            "expected nzik-media-* but was $executionThreadName",
            executionThreadName.startsWith("nzik-media-")
        )
    }

    @Test
    fun `extractPalette dispatched to MEDIA with null bitmap returns the default palette, same as direct call`() = runBlocking {
        val direct = NZikWidgetManager.extractPalette(context, null)
        val offloaded = withContext(NzikDispatchers.MEDIA) { NZikWidgetManager.extractPalette(context, null) }

        assertEquals(direct, offloaded)
    }
}

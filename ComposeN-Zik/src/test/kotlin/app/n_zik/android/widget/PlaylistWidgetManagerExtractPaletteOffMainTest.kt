package app.n_zik.android.widget

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
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
 * Issue #606 M4 — `PlaylistWidgetManager.updateWidgets()`/`updateIdleWidget()` now wrap palette
 * resolution and `RemoteViews` construction in `withContext(NzikDispatchers.MEDIA)` instead of
 * running them inline on whatever dispatcher the caller used. `extractPalette` itself (widened
 * from `private` to `internal` purely for testability) is untouched, so this test only asserts
 * the offload: the same bitmap yields the same palette whether called directly or dispatched to
 * MEDIA, and the dispatched call actually runs on a `nzik-media-*` thread. This matters in
 * particular for `updateIdleWidget` with a real `lastWidgetState.artworkBitmap` already set,
 * where `extractPalette` falls through to the costly `dynamicColorPaletteOf` extraction rather
 * than the cheap saved-preferences shortcut.
 *
 * Robolectric is required (not a plain JVM unit test) because
 * [PlaylistWidgetManager.extractPalette] reads `Context.resources.configuration` and
 * `Context.getSharedPreferences`, which need a real Android environment to shadow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistWidgetManagerExtractPaletteOffMainTest {

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
     * (`updateIdleWidget`) with this observes the actual thread its own
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
    fun `updateIdleWidget dispatches its internal work to a nzik-media thread, not the caller's thread`() = runBlocking {
        val bitmap = solidBitmap(Color.rgb(80, 40, 220))

        // updateWidgets() sets lastWidgetState as its very first statement, before its own early
        // return (no widgets registered under Robolectric) -- this is enough to populate the
        // state updateIdleWidget() needs, without going through its own withContext(MEDIA) block.
        PlaylistWidgetManager.updateWidgets(
            context = context,
            title = "Title",
            artist = "Artist",
            artworkBitmap = bitmap,
            isPlaying = false,
            isLiked = false,
        )

        val probeContext = ThreadProbingContext(context)
        val callerThreadName = Thread.currentThread().name

        try {
            // With lastWidgetState now populated, this reaches the real withContext(MEDIA) block:
            // extractPalette() runs (and fires the probe) before createRemoteViews() goes on to
            // build quick picks (DB access, unconfigured in this unit test) and call
            // AppWidgetManager.updateAppWidget for an arbitrary, unregistered widget id. Any
            // failure past the probed point is irrelevant to what this test observes.
            PlaylistWidgetManager.updateIdleWidget(probeContext, appWidgetId = 1, options = Bundle())
        } catch (e: Exception) {
            // Ignored -- see comment above.
        }

        val threadName = probeContext.sharedPreferencesThreadName
        assertTrue("expected extractPalette's getSharedPreferences call to have been observed", threadName != null)
        assertNotEquals(
            "updateIdleWidget's internal work must not run on the caller's (UI-simulating) thread",
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
        val bitmap = solidBitmap(Color.rgb(90, 200, 90))

        val direct = PlaylistWidgetManager.extractPalette(context, bitmap)
        val offloaded = withContext(NzikDispatchers.MEDIA) { PlaylistWidgetManager.extractPalette(context, bitmap) }

        assertEquals(direct, offloaded)
    }

    @Test
    fun `extractPalette runs on a nzik-media thread when dispatched to MEDIA, not the caller's thread`() = runBlocking {
        val bitmap = solidBitmap(Color.rgb(210, 30, 130))
        val callerThreadName = Thread.currentThread().name

        val executionThreadName = withContext(NzikDispatchers.MEDIA) {
            PlaylistWidgetManager.extractPalette(context, bitmap)
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
        val direct = PlaylistWidgetManager.extractPalette(context, null)
        val offloaded = withContext(NzikDispatchers.MEDIA) { PlaylistWidgetManager.extractPalette(context, null) }

        assertEquals(direct, offloaded)
    }
}

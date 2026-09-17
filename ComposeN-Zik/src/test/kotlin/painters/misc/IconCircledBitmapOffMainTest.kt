package painters.misc

import android.graphics.Bitmap
import android.graphics.Color
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #606 M5 — `NextVisualizer.kt` now wraps `Icon.getCircledBitmap(...)` in
 * `withContext(NzikDispatchers.MEDIA)` instead of running it inline on Main, in both call sites
 * (the artwork `LaunchedEffect` and the `onMediaItemTransition` listener). `getCircledBitmap`
 * itself (`modules/nextvisualizer/.../painters/misc/Icon.kt`) is a pure off-screen
 * `Bitmap`/`Canvas` transform with no thread affinity, so the offload must change neither its
 * output nor correctness — only which thread runs it.
 *
 * Robolectric is required (not a plain JVM unit test) because `Bitmap`/`Canvas`/`PorterDuffXfermode`
 * need real pixel backing to assert on produced pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IconCircledBitmapOffMainTest {

    private fun solidBitmap(color: Int, size: Int = 8): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { color }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    private fun pixelsOf(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }

    @Test
    fun `getCircledBitmap dispatched to MEDIA produces the same pixels as calling it directly`() = runBlocking {
        val source = solidBitmap(Color.RED)

        val direct = Icon.getCircledBitmap(source)
        val offloaded = withContext(NzikDispatchers.MEDIA) { Icon.getCircledBitmap(source) }

        assertArrayEquals(pixelsOf(direct), pixelsOf(offloaded))
    }

    @Test
    fun `getCircledBitmap runs on a nzik-media thread when dispatched to MEDIA, not the caller's thread`() = runBlocking {
        val source = solidBitmap(Color.BLUE)
        val callerThreadName = Thread.currentThread().name

        val executionThreadName = withContext(NzikDispatchers.MEDIA) {
            Icon.getCircledBitmap(source)
            Thread.currentThread().name
        }

        assertNotEquals(
            "getCircledBitmap must not run on the caller's (UI-simulating) thread once dispatched to MEDIA",
            callerThreadName,
            executionThreadName
        )
        assertTrue(
            "expected nzik-media-* but was $executionThreadName",
            executionThreadName.startsWith("nzik-media-")
        )
    }
}

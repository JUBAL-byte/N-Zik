package app.n_zik.android.legacyoffmain.player

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import app.it.fast4x.rimusic.ui.screens.player.computePlayerDynamicPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
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
 * Issue #606 H10 (Lot 2, AD-4 tier 2 legacy in-place patch) -- `Player.kt`'s cover-swipe
 * `LaunchedEffect(currentSwipedMediaItem.mediaId, updateBrush)` used to run
 * `dynamicColorPaletteOf(bitmap, ...)`, `Palette.from(bitmap).generate()`, and 6
 * `Palette.get*Color` extractions inline, on whatever dispatcher that effect resumes on (Main).
 * That whole CPU-bound sequence is now extracted, unchanged, to [computePlayerDynamicPalette]
 * (declared `internal` in `Player.kt`, `app.it.fast4x.rimusic.*`, legacy) and dispatched in one
 * `withContext(NzikDispatchers.MEDIA)` call.
 *
 * This test file itself lives under `app.n_zik.android.*` -- not the legacy package -- per the
 * gh-606 Lot 2 spec's "no new file under app.it.fast4x.rimusic.*" boundary; `internal` visibility
 * is module-scoped in Kotlin, not package-scoped, so it still resolves
 * [computePlayerDynamicPalette] from here without needing to share its package.
 *
 * Robolectric is required (not a plain JVM unit test) because `Bitmap`/`Palette` need real pixel
 * backing to generate a deterministic swatch -- the same technique
 * `NZikWidgetManagerExtractPaletteOffMainTest` (Lot 1, M4) uses for an equivalent palette
 * extraction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlayerDynamicPaletteOffMainTest {

    private fun solidBitmap(color: Int, size: Int = 16): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { color }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    @Test
    fun `computePlayerDynamicPalette matches manual dynamicColorPaletteOf and Palette getXColor calls`() = runBlocking {
        val bitmap = solidBitmap(Color.rgb(180, 90, 40))
        val fallback = DefaultDarkColorPalette

        val expectedPalette = dynamicColorPaletteOf(bitmap, false) ?: fallback
        val expectedSwatch = Palette.from(bitmap).generate()

        val result = computePlayerDynamicPalette(bitmap, false, fallback)

        assertEquals(expectedPalette, result.palette)
        assertEquals(expectedSwatch.getDominantColor(expectedPalette.accent.toArgb()), result.dominant)
        assertEquals(expectedSwatch.getVibrantColor(expectedPalette.accent.toArgb()), result.vibrant)
        assertEquals(expectedSwatch.getLightVibrantColor(expectedPalette.accent.toArgb()), result.lightVibrant)
        assertEquals(expectedSwatch.getDarkVibrantColor(expectedPalette.accent.toArgb()), result.darkVibrant)
        assertEquals(expectedSwatch.getMutedColor(expectedPalette.accent.toArgb()), result.muted)
        assertEquals(expectedSwatch.getLightMutedColor(expectedPalette.accent.toArgb()), result.lightMuted)
        assertEquals(expectedSwatch.getDarkMutedColor(expectedPalette.accent.toArgb()), result.darkMuted)
    }

    @Test
    fun `computePlayerDynamicPalette dispatched to MEDIA runs on a nzik-media thread, not the caller's thread`() = runBlocking {
        val bitmap = solidBitmap(Color.rgb(30, 120, 200))
        val fallback = DefaultDarkColorPalette
        val callerThreadName = Thread.currentThread().name

        val executionThreadName = withContext(NzikDispatchers.MEDIA) {
            computePlayerDynamicPalette(bitmap, true, fallback)
            Thread.currentThread().name
        }

        assertNotEquals(
            "computePlayerDynamicPalette must not run on the caller's (UI-simulating) thread once dispatched to MEDIA",
            callerThreadName,
            executionThreadName
        )
        assertTrue(
            "expected nzik-media-* but was $executionThreadName",
            executionThreadName.startsWith("nzik-media-")
        )
    }
}

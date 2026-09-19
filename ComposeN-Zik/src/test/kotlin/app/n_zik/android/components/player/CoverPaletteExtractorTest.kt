package app.n_zik.android.components.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils.colorToHSL
import androidx.palette.graphics.Palette
import app.it.fast4x.rimusic.ui.screens.player.computePlayerDynamicPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.it.fast4x.rimusic.ui.styling.hsl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit coverage for the shared M3E cover-color extractor used by the player background
 * (`CoverColor` / `CoverColorGradient`), the lyrics screen and the visualizer ("cover" color
 * options).
 *
 * Robolectric is required (not a plain JVM unit test) because `Bitmap`/`Palette` need real pixel
 * backing to generate a deterministic swatch -- the same technique
 * `PlayerDynamicPaletteOffMainTest` uses for an equivalent palette extraction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CoverPaletteExtractorTest {

    private fun solidBitmap(color: Int, size: Int = 16): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { color }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /**
     * Two-region bitmap whose dominant and vibrant swatches differ (verified empirically under
     * Robolectric 33): 70% rgb(150, 110, 170) (the dominant region) + 30% rgb(100, 30, 200) (the
     * vibrant region). Solid bitmaps cannot distinguish the dominant/vibrant hue sources, so the
     * hue-source regressions are pinned on this fixture.
     */
    private fun twoRegionBitmap(): Bitmap {
        val size = 32
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { index ->
            if (index < (size * size * 7) / 10) android.graphics.Color.rgb(150, 110, 170)
            else android.graphics.Color.rgb(100, 30, 200)
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    @Test
    fun `extractM3ECoverColors returns the same 7 swatches as computePlayerDynamicPalette`() = runBlocking {
        for (isDark in listOf(true, false)) {
            // Solid blue bitmap: every swatch is the same color, so the parity assertions below
            // are exact (no get*Color fallback is used).
            val bitmap = solidBitmap(android.graphics.Color.rgb(30, 120, 200))

            val colors = extractM3ECoverColors(bitmap, isDark)
            val reference = computePlayerDynamicPalette(bitmap, isDark, DefaultDarkColorPalette)

            assertNotNull("expected 7 swatches for a solid bitmap (isDark=$isDark)", colors)
            assertEquals(reference.dominant, colors!!.dominant)
            assertEquals(reference.vibrant, colors.vibrant)
            assertEquals(reference.lightVibrant, colors.lightVibrant)
            assertEquals(reference.darkVibrant, colors.darkVibrant)
            assertEquals(reference.muted, colors.muted)
            assertEquals(reference.lightMuted, colors.lightMuted)
            assertEquals(reference.darkMuted, colors.darkMuted)
        }
    }

    @Test
    fun `m3eDynamicColorPaletteOf builds the capped palette from the vibrant swatch`() = runBlocking {
        for (isDark in listOf(true, false)) {
            // Solid blue bitmap: every swatch is the same color, so the expected palette is exact.
            val bitmap = solidBitmap(android.graphics.Color.rgb(30, 120, 200))
            val vibrant = Palette.from(bitmap).generate().getVibrantColor(0)
            val vibrantHsl = FloatArray(3)
            colorToHSL(vibrant, vibrantHsl)

            val result = m3eDynamicColorPaletteOf(bitmap, isDark)

            assertNotNull("expected a vibrant-based palette for a solid bitmap (isDark=$isDark)", result)
            assertEquals(dynamicColorPaletteOf(vibrantHsl, isDark), result)
        }
    }

    @Test
    fun `m3eDynamicColorPaletteOf accent keeps the vibrant hue with capped saturation and fixed lightness`() = runBlocking {
        val bitmap = solidBitmap(android.graphics.Color.rgb(200, 30, 30))

        val result = m3eDynamicColorPaletteOf(bitmap, false)

        assertNotNull(result)
        val accent = result!!.accent.hsl
        // rgb(200, 30, 30) -> HSL(0, ~0.739, ~0.451): accent caps saturation at 0.5, fixed lightness 0.5
        assertEquals(0f, accent.hue, 5f)
        assertEquals(0.5f, accent.saturation, 0.01f)
        assertEquals(0.5f, accent.lightness, 0.01f)
    }

    /**
     * Contract for the flat "Match song cover" player background (`Player.kt`,
     * `PlayerBackgroundColors.CoverColor`): its single color is
     * `m3eSaturate(vibrant, lightTheme).m3eDarkenBy(lightTheme)` -- the same expression as the
     * `M3EMorphingCover` shapes' V parameter -- so it renders the cover's vibrant hue, not the
     * dominant (often neutral) one. The expected colors are derived independently from the
     * extracted swatch's own HSL (via [hslToRgb], not via `m3eSaturate`/`m3eDarkenBy`).
     */
    @Test
    fun `flat match song cover background equals the M3E morphing vibrant color`() = runBlocking {
        // Reddish (hue ~0); solid bitmap -> every swatch is the bitmap color.
        val bitmap = solidBitmap(android.graphics.Color.rgb(200, 30, 30))
        val colors = extractM3ECoverColors(bitmap, false)
        assertNotNull("expected swatches for a solid bitmap", colors)

        val swatchHsl = FloatArray(3)
        colorToHSL(colors!!.vibrant, swatchHsl)
        val hue = swatchHsl[0]
        val saturation = swatchHsl[1]
        val lightness = swatchHsl[2]
        // Solid reddish bitmap yields a saturated swatch (S >= 0.1), so the dark-theme
        // saturation boost applies in the expectations below
        assertTrue("expected a saturated swatch for a solid reddish bitmap", saturation >= 0.1f)

        // Dark theme: saturation +0.35 (the swatch is saturated) clamped at 1.0, lightness kept,
        // then RGB x0.5
        val dark = m3eSaturate(colors.vibrant, lightTheme = false).m3eDarkenBy(lightTheme = false)
        val expectedDark = hslToRgb(hue, saturation + 0.35f, lightness, darken = 0.5f)
        assertEquals(expectedDark.red, dark.red, 0.01f)
        assertEquals(expectedDark.green, dark.green, 0.01f)
        assertEquals(expectedDark.blue, dark.blue, 0.01f)

        // Light theme: no saturation boost, lightness raised to at least 0.5, no darkening
        val light = m3eSaturate(colors.vibrant, lightTheme = true).m3eDarkenBy(lightTheme = true)
        val expectedLight = hslToRgb(hue, saturation, lightness.coerceAtLeast(0.5f), darken = 1f)
        assertEquals(expectedLight.red, light.red, 0.01f)
        assertEquals(expectedLight.green, light.green, 0.01f)
        assertEquals(expectedLight.blue, light.blue, 0.01f)
    }

    /** Independent HSL -> RGB reference used to compute expected colors in the tests above. */
    private fun hslToRgb(hue: Float, saturation: Float, lightness: Float, darken: Float): Color {
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs(((hue / 60f) % 2f) - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color((r + m) * darken, (g + m) * darken, (b + m) * darken)
    }

    @Test
    fun `m3eSaturate boosts saturation by 0_35 in dark theme when the input is saturated`() {
        // rgb(200, 30, 30) -> HSL(0, ~0.739, ~0.451); 0.739 + 0.35 > 1, so S is clamped to 1.0
        val result = m3eSaturate(android.graphics.Color.rgb(200, 30, 30), lightTheme = false)

        val hsl = result.hsl
        assertEquals(0f, hsl.hue, 5f)
        assertEquals(1f, hsl.saturation, 0.01f)
        assertEquals(0.451f, hsl.lightness, 0.01f)
    }

    @Test
    fun `m3eSaturate leaves low-saturation colors untouched in dark theme`() {
        // rgb(128, 126, 124) -> HSL(~30, ~0.016, ~0.494); S < 0.1 -> no boost, L unchanged
        val result = m3eSaturate(android.graphics.Color.rgb(128, 126, 124), lightTheme = false)

        val hsl = result.hsl
        assertEquals(0.016f, hsl.saturation, 0.01f)
        assertEquals(0.494f, hsl.lightness, 0.01f)
    }

    @Test
    fun `m3eSaturate forces lightness to at least 0_5 in light theme`() {
        // rgb(20, 60, 120) -> HSL(216, ~0.714, ~0.275); light theme -> S unchanged, L raised to 0.5
        val result = m3eSaturate(android.graphics.Color.rgb(20, 60, 120), lightTheme = true)

        val hsl = result.hsl
        assertEquals(216f, hsl.hue, 5f)
        assertEquals(0.714f, hsl.saturation, 0.01f)
        assertEquals(0.5f, hsl.lightness, 0.01f)
    }

    @Test
    fun `m3eDarkenBy halves RGB in dark theme and keeps the color in light theme`() {
        val input = Color(0.8f, 0.4f, 0.2f, 1f)

        val dark = input.m3eDarkenBy(lightTheme = false)
        // Compose Color stores 8-bit components, so quantization drifts up to 1/255 (~0.004)
        // from the ideal float values (e.g. 0.2f * 0.5f -> 26/255).
        assertEquals(0.4f, dark.red, 0.01f)
        assertEquals(0.2f, dark.green, 0.01f)
        assertEquals(0.1f, dark.blue, 0.01f)
        assertEquals(1f, dark.alpha, 0.001f)

        val light = input.m3eDarkenBy(lightTheme = true)
        assertEquals(input, light)
    }

    @Test
    fun `m3eDynamicColorPaletteOf uses the vibrant swatch hue, not the dominant one`() = runBlocking {
        val bitmap = twoRegionBitmap()
        val swatchPalette = Palette.from(bitmap).generate()
        val vibrant = swatchPalette.getVibrantColor(0)
        val vibrantHsl = FloatArray(3)
        colorToHSL(vibrant, vibrantHsl)

        val legacyDominantBased = dynamicColorPaletteOf(bitmap, false)
        val vibrantBased = dynamicColorPaletteOf(vibrantHsl, false)

        assertNotNull(legacyDominantBased)
        assertNotEquals(
            "fixture must distinguish the hue sources (dominant-based != vibrant-based)",
            legacyDominantBased,
            vibrantBased,
        )

        val result = m3eDynamicColorPaletteOf(bitmap, false)

        assertNotNull(result)
        assertEquals(vibrantBased, result)
    }

    @Test
    fun `m3eCoverBackgroundColor renders the vibrant swatch, not the dominant one`() = runBlocking {
        val bitmap = twoRegionBitmap()
        val colors = extractM3ECoverColors(bitmap, false)
        assertNotNull("expected swatches for a two-region bitmap", colors)

        val vibrant = colors!!.vibrant
        val dominant = colors.dominant
        assertNotEquals(
            "fixture must yield distinct dominant/vibrant swatches",
            m3eSaturate(dominant, lightTheme = false),
            m3eSaturate(vibrant, lightTheme = false),
        )

        assertEquals(
            m3eSaturate(vibrant, lightTheme = false).m3eDarkenBy(lightTheme = false),
            m3eCoverBackgroundColor(vibrant, lightTheme = false),
        )
        assertNotEquals(
            m3eSaturate(dominant, lightTheme = false).m3eDarkenBy(lightTheme = false),
            m3eCoverBackgroundColor(vibrant, lightTheme = false),
        )
    }

    @Test
    fun `m3eCoverForegroundArgb uses the vibrant swatch, not the dominant one`() = runBlocking {
        val bitmap = twoRegionBitmap()
        val colors = extractM3ECoverColors(bitmap, false)
        assertNotNull("expected swatches for a two-region bitmap", colors)

        val vibrant = colors!!.vibrant
        val dominant = colors.dominant
        assertNotEquals(
            "fixture must yield distinct dominant/vibrant swatches",
            m3eSaturate(dominant, lightTheme = false).toArgb(),
            m3eSaturate(vibrant, lightTheme = false).toArgb(),
        )

        assertEquals(
            m3eSaturate(vibrant, lightTheme = false).toArgb(),
            m3eCoverForegroundArgb(vibrant, lightTheme = false),
        )
        assertNotEquals(
            m3eSaturate(dominant, lightTheme = false).toArgb(),
            m3eCoverForegroundArgb(vibrant, lightTheme = false),
        )
    }
}

package app.n_zik.android.components.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils.colorToHSL
import androidx.palette.graphics.Palette
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf

/**
 * The 7 raw swatches of an album cover extracted from the full [Palette]
 * (no `maximumColorCount`, no filter) — the same extraction the `M3EMorphingCover` player
 * background performs in `computePlayerDynamicPalette` (`Player.kt`, legacy).
 *
 * All values are ARGB `Int`s.
 */
data class M3ECoverColors(
    val dominant: Int,
    val vibrant: Int,
    val lightVibrant: Int,
    val darkVibrant: Int,
    val muted: Int,
    val lightMuted: Int,
    val darkMuted: Int,
)

/**
 * Extracts the 7 M3E morphing cover swatches from [bitmap].
 *
 * Identical extraction to `computePlayerDynamicPalette`: `Palette.from(bitmap).generate()`
 * (full palette, no `maximumColorCount`, no filter) with each `get*Color` falling back to the
 * dynamic palette's accent.
 *
 * @param bitmap the cover bitmap
 * @param isDark whether the extraction targets a dark theme
 * @return the 7 swatches, or `null` when `dynamicColorPaletteOf` cannot derive a dominant
 *  swatch from [bitmap]
 */
suspend fun extractM3ECoverColors(bitmap: Bitmap, isDark: Boolean): M3ECoverColors? {
    val palette = dynamicColorPaletteOf(bitmap, isDark) ?: return null
    val swatchPalette = Palette.from(bitmap).generate()
    val fallback = palette.accent.toArgb()
    return M3ECoverColors(
        dominant = swatchPalette.getDominantColor(fallback),
        vibrant = swatchPalette.getVibrantColor(fallback),
        lightVibrant = swatchPalette.getLightVibrantColor(fallback),
        darkVibrant = swatchPalette.getDarkVibrantColor(fallback),
        muted = swatchPalette.getMutedColor(fallback),
        lightMuted = swatchPalette.getLightMutedColor(fallback),
        darkMuted = swatchPalette.getDarkMutedColor(fallback),
    )
}

/**
 * Builds the dynamic [ColorPalette] consumed by the mini-player and the app-wide dynamic theme,
 * from the M3E cover extraction: the HSL of the vibrant swatch (identical extraction to
 * [extractM3ECoverColors]) is injected into the existing capped construction
 * [dynamicColorPaletteOf] -- same saturation caps (<=0.1/0.3/0.4/0.5) and fixed lightnesses.
 * Unlike the legacy dominant-based path, it reads the vibrant swatch's HSL directly (no 8-color
 * cap, no low-saturation rescue).
 *
 * @param bitmap the cover bitmap
 * @param isDark whether the palette targets a dark theme
 * @return the vibrant-based dynamic palette, or `null` when the bitmap yields no dominant swatch
 */
suspend fun m3eDynamicColorPaletteOf(bitmap: Bitmap, isDark: Boolean): ColorPalette? {
    val colors = extractM3ECoverColors(bitmap, isDark) ?: return null
    val vibrantHsl = FloatArray(3)
    colorToHSL(colors.vibrant, vibrantHsl)
    return dynamicColorPaletteOf(vibrantHsl, isDark)
}

/**
 * The flat "Match song cover" player background color (`Player.kt`, `CoverColor`): the same
 * expression as the `M3EMorphingCover` shapes' vibrant parameter, so both render the cover's
 * vibrant hue.
 *
 * @param vibrant the vibrant swatch as an ARGB `Int` (see [M3ECoverColors.vibrant])
 * @param lightTheme whether the current theme is light
 */
fun m3eCoverBackgroundColor(vibrant: Int, lightTheme: Boolean): Color =
    m3eSaturate(vibrant, lightTheme).m3eDarkenBy(lightTheme)

/**
 * The "cover" foreground color used by the lyrics screen and the visualizer when their cover
 * color option is selected: the vibrant swatch with the same saturate transformation as the
 * player background -- `m3eSaturate(vibrant).toArgb()`.
 *
 * @param vibrant the vibrant swatch as an ARGB `Int` (see [M3ECoverColors.vibrant])
 * @param lightTheme whether the current theme is light
 */
fun m3eCoverForegroundArgb(vibrant: Int, lightTheme: Boolean): Int =
    m3eSaturate(vibrant, lightTheme).toArgb()

/**
 * Pure (non-composable) copy of `Player.saturate()`: adds 0.35 to the saturation in dark theme
 * when the input saturation is at least 0.1 (clamped to [0, 1]), and forces the lightness to at
 * least 0.5 in light theme.
 *
 * @param color the input color as an ARGB `Int`
 * @param lightTheme whether the current theme is light
 */
fun m3eSaturate(color: Int, lightTheme: Boolean): Color {
    val hsl = FloatArray(3)
    colorToHSL(color, hsl)
    hsl[1] = (hsl[1] + if (lightTheme || hsl[1] < 0.1f) 0f else 0.35f).coerceIn(0f, 1f)
    hsl[2] = if (lightTheme) hsl[2].coerceIn(0.5f, 1f) else hsl[2]
    return Color.hsl(hsl[0], hsl[1], hsl[2])
}

/**
 * Pure (non-composable) copy of `Player.Color.darkenBy()`: multiplies RGB by 0.5 in dark theme
 * and leaves the color untouched in light theme.
 *
 * @param lightTheme whether the current theme is light
 */
fun Color.m3eDarkenBy(lightTheme: Boolean): Color {
    val ratio = if (lightTheme) 1f else 0.5f
    return copy(
        red = red * ratio,
        green = green * ratio,
        blue = blue * ratio,
        alpha = alpha,
    )
}

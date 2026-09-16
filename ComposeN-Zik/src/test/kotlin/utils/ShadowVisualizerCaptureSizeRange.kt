package utils

import android.media.audiofx.Visualizer
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Test-only Robolectric shadow.
 *
 * Robolectric's built-in `ShadowVisualizer` does not implement the static
 * `Visualizer.getCaptureSizeRange()` (it is a raw native stub, not one of the `native_*` methods
 * `ShadowVisualizer` backs), so under plain Robolectric it silently returns `null` and NPEs
 * `VisualizerHelper`'s buffer-sizing field initializers (`ByteArray(Visualizer.getCaptureSizeRange()[1])`).
 * This fills in a fixed, realistic range so [utils.VisualizerHelper] instances can be
 * constructed in tests without touching real audio hardware.
 */
@Implements(Visualizer::class, isInAndroidSdk = false)
class ShadowVisualizerCaptureSizeRange {
    companion object {
        @JvmStatic
        @Implementation
        fun getCaptureSizeRange(): IntArray = intArrayOf(128, 1024)
    }
}

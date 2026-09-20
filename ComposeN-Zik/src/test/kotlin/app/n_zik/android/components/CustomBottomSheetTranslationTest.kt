package app.n_zik.android.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomBottomSheetTranslationTest {

    private val parentHeight = 1000f
    private val collapsedHeight = 72f
    private val bottomPadding = 30f
    private val topPadding = 200f

    private fun bottomAnchored(progress: Float) = sheetTranslationY(
        progress = progress,
        parentHeightPx = parentHeight,
        collapsedHeightPx = collapsedHeight,
        bottomPaddingPx = bottomPadding,
        topPaddingPx = null,
    )

    private fun topAnchored(progress: Float) = sheetTranslationY(
        progress = progress,
        parentHeightPx = parentHeight,
        collapsedHeightPx = collapsedHeight,
        bottomPaddingPx = bottomPadding,
        topPaddingPx = topPadding,
    )

    @Test
    fun `bottom anchor pushes the collapsed sheet down to the bottom edge`() {
        assertEquals(parentHeight - collapsedHeight - bottomPadding, bottomAnchored(0f))
    }

    @Test
    fun `top anchor keeps the collapsed sheet at the top padding`() {
        assertEquals(topPadding, topAnchored(0f))
    }

    @Test
    fun `top anchor deploys from the top padding to full screen`() {
        assertEquals(topPadding / 2f, topAnchored(0.5f))
        assertEquals(0f, topAnchored(1f))
    }

    @Test
    fun `expanded sheet is not translated in either anchor`() {
        assertEquals(0f, bottomAnchored(1f))
        assertEquals(0f, topAnchored(1f))
    }

    @Test
    fun `bottom anchor opens on an upward drag and swipe`() {
        // The sheet state opens on a negative delta and on a positive fling velocity
        assertEquals(-50f, sheetDragDelta(dragAmountPx = -50f, anchoredAtTop = false))
        assertEquals(1000f, sheetFlingVelocity(velocityYPx = -1000f, anchoredAtTop = false))
    }

    @Test
    fun `top anchor opens on a downward drag and swipe`() {
        assertEquals(-50f, sheetDragDelta(dragAmountPx = 50f, anchoredAtTop = true))
        assertEquals(1000f, sheetFlingVelocity(velocityYPx = 1000f, anchoredAtTop = true))
    }

    @Test
    fun `top anchor closes on an upward drag and swipe`() {
        assertEquals(50f, sheetDragDelta(dragAmountPx = -50f, anchoredAtTop = true))
        assertEquals(-1000f, sheetFlingVelocity(velocityYPx = -1000f, anchoredAtTop = true))
    }

    @Test
    fun `progress outside 0 to 1 is clamped`() {
        assertEquals(bottomAnchored(0f), bottomAnchored(-0.4f))
        assertEquals(topAnchored(1f), topAnchored(1.3f))
    }
}

package app.n_zik.android.components.player

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MiniPlayerTopAnchorTest {

    private val statusBar = 24.dp
    private val header = 64.dp
    private val topNavBar = 59.dp
    private val gap = 8.dp

    private fun inset(barsHidden: Boolean, hasTopNavBar: Boolean) = miniPlayerTopInset(
        statusBarTop = statusBar,
        barsHidden = barsHidden,
        hasTopNavBar = hasTopNavBar,
        headerHeight = header,
        topNavBarHeight = topNavBar,
        gap = gap,
    )

    @Test
    fun `sits below the app header with a small gap`() {
        assertEquals(statusBar + header + gap, inset(barsHidden = false, hasTopNavBar = false))
    }

    @Test
    fun `sits below the top navigation bar when there is one`() {
        assertEquals(statusBar + header + topNavBar + gap, inset(barsHidden = false, hasTopNavBar = true))
    }

    @Test
    fun `only clears the status bar while the bars are put away`() {
        assertEquals(statusBar, inset(barsHidden = true, hasTopNavBar = true))
    }

    @Test
    fun `stays at its inset while the header is shown`() {
        assertEquals(
            288f,
            miniPlayerTopPaddingPx(insetPx = 288f, scrollOffsetPx = 0f, hideRangePx = 120f, collapsedHeightPx = 200f),
        )
    }

    @Test
    fun `leaves with the header, fully above the screen once it is gone`() {
        assertEquals(
            -200f,
            miniPlayerTopPaddingPx(insetPx = 288f, scrollOffsetPx = -120f, hideRangePx = 120f, collapsedHeightPx = 200f),
        )
    }

    @Test
    fun `moves proportionally while the header slides out`() {
        assertEquals(
            44f,
            miniPlayerTopPaddingPx(insetPx = 288f, scrollOffsetPx = -60f, hideRangePx = 120f, collapsedHeightPx = 200f),
        )
    }

    @Test
    fun `an offset beyond the header travel does not push it further`() {
        assertEquals(
            -200f,
            miniPlayerTopPaddingPx(insetPx = 288f, scrollOffsetPx = -500f, hideRangePx = 120f, collapsedHeightPx = 200f),
        )
    }

    @Test
    fun `without a hide range it stays at its inset`() {
        assertEquals(
            288f,
            miniPlayerTopPaddingPx(insetPx = 288f, scrollOffsetPx = -60f, hideRangePx = 0f, collapsedHeightPx = 200f),
        )
    }
}

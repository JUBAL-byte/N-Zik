package app.n_zik.android.components

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DominantVerticalDragTest {

    @Test
    fun `a straight vertical drag counts, up or down`() {
        assertTrue(isMostlyVerticalDrag(Offset(0f, 12f)))
        assertTrue(isMostlyVerticalDrag(Offset(0f, -12f)))
    }

    @Test
    fun `a drag with a small horizontal drift still counts`() {
        assertTrue(isMostlyVerticalDrag(Offset(3f, 12f)))
        assertTrue(isMostlyVerticalDrag(Offset(-3f, -12f)))
    }

    @Test
    fun `a straight horizontal swipe does not count`() {
        assertFalse(isMostlyVerticalDrag(Offset(12f, 0f)))
        assertFalse(isMostlyVerticalDrag(Offset(-12f, 0f)))
    }

    @Test
    fun `a mainly horizontal swipe that drifts up or down does not open or close the sheet`() {
        assertFalse(isMostlyVerticalDrag(Offset(12f, 4f)))
        assertFalse(isMostlyVerticalDrag(Offset(-12f, -4f)))
    }

    @Test
    fun `a perfect diagonal counts as vertical, so the sheet still follows the finger`() {
        assertTrue(isMostlyVerticalDrag(Offset(8f, 8f)))
    }

    @Test
    fun `no movement is not a vertical drag`() {
        assertFalse(isMostlyVerticalDrag(Offset.Zero))
    }
}

class OngoingDragDirectionMonitorTest {

    private val slop = 24f

    @Test
    fun `opening then sliding sideways stops the follow once the sideways slide covers a window`() {
        val monitor = OngoingDragDirectionMonitor(recheckDistance = slop)
        assertFalse(monitor.turnsHorizontal(0f, -15f))
        assertFalse(monitor.turnsHorizontal(1f, -1f))
        assertFalse(monitor.turnsHorizontal(8f, 0f))
        assertFalse(monitor.turnsHorizontal(9f, 0f))
        assertFalse(monitor.turnsHorizontal(9f, 0f))
        assertTrue(monitor.turnsHorizontal(9f, 0f))
    }

    @Test
    fun `a wobbly but vertical drag keeps following`() {
        val monitor = OngoingDragDirectionMonitor(recheckDistance = slop)
        assertFalse(monitor.turnsHorizontal(6f, -12f))
        assertFalse(monitor.turnsHorizontal(-5f, -14f))
        assertFalse(monitor.turnsHorizontal(7f, -10f))
        assertFalse(monitor.turnsHorizontal(-6f, -13f))
        assertFalse(monitor.turnsHorizontal(4f, -9f))
    }

    @Test
    fun `a perfect diagonal keeps following, like the start-of-gesture rule`() {
        val monitor = OngoingDragDirectionMonitor(recheckDistance = slop)
        assertFalse(monitor.turnsHorizontal(12f, 12f))
        assertFalse(monitor.turnsHorizontal(12f, 12f))
    }

    @Test
    fun `a straight sideways slide turns horizontal as soon as the window fills`() {
        val monitor = OngoingDragDirectionMonitor(recheckDistance = slop)
        assertFalse(monitor.turnsHorizontal(12f, 0f))
        assertTrue(monitor.turnsHorizontal(12f, 0f))
    }

    @Test
    fun `movement below the recheck distance is never judged`() {
        val monitor = OngoingDragDirectionMonitor(recheckDistance = slop)
        assertFalse(monitor.turnsHorizontal(10f, 0f))
        assertFalse(monitor.turnsHorizontal(12f, 0f))
    }
}

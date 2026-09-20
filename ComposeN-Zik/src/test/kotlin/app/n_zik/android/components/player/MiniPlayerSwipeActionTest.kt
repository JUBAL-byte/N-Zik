package app.n_zik.android.components.player

import androidx.compose.material3.SwipeToDismissBoxValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiniPlayerSwipeActionTest {

    @Test
    fun `right swipe likes the song on the Essential mini-player`() {
        assertEquals(
            MiniPlayerSwipeAction.LikeCurrentSong,
            miniPlayerSwipeAction(SwipeToDismissBoxValue.StartToEnd, isEssentialType = true),
        )
    }

    @Test
    fun `right swipe goes back on the other mini-player types`() {
        assertEquals(
            MiniPlayerSwipeAction.SeekToPrevious,
            miniPlayerSwipeAction(SwipeToDismissBoxValue.StartToEnd, isEssentialType = false),
        )
    }

    @Test
    fun `left swipe skips to the next song whatever the type`() {
        assertEquals(
            MiniPlayerSwipeAction.SeekToNext,
            miniPlayerSwipeAction(SwipeToDismissBoxValue.EndToStart, isEssentialType = true),
        )
        assertEquals(
            MiniPlayerSwipeAction.SeekToNext,
            miniPlayerSwipeAction(SwipeToDismissBoxValue.EndToStart, isEssentialType = false),
        )
    }

    @Test
    fun `mini-player swipe is on only at its original position and before an action ran`() {
        assertTrue(isMiniPlayerSwipeEnabled(actionFired = false, sheetProgress = 0f))
        assertFalse(isMiniPlayerSwipeEnabled(actionFired = true, sheetProgress = 0f))
    }

    @Test
    fun `mini-player swipe is off as soon as the sheet leaves its original position`() {
        assertFalse(isMiniPlayerSwipeEnabled(actionFired = false, sheetProgress = 0.01f))
        assertFalse(isMiniPlayerSwipeEnabled(actionFired = false, sheetProgress = 1f))
    }

    @Test
    fun `player swipe is on only when the sheet is completely open`() {
        assertTrue(isPlayerHorizontalSwipeEnabled(disabledByUser = false, sheetExpanded = true))
        assertFalse(isPlayerHorizontalSwipeEnabled(disabledByUser = false, sheetExpanded = false))
    }

    @Test
    fun `player swipe stays off when the user disabled it`() {
        assertFalse(isPlayerHorizontalSwipeEnabled(disabledByUser = true, sheetExpanded = true))
        assertFalse(isPlayerHorizontalSwipeEnabled(disabledByUser = true, sheetExpanded = false))
    }

    @Test
    fun `no action while the box is at rest`() {
        assertNull(miniPlayerSwipeAction(SwipeToDismissBoxValue.Settled, isEssentialType = true))
        assertNull(miniPlayerSwipeAction(SwipeToDismissBoxValue.Settled, isEssentialType = false))
    }
}

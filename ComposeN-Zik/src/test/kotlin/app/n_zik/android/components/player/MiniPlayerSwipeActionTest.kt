package app.n_zik.android.components.player

import androidx.compose.material3.SwipeToDismissBoxValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `no action while the box is at rest`() {
        assertNull(miniPlayerSwipeAction(SwipeToDismissBoxValue.Settled, isEssentialType = true))
        assertNull(miniPlayerSwipeAction(SwipeToDismissBoxValue.Settled, isEssentialType = false))
    }
}

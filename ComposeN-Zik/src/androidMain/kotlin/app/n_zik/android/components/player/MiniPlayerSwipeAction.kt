package app.n_zik.android.components.player

import androidx.compose.material3.SwipeToDismissBoxValue

/** What a swipe on the mini-player does. */
enum class MiniPlayerSwipeAction {
    /** Cycles the like state of the song playing when the swipe happens. */
    LikeCurrentSong,
    SeekToPrevious,
    SeekToNext,
}

/**
 * Maps a swipe direction to its action.
 *
 * @param isEssentialType The Essential mini-player has no previous button, so its right swipe
 *        likes the song instead of going back.
 * @return null while the box is at rest.
 */
fun miniPlayerSwipeAction(
    direction: SwipeToDismissBoxValue,
    isEssentialType: Boolean,
): MiniPlayerSwipeAction? = when (direction) {
    SwipeToDismissBoxValue.StartToEnd ->
        if (isEssentialType) MiniPlayerSwipeAction.LikeCurrentSong else MiniPlayerSwipeAction.SeekToPrevious

    SwipeToDismissBoxValue.EndToStart -> MiniPlayerSwipeAction.SeekToNext
    SwipeToDismissBoxValue.Settled -> null
}

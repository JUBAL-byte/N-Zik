package app.n_zik.android.components.player.lyrics.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Tracks the active lyric line for a list of (timestamp, text) pairs.
 *
 * The playback position is passed by value to [update] (read by the caller on the UI thread),
 * never as a provider lambda — the scan and the [index] publication run on [dispatcher]
 * (MEDIA) so no ExoPlayer access happens off the UI thread.
 */
class SynchronizedLyrics(
    val sentences: List<Pair<Long, String>>,
    private val dispatcher: CoroutineDispatcher = NzikDispatchers.MEDIA
) {
    var index by mutableStateOf(scan(0L))
        private set

    private fun scan(positionMs: Long): Int {
        var index = -1
        for (item in sentences) {
            if (item.first >= positionMs) break
            index++
        }
        return if (index == -1) 0 else index
    }

    suspend fun update(positionMs: Long): Boolean {
        return withContext(dispatcher) {
            val newIndex = scan(positionMs)
            if (newIndex != index) {
                index = newIndex
                true
            } else {
                false
            }
        }
    }
}

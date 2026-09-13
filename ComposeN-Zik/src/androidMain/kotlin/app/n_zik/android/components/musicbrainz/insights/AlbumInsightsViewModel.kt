package app.n_zik.android.components.musicbrainz.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.n_zik.android.Dependencies
import app.n_zik.android.core.database.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads the album Insights data: tracks, artist, other albums by the
 * same artist, listening stats and external links.
 */
class AlbumInsightsViewModel(application: Application) : AndroidViewModel(application) {

    companion object : ViewModelProvider.Factory {
        private const val MAX_OTHER_ALBUMS = 20

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlbumInsightsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AlbumInsightsViewModel(Dependencies.application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private val albumTable = Database.albumTable
    private val artistTable = Database.artistTable
    private val eventTable = Database.eventTable
    private val songAlbumMapTable = Database.songAlbumMapTable

    private val _state = MutableStateFlow(AlbumDetailUiState())
    val state: StateFlow<AlbumDetailUiState> = _state.asStateFlow()

    fun loadAlbum(albumId: String) {
        viewModelScope.launch {
            _state.value = AlbumDetailUiState(isLoading = true)

            val album = withContext(Dispatchers.IO) {
                albumTable.findByIdDirect(albumId)
            } ?: return@launch

            val tracks = withContext(Dispatchers.IO) {
                songAlbumMapTable.allSongsOfDirect(albumId)
            }

            val artist = withContext(Dispatchers.IO) {
                val authors = album.authorsText?.trim().orEmpty()
                if (authors.isNotBlank()) artistTable.findByNameDirect(authors) else null
            }

            val otherAlbums = withContext(Dispatchers.IO) {
                val artistName = album.authorsText?.trim().orEmpty()
                if (artistName.isBlank()) {
                    emptyList()
                } else {
                    albumTable.getOtherAlbumsByArtistName(artistName, albumId, limit = MAX_OTHER_ALBUMS)
                }
            }

            val stats = withContext(Dispatchers.IO) {
                val likedSongs = tracks.count { it.likedAt != null }
                val totalPlayTimeMs = eventTable.getAlbumTotalPlayTime(albumId).first()
                val playCount = eventTable.getAlbumPlayCount(albumId).first()
                AlbumStats(totalPlayTimeMs, playCount, likedSongs, tracks.size)
            }

            _state.value = AlbumDetailUiState(
                isLoading = false,
                album = album,
                tracks = tracks,
                artist = artist,
                otherAlbums = otherAlbums,
                externalLinks = album.links.orEmpty(),
                stats = stats
            )
        }
    }
}

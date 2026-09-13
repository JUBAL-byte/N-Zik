package app.n_zik.android.components.musicbrainz.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.it.fast4x.rimusic.models.Artist
import app.n_zik.android.Dependencies
import app.n_zik.android.core.database.Database
import app.n_zik.android.musicbrainz.MusicBrainz
import app.n_zik.android.musicbrainz.models.MBArtistRelationEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads the artist Insights data: albums, top tracks, listening stats,
 * MusicBrainz relations and external links.
 */
class ArtistInsightsViewModel(application: Application) : AndroidViewModel(application) {

    companion object : ViewModelProvider.Factory {
        private val EXCLUDED_RELATION_TYPES =
            setOf("wikipedia", "wikidata", "allmusic", "discogs", "imdb")
        private const val MAX_RELATIONS = 20

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArtistInsightsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ArtistInsightsViewModel(Dependencies.application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private val artistTable = Database.artistTable
    private val albumTable = Database.albumTable
    private val eventTable = Database.eventTable
    private val mbClient = MusicBrainz()

    private val _state = MutableStateFlow(ArtistDetailUiState())
    val state: StateFlow<ArtistDetailUiState> = _state.asStateFlow()

    fun loadArtist(artistId: String) {
        viewModelScope.launch {
            _state.value = ArtistDetailUiState(isLoading = true)

            val artist = withContext(Dispatchers.IO) {
                artistTable.findByIdDirect(artistId)
            } ?: return@launch

            val albums = withContext(Dispatchers.IO) {
                artistTable.getAlbumsByArtist(artistId)
            }

            val topTracks = withContext(Dispatchers.IO) {
                artistTable.getTopSongsByArtist(artistId, limit = 5)
            }

            val relations = withContext(Dispatchers.IO) {
                fetchRelations(artist)
            }

            val stats = withContext(Dispatchers.IO) {
                val likedSongs = artistTable.getTopSongsByArtist(artistId, limit = 1000)
                    .count { it.likedAt != null }
                val totalPlayTimeMs = eventTable.getArtistTotalPlayTime(artistId).first()
                val playCount = eventTable.getArtistPlayCount(artistId).first()
                ArtistStats(totalPlayTimeMs, playCount, likedSongs, albums.size)
            }

            _state.value = ArtistDetailUiState(
                isLoading = false,
                artist = artist,
                albums = albums,
                topTracks = topTracks,
                relations = relations,
                externalLinks = artist.links.orEmpty(),
                stats = stats
            )
        }
    }

    /**
     * Fetches artist relations from MusicBrainz on demand and resolves
     * the targets against the local database (falling back to the raw
     * MusicBrainz name when the artist is not in the library).
     */
    private suspend fun fetchRelations(artist: Artist): List<ArtistRelationInfo> {
        val mbId = artist.mbId?.takeIf { it.isNotBlank() } ?: return emptyList()

        return runCatching {
            mbClient.fetchArtistRelations(mbId)
        }.getOrDefault(emptyList())
            .filter { entry ->
                (entry.type ?: "").lowercase() !in EXCLUDED_RELATION_TYPES
            }
            .mapNotNull { entry ->
                val target = entry.artist ?: return@mapNotNull null
                val resolved = runCatching {
                    artistTable.getByMbId(target.id)
                }.getOrNull()
                if (resolved != null) {
                    ArtistRelationInfo(
                        resolved,
                        entry.type.orEmpty(),
                        entry.direction.orEmpty(),
                        localArtistId = resolved.id
                    )
                } else {
                    ArtistRelationInfo(
                        Artist(id = target.id, name = target.name),
                        entry.type.orEmpty(),
                        entry.direction.orEmpty(),
                        localArtistId = null
                    )
                }
            }
            .take(MAX_RELATIONS)
    }
}

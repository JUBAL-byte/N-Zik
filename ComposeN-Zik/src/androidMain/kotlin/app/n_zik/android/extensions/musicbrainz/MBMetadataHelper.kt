package app.n_zik.android.extensions.musicbrainz

import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.musicbrainz.MusicBrainz
import timber.log.Timber

/**
 * Fetches and stores MusicBrainz metadata for artists and albums.
 *
 * Intended to be called from a background coroutine (Dispatchers.IO)
 * when the user views an artist/album page, and from the backfill worker.
 */
class MBMetadataHelper(
    private val mbClient: MusicBrainz = MusicBrainz(),
    private val artistTable: ArtistTable = Database.artistTable,
    private val albumTable: AlbumTable = Database.albumTable
) {

    companion object {
        val Default by lazy { MBMetadataHelper() }
    }

    private val TAG = "MBMetadataHelper"

    suspend fun onArtistViewed(artistId: String) {
        val artist = artistTable.findByIdDirect(artistId) ?: return

        // genres/tags are always written after a fetch cycle (even when the
        // MusicBrainz search finds no match), so they act as the fetched sentinel
        if (artist.genres != null && artist.tags != null) return

        val artistName = artist.name?.takeIf { it.isNotBlank() } ?: return

        try {
            var metadata = mbClient.fetchArtistMetadata(artistName)

            // No Wikipedia link on MusicBrainz -> try a direct Wikipedia search
            if (metadata.wikipediaUrl == null && metadata.wikipediaBio == null) {
                val searchTerm = when (artist.artistType ?: metadata.artistType) {
                    "Group" -> "$artistName band"
                    "Person" -> "$artistName singer"
                    else -> artistName
                }
                mbClient.fetchWikipediaExtractByArtist(searchTerm)?.let { wiki ->
                    metadata = metadata.copy(wikipediaBio = wiki.info, wikipediaUrl = wiki.url)
                }
            }

            artistTable.update(
                artist.copy(
                    genres = metadata.genres.ifEmpty { emptyList() },
                    artistType = metadata.artistType ?: artist.artistType,
                    countryCode = metadata.countryCode ?: artist.countryCode,
                    beginYear = metadata.beginYear ?: artist.beginYear,
                    tags = metadata.topTags.ifEmpty { emptyList() },
                    rating = metadata.ratingValue ?: artist.rating,
                    ratingVotes = metadata.ratingVotes ?: artist.ratingVotes,
                    wikipediaUrl = metadata.wikipediaUrl ?: artist.wikipediaUrl,
                    wikipediaBio = metadata.wikipediaBio ?: artist.wikipediaBio,
                    disambiguation = metadata.disambiguation ?: artist.disambiguation,
                    links = metadata.links ?: artist.links,
                    mbId = metadata.mbId ?: artist.mbId
                )
            )
            Timber.tag(TAG).d("Artist metadata stored for ${artist.name}")
        } catch (e: Exception) {
            // Network error: keep fields null so the next view retries
            Timber.tag(TAG).e(e, "Failed to fetch artist metadata for ${artist.name}")
        }
    }

    suspend fun onAlbumViewed(albumId: String) {
        val album = albumTable.findByIdDirect(albumId) ?: return

        // genres/tags are always written after a fetch cycle (even when the
        // MusicBrainz search finds no match), so they act as the fetched sentinel
        if (album.genres != null && album.tags != null) return

        val albumTitle = album.title?.takeIf { it.isNotBlank() } ?: return
        val albumAuthor = album.authorsText?.takeIf { it.isNotBlank() } ?: return

        val artist = artistTable.findByNameDirect(albumAuthor) ?: return

        try {
            var metadata = mbClient.fetchAlbumMetadata(albumTitle, artist.name.orEmpty())

            if (metadata.wikipediaUrl == null && metadata.wikipediaInfo == null) {
                mbClient.fetchWikipediaExtractByArtist("$albumTitle $albumAuthor album")
                    ?.let { wiki ->
                        metadata = metadata.copy(wikipediaInfo = wiki.info, wikipediaUrl = wiki.url)
                    }
            }

            // Fallback: inherit the artist's genres when the album has none
            val finalGenres = metadata.genres.ifEmpty {
                artist.genres ?: run {
                    artist.name?.let { mbClient.fetchArtistMetadata(it).genres } ?: emptyList()
                }
            }

            albumTable.updateReplace(
                album.copy(
                    genres = finalGenres.ifEmpty { emptyList() },
                    albumType = metadata.albumType ?: album.albumType,
                    originalYear = metadata.originalYear ?: album.originalYear,
                    tags = metadata.topTags.ifEmpty { emptyList() },
                    rating = metadata.ratingValue ?: album.rating,
                    ratingVotes = metadata.ratingVotes ?: album.ratingVotes,
                    wikipediaUrl = metadata.wikipediaUrl ?: album.wikipediaUrl,
                    wikipediaInfo = metadata.wikipediaInfo ?: album.wikipediaInfo,
                    links = metadata.links ?: album.links,
                    mbId = metadata.mbId ?: album.mbId
                )
            )
            Timber.tag(TAG).d("Album metadata stored for ${album.title}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to fetch album metadata for ${album.title}")
        }
    }
}

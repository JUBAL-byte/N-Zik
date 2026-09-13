package app.it.fast4x.rimusic.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.musicbrainz.models.ExternalLink

@Immutable
@Entity
data class Album(
    @PrimaryKey val id: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val year: String? = null,
    val authorsText: String? = null,
    val shareUrl: String? = null,
    val timestamp: Long? = null,
    val bookmarkedAt: Long? = null,
    val isYoutubeAlbum: Boolean = false,
    val position: Int = -1,
    val lastFetch: Long? = null,
    val dislikedAt: Long? = null,
    val genres: List<String>? = null,
    val originalYear: Int? = null,
    val albumType: String? = null,
    val tags: List<String>? = null,
    val rating: Float? = null,
    val ratingVotes: Int? = null,
    val wikipediaUrl: String? = null,
    val wikipediaInfo: String? = null,
    val links: List<ExternalLink>? = null,
    val mbId: String? = null,
    val youtubeAlbumId: String? = null
) {
    fun toggleBookmark(): Album {
        return copy(
            bookmarkedAt = if (bookmarkedAt == null) System.currentTimeMillis() else null
        )
    }

    val isDisliked: Boolean
        get() = dislikedAt != null

    fun cleanTitle() = cleanPrefix( this.title ?: "" )

    fun cleanAuthorsText() = cleanPrefix( this.authorsText ?: "" )

    val info: String
        get() = buildList {
            originalYear?.let { add(it.toString()) }
            albumType?.let { add(it) }
        }.joinToString("    ")

    val keywords: List<String>
        get() = (genres.orEmpty() + tags.orEmpty())
            .distinctBy { it.lowercase() }
            .take(8)
}




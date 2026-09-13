package app.it.fast4x.rimusic.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.musicbrainz.models.ExternalLink
import app.n_zik.android.musicbrainz.utils.toFlagEmoji

@Immutable
@Entity
data class Artist(
    @PrimaryKey val id: String,
    val name: String? = null,
    val thumbnailUrl: String? = null,
    val timestamp: Long? = null,
    val bookmarkedAt: Long? = null,
    val isYoutubeArtist: Boolean = false,
    val position: Int = -1,
    val lastFetch: Long? = null,
    val dislikedAt: Long? = null,
    val genres: List<String>? = null,
    val artistType: String? = null,
    val countryCode: String? = null,
    val beginYear: Int? = null,
    val tags: List<String>? = null,
    val rating: Float? = null,
    val ratingVotes: Int? = null,
    val wikipediaUrl: String? = null,
    val wikipediaBio: String? = null,
    val description: String? = null,
    val disambiguation: String? = null,
    val links: List<ExternalLink>? = null,
    val mbId: String? = null,
    val youtubeChannelId: String? = null
) {
    fun cleanName() = cleanPrefix( this.name ?: "" )

    val isDisliked: Boolean
        get() = dislikedAt != null

    val info: String
        get() = buildList {
            beginYear?.let { add(it) }
            countryCode?.let { add(it.toFlagEmoji()) }
        }.joinToString("    ")

    val keywords: List<String>
        get() = (genres.orEmpty() + tags.orEmpty())
            .distinctBy { it.lowercase() }
            .take(8)
}




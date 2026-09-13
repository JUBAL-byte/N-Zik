package app.n_zik.android.musicbrainz.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MBSearchArtistResponse(
    val artists: List<MBArtist> = emptyList()
)

@Serializable
data class MBSearchReleaseGroupResponse(
    // MusicBrainz returns the list with the key "release-groups" (with the dash)
    @SerialName("release-groups")
    val releaseGroups: List<MBReleaseGroupSearchResult> = emptyList()
)

@Serializable
data class MBReleaseGroupSearchResult(
    val id: String,
    val title: String,
    val score: Int
)

@Serializable
data class MBArtist(
    val id: String,
    val name: String,
    val score: Int
)

@Serializable
data class MBLifeSpan(
    val begin: String? = null,
    val end: String? = null,
    val ended: Boolean? = null
)

@Serializable
data class MBRating(
    val value: Float? = null,
    @SerialName("votes-count") val votesCount: Int? = null
)

@Serializable
data class MBRelation(
    val type: String? = null,
    val url: MBRelatedUrl? = null
)

@Serializable
data class MBRelatedUrl(
    val resource: String
)

@Serializable
data class MBTag(
    val name: String,
    val count: Int
)

@Serializable
data class MBGenre(
    val name: String,
    val count: Int
)

@Serializable
data class MBArtistDetailResponse(
    val id: String,
    val name: String,
    val genres: List<MBGenre> = emptyList(),

    val type: String? = null,
    val country: String? = null,

    @SerialName("life-span")
    val lifeSpan: MBLifeSpan? = null,

    val tags: List<MBTag>? = null,
    val rating: MBRating? = null,

    val relations: List<MBRelation>? = null,
    val disambiguation: String? = null,
)

@Serializable
data class MBReleaseGroupDetailResponse(
    val id: String,
    val title: String,
    val genres: List<MBGenre> = emptyList(),

    @SerialName("primary-type")
    val primaryType: String? = null,

    @SerialName("secondary-types")
    val secondaryTypes: List<String> = emptyList(),

    @SerialName("first-release-date")
    val firstReleaseDate: String? = null,

    val tags: List<MBTag>? = null,
    val rating: MBRating? = null,
    val relations: List<MBRelation>? = null,

    @SerialName("artist-credit")
    val artistCredit: List<MBArtistCredit>? = null
)

@Serializable
data class MBArtistCredit(
    val name: String? = null,
    val joinpath: String? = null,
    val artist: MBArtistCreditArtist? = null
)

@Serializable
data class MBArtistCreditArtist(
    val id: String? = null,
    val name: String? = null,
    @SerialName("sort-name")
    val sortName: String? = null
)

data class MBAlbumMetadata(
    val genres: List<String>,
    val albumType: String?,
    val originalYear: Int?,
    val topTags: List<String>,
    val ratingValue: Float?,
    val ratingVotes: Int?,
    val wikipediaUrl: String?,
    val wikipediaInfo: String? = null,
    val links: List<ExternalLink>? = null,
    val mbId: String? = null
)

data class MBArtistMetadata(
    val genres: List<String>,
    val artistType: String?,
    val countryCode: String?,
    val beginYear: Int?,

    val topTags: List<String>,
    val ratingValue: Float?,
    val ratingVotes: Int?,
    val wikipediaUrl: String?,
    val wikipediaBio: String?,
    val disambiguation: String?,
    val links: List<ExternalLink>? = null,
    val mbId: String?,
)

data class WikiInfoResult(
    val info: String,
    val url: String
)

@Serializable
data class ExternalLink(
    val type: String,
    val url: String,
    val platform: String
)

@Serializable
data class MBArtistRelationResponse(
    val relations: List<MBArtistRelationEntry> = emptyList()
)

@Serializable
data class MBArtistRelationEntry(
    val type: String? = null,
    val direction: String? = null,
    val artist: MBArtistRelationTarget? = null
)

@Serializable
data class MBArtistRelationTarget(
    val id: String,
    val name: String? = null,
    val type: String? = null
)

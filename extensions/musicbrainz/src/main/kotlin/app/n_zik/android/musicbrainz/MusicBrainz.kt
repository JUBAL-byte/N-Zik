package app.n_zik.android.musicbrainz

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import app.n_zik.android.musicbrainz.models.ExternalLink
import app.n_zik.android.musicbrainz.models.MBAlbumMetadata
import app.n_zik.android.musicbrainz.models.MBArtistDetailResponse
import app.n_zik.android.musicbrainz.models.MBArtistMetadata
import app.n_zik.android.musicbrainz.models.MBArtistRelationEntry
import app.n_zik.android.musicbrainz.models.MBArtistRelationResponse
import app.n_zik.android.musicbrainz.models.MBReleaseGroupDetailResponse
import app.n_zik.android.musicbrainz.models.MBSearchArtistResponse
import app.n_zik.android.musicbrainz.models.MBSearchReleaseGroupResponse
import app.n_zik.android.musicbrainz.models.WikiInfoResult
import app.n_zik.android.musicbrainz.utils.cleanWikipediaText
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * MusicBrainz API client.
 *
 * MusicBrainz asks clients to respect a maximum rate of 1 request/second
 * and to identify themselves with a valid User-Agent.
 */
class MusicBrainz {

    companion object {
        private const val BASE_URL = "https://musicbrainz.org/ws/2"
        private const val WIKI_API_URL = "https://en.wikipedia.org/w/api.php"

        // Set by the Android app from BuildConfig.VERSION_NAME
        var appVersion: String = "dev"
    }

    private val userAgent get() = "n-zik/$appVersion ( com.nevar.nzik )"

    private val rateLimiter = Mutex()

    private suspend fun <T> makeRateLimitedRequest(block: suspend () -> T): T {
        return rateLimiter.withLock {
            delay(1050) // 1 second + margin
            block()
        }
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                        encodeDefaults = true
                        isLenient = true
                    }
                )
            }

            install(ContentEncoding) {
                gzip()
                deflate()
            }

            MBNetwork.proxy?.let { proxy ->
                engine {
                    this.proxy = proxy
                }
            }

            defaultRequest {
                url(BASE_URL)
            }
        }
    }

    /**
     * Fetches artist metadata (genres, tags, rating, links) from MusicBrainz.
     */
    suspend fun fetchArtistMetadata(artistName: String): MBArtistMetadata {
        return makeRateLimitedRequest {
            // 1. Search the artist to get the MBID
            val searchResponse = client.get("$BASE_URL/artist?query=$artistName&fmt=json") {
                header("User-Agent", userAgent)
            }
            val searchResult = searchResponse.body<MBSearchArtistResponse>()
            val mbid = searchResult.artists.maxByOrNull { it.score }?.id
                ?: return@makeRateLimitedRequest MBArtistMetadata(emptyList(), null, null, null, emptyList(), null, null, null, null, null, emptyList(), null)

            // 2. Fetch details with genres
            val detailResponse = client.get("$BASE_URL/artist/$mbid?inc=genres+tags+ratings+url-rels&fmt=json") {
                header("User-Agent", userAgent)
            }
            val detailResult = detailResponse.body<MBArtistDetailResponse>()

            val genres = detailResult.genres
                .sortedByDescending { it.count }
                .map { it.name.lowercase() }

            val beginYear = detailResult.lifeSpan?.begin?.take(4)?.toIntOrNull()

            val topTags = detailResult.tags
                ?.sortedByDescending { it.count }
                ?.take(5)
                ?.map { it.name.lowercase() }
                ?: emptyList()

            val ratingValue = detailResult.rating?.value
            val ratingVotes = detailResult.rating?.votesCount

            val links = detailResult.relations
                ?.filter { it.url != null && (it.type == "social network" || it.type == "official homepage") }
                ?.map { relation ->
                    val url = relation.url!!.resource
                    ExternalLink(
                        type = relation.type ?: "unknown",
                        url = url,
                        platform = extractPlatformFromUrl(url, relation.type)
                    )
                } ?: emptyList()

            val wikiUrl = detailResult.relations
                ?.firstOrNull { it.url?.resource?.contains("wikipedia.org") == true }
                ?.url?.resource

            MBArtistMetadata(
                genres = genres,
                artistType = detailResult.type,
                countryCode = detailResult.country,
                beginYear = beginYear,
                topTags = topTags,
                ratingValue = ratingValue,
                ratingVotes = ratingVotes,
                wikipediaUrl = wikiUrl,
                disambiguation = detailResult.disambiguation,
                wikipediaBio = null,
                links = links,
                mbId = mbid
            )
        }
    }

    /**
     * Fetches release-group metadata (genres, tags, rating, links) for an album.
     */
    suspend fun fetchAlbumMetadata(albumTitle: String, artistName: String): MBAlbumMetadata {
        return makeRateLimitedRequest {
            // 1. Search the Release Group
            val query = URLEncoder.encode("releasegroup:\"$albumTitle\" AND artist:\"$artistName\"", "UTF-8")
            val searchResponse = client.get("$BASE_URL/release-group?query=$query&fmt=json") {
                header("User-Agent", userAgent)
            }
            val searchResult = searchResponse.body<MBSearchReleaseGroupResponse>()
            val mbid = searchResult.releaseGroups.maxByOrNull { it.score }?.id
                ?: return@makeRateLimitedRequest MBAlbumMetadata(emptyList(), null, null, emptyList(), null, null, null, mbId = null)

            // 2. Fetch details
            val detailResponse = client.get("$BASE_URL/release-group/$mbid?inc=genres+tags+ratings+url-rels&fmt=json") {
                header("User-Agent", userAgent)
            }
            val detailResult = detailResponse.body<MBReleaseGroupDetailResponse>()

            // 3. Extract and format the data
            val genres = detailResult.genres
                .sortedByDescending { it.count }
                .map { it.name.lowercase() }

            // If the type is Album but also Live, prefer "Live"
            val albumType = when {
                detailResult.secondaryTypes.contains("Live") -> "Live"
                detailResult.secondaryTypes.contains("Compilation") -> "Compilation"
                detailResult.secondaryTypes.contains("Remix") -> "Remix"
                else -> detailResult.primaryType // "Album", "Single", "EP"
            }

            // Extract the year from the YYYY date
            val originalYear = detailResult.firstReleaseDate?.take(4)?.toIntOrNull()

            val topTags = detailResult.tags
                ?.sortedByDescending { it.count }
                ?.take(5)
                ?.map { it.name.lowercase() }
                ?: emptyList()

            val ratingValue = detailResult.rating?.value
            val ratingVotes = detailResult.rating?.votesCount

            val wikiUrl = detailResult.relations
                ?.firstOrNull { it.url?.resource?.contains("wikipedia.org") == true }
                ?.url?.resource

            val links = detailResult.relations
                ?.filter { it.url != null && (it.type == "social network" || it.type == "official homepage") }
                ?.map { relation ->
                    val url = relation.url!!.resource
                    ExternalLink(
                        type = relation.type ?: "unknown",
                        url = url,
                        platform = extractPlatformFromUrl(url, relation.type)
                    )
                } ?: emptyList()

            MBAlbumMetadata(
                genres = genres,
                albumType = albumType,
                originalYear = originalYear,
                topTags = topTags,
                ratingValue = ratingValue,
                ratingVotes = ratingVotes,
                wikipediaUrl = wikiUrl,
                links = links,
                mbId = mbid
            )
        }
    }

    /**
     * Fetches a short Wikipedia extract (intro) for the given search term.
     */
    suspend fun fetchWikipediaExtractByArtist(artistTerm: String): WikiInfoResult? {
        return try {
            val cleanName = artistTerm
                .replace(Regex("(?i)VEVO$|- Topic$|Official"), "")
                .trim()
                .encodeURLParameter()

            val searchUrl = "$WIKI_API_URL?" +
                    "action=query&titles=$cleanName&prop=extracts&exintro&explaintext&format=json&redirects=true"

            val searchResponse = client.get(searchUrl)
            val searchJson = Json.parseToJsonElement(searchResponse.bodyAsText()).jsonObject

            val pages = searchJson["query"]?.jsonObject?.get("pages")?.jsonObject

            // Wikipedia JSON is a map keyed by page id (e.g. "12345");
            // when the page does not exist the key is "-1"
            val pageEntry = pages?.entries?.firstOrNull()

            val isMissing = pageEntry?.value?.jsonObject?.containsKey("missing")

            if (isMissing == true) {
                return null
            }

            val pageData = pageEntry?.value?.jsonObject

            val bio = pageData?.get("extract")?.jsonPrimitive?.contentOrNull

            // Final title after any Wikipedia redirect
            val title = pageData?.get("title")?.jsonPrimitive?.contentOrNull

            if (bio != null && title != null) {
                // Build the URL: "Nirvana (band)" -> "Nirvana_(band)"
                val formattedTitle = title.replace(" ", "_")
                val wikiUrl = "https://en.wikipedia.org/wiki/${formattedTitle.encodeURLParameter()}"

                WikiInfoResult(
                    info = bio.cleanWikipediaText(),
                    url = wikiUrl
                )
            } else {
                null
            }

        } catch (e: Exception) {
            MBLogger.e("MusicBrainz", "Error fetching Wikipedia bio for $artistTerm", e)
            null
        }
    }

    /**
     * Fetches artist relations (members, collaborations, ...) from MusicBrainz.
     */
    suspend fun fetchArtistRelations(artistMbId: String): List<MBArtistRelationEntry> {
        val response = client.get("$BASE_URL/artist/$artistMbId?inc=artist-rels&fmt=json") {
            header("User-Agent", userAgent)
        }
        return response.body<MBArtistRelationResponse>().relations
    }

    /**
     * Guesses the platform of an external link from its URL.
     */
    private fun extractPlatformFromUrl(url: String, type: String?): String {
        return when {
            type == "official homepage" -> "home"
            "instagram.com" in url -> "instagram"
            "facebook.com" in url -> "facebook"
            "twitter.com" in url || "x.com" in url -> "twitter"
            "youtube.com" in url || "youtu.be" in url -> "youtube"
            "open.spotify.com" in url -> "spotify"
            "music.apple.com" in url -> "applemusic"
            "deezer.com" in url -> "deezer"
            "tidal.com" in url -> "tidal"
            "soundcloud.com" in url -> "soundcloud"
            "discogs.com" in url -> "discogs"
            "rateyourmusic.com" in url -> "rateyourmusic"
            "last.fm" in url -> "lastfm"
            else -> "" // Generic fallback
        }
    }
}

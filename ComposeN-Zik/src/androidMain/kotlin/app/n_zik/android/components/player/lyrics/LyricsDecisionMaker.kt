package app.n_zik.android.components.player.lyrics

import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics

/**
 * Encapsulates the logic for determining which lyrics to display and whether a network fetch is needed.
 */
data class FetchNeeds(
    val currentLyrics: Lyrics?,
    val needKaraokeFetch: Boolean,
    val needSyncedFetch: Boolean,
    val needUnsyncedFetch: Boolean
)

object LyricsDecisionMaker {

    fun evaluateFetchNeeds(
        mediaId: String,
        lyricsType: LyricsType,
        allLyrics: List<Lyrics>,
        globalLastKaraokeAttemptMediaId: String?,
        globalLastSyncedAttemptMediaId: String?,
        globalLastUnSyncedAttemptMediaId: String?
    ): FetchNeeds {
        
        val currentLyrics = when (lyricsType) {
            LyricsType.Auto -> {
                allLyrics.find { it.type == LyricsType.Karaoke.name }
                    ?: allLyrics.find { it.type == LyricsType.Synced.name }
                    ?: allLyrics.find { it.type == LyricsType.Unsynced.name }
            }
            LyricsType.Karaoke -> allLyrics.find { it.type == LyricsType.Karaoke.name }
            LyricsType.Synced -> allLyrics.find { it.type == LyricsType.Synced.name }
                ?: allLyrics.find { it.type == LyricsType.Karaoke.name }
            LyricsType.Unsynced -> allLyrics.find { it.type == LyricsType.Unsynced.name }
        }

        // User-edited lyrics are never replaced by an automatic fetch (gh-765). An emptied edit
        // is treated as "no lyrics", so fetching stays allowed in that case.
        if (currentLyrics?.isEdited == true && !currentLyrics.data.isNullOrEmpty()) {
            return FetchNeeds(
                currentLyrics = currentLyrics,
                needKaraokeFetch = false,
                needSyncedFetch = false,
                needUnsyncedFetch = false
            )
        }

        val hasWordTimings = currentLyrics?.data?.lines()?.any { it.trim().startsWith("<") && it.contains(":") && it.contains(">") } == true

        val needKaraokeFetch = (lyricsType == LyricsType.Karaoke || lyricsType == LyricsType.Synced || lyricsType == LyricsType.Auto) && 
            (!hasWordTimings && globalLastKaraokeAttemptMediaId != mediaId)

        val needSyncedFetch = when (lyricsType) {
            LyricsType.Auto -> {
                (currentLyrics == null || (currentLyrics.type != LyricsType.Synced.name && currentLyrics.type != LyricsType.Karaoke.name) || currentLyrics.data.isNullOrEmpty())
            }
            LyricsType.Synced -> {
                // If explicitly requested Synced, we strictly require Synced.name
                (currentLyrics == null || currentLyrics.type != LyricsType.Synced.name || currentLyrics.data.isNullOrEmpty())
            }
            else -> false
        } && globalLastSyncedAttemptMediaId != mediaId

        val needUnsyncedFetch = (lyricsType == LyricsType.Unsynced || lyricsType == LyricsType.Auto) && 
            (currentLyrics == null || currentLyrics.data.isNullOrEmpty()) && 
            globalLastUnSyncedAttemptMediaId != mediaId

        return FetchNeeds(
            currentLyrics = currentLyrics,
            needKaraokeFetch = needKaraokeFetch,
            needSyncedFetch = needSyncedFetch,
            needUnsyncedFetch = needUnsyncedFetch
        )
    }
}


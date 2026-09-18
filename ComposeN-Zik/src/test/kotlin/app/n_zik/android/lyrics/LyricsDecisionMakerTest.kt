package app.n_zik.android.lyrics

import app.n_zik.android.components.player.lyrics.LyricsDecisionMaker
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LyricsDecisionMakerTest {

    @Test
    fun `when wanting karaoke and database is empty, it needs karaoke fetch`() {
        val needs = LyricsDecisionMaker.evaluateFetchNeeds(
            mediaId = "song1",
            lyricsType = LyricsType.Karaoke,
            allLyrics = emptyList(),
            globalLastKaraokeAttemptMediaId = null,
            globalLastSyncedAttemptMediaId = null,
            globalLastUnSyncedAttemptMediaId = null
        )

        assertTrue(needs.needKaraokeFetch)
        assertFalse(needs.needSyncedFetch)
        assertNull(needs.currentLyrics)
    }

    @Test
    fun `when wanting karaoke and karaoke has no timings, but already attempted, it skips fetch`() {
        // Simulating the fallback behavior where BetterLyrics Sync was stored in Karaoke slot
        val fallbackLyrics = Lyrics(
            songId = "song1",
            type = LyricsType.Karaoke.name,
            data = "[00:10.00] Sync line 1" // No '<' timings
        )

        val needs = LyricsDecisionMaker.evaluateFetchNeeds(
            mediaId = "song1",
            lyricsType = LyricsType.Karaoke,
            allLyrics = listOf(fallbackLyrics),
            globalLastKaraokeAttemptMediaId = "song1", // Already attempted in this session
            globalLastSyncedAttemptMediaId = null,
            globalLastUnSyncedAttemptMediaId = null
        )

        // It does not have word timings, but because we already attempted it, needKaraokeFetch should be false
        assertFalse(needs.needKaraokeFetch)
        assertEquals(fallbackLyrics, needs.currentLyrics)
    }

    @Test
    fun `when wanting karaoke and karaoke has no timings and NOT attempted yet, it needs fetch`() {
        val fallbackLyrics = Lyrics(
            songId = "song1",
            type = LyricsType.Karaoke.name,
            data = "[00:10.00] Sync line 1" // No '<' timings
        )

        val needs = LyricsDecisionMaker.evaluateFetchNeeds(
            mediaId = "song1",
            lyricsType = LyricsType.Karaoke,
            allLyrics = listOf(fallbackLyrics),
            globalLastKaraokeAttemptMediaId = null, // NOT attempted yet (e.g. app restart)
            globalLastSyncedAttemptMediaId = null,
            globalLastUnSyncedAttemptMediaId = null
        )

        assertTrue(needs.needKaraokeFetch)
        assertEquals(fallbackLyrics, needs.currentLyrics)
    }

    @Test
    fun `when wanting synced and database is empty, it needs synced fetch`() {
        val needs = LyricsDecisionMaker.evaluateFetchNeeds(
            mediaId = "song1",
            lyricsType = LyricsType.Synced,
            allLyrics = emptyList(),
            globalLastKaraokeAttemptMediaId = null,
            globalLastSyncedAttemptMediaId = null,
            globalLastUnSyncedAttemptMediaId = null
        )

        assertTrue(needs.needSyncedFetch)
        assertTrue(needs.needKaraokeFetch)
        assertNull(needs.currentLyrics)
    }

    private fun needsFor(lyricsType: LyricsType, allLyrics: List<Lyrics>) =
        LyricsDecisionMaker.evaluateFetchNeeds(
            mediaId = "song1",
            lyricsType = lyricsType,
            allLyrics = allLyrics,
            globalLastKaraokeAttemptMediaId = "otherSong",
            globalLastSyncedAttemptMediaId = "otherSong",
            globalLastUnSyncedAttemptMediaId = "otherSong"
        )

    @Test
    fun `edited lyrics are protected from every fetch in every mode`() {
        // gh-765: globals only remember the last mediaId, so another song was played in between.
        val cases = mapOf(
            LyricsType.Auto to LyricsType.Unsynced,
            LyricsType.Karaoke to LyricsType.Karaoke,
            LyricsType.Synced to LyricsType.Synced,
            LyricsType.Unsynced to LyricsType.Unsynced
        )
        cases.forEach { (mode, storedType) ->
            val edited = Lyrics("song1", storedType.name, "my own text", isEdited = true)

            val needs = needsFor(mode, listOf(edited))

            assertFalse(needs.needKaraokeFetch, "karaoke fetch in $mode")
            assertFalse(needs.needSyncedFetch, "synced fetch in $mode")
            assertFalse(needs.needUnsyncedFetch, "unsynced fetch in $mode")
            assertSame(edited, needs.currentLyrics)
        }
    }

    @Test
    fun `edited karaoke row displayed as Synced fallback is protected`() {
        val edited = Lyrics("song1", LyricsType.Karaoke.name, "[00:10.00] mine", isEdited = true)

        val needs = needsFor(LyricsType.Synced, listOf(edited))

        assertFalse(needs.needKaraokeFetch)
        assertFalse(needs.needSyncedFetch)
        assertSame(edited, needs.currentLyrics)
    }

    @Test
    fun `not edited lyrics keep the upgrade behaviour`() {
        val stored = Lyrics("song1", LyricsType.Unsynced.name, "plain text")

        val needs = needsFor(LyricsType.Auto, listOf(stored))

        assertTrue(needs.needKaraokeFetch)
        assertTrue(needs.needSyncedFetch)
        assertFalse(needs.needUnsyncedFetch)
        assertSame(stored, needs.currentLyrics)
    }

    @Test
    fun `emptied edit is treated as no lyrics and allows fetching again`() {
        listOf<String?>("", null).forEach { emptied ->
            val edited = Lyrics("song1", LyricsType.Unsynced.name, emptied, isEdited = true)

            val needs = needsFor(LyricsType.Auto, listOf(edited))

            assertTrue(needs.needKaraokeFetch)
            assertTrue(needs.needSyncedFetch)
            assertTrue(needs.needUnsyncedFetch)
        }
    }

}

package app.n_zik.android.components.player.lyrics

import app.n_zik.android.enums.lyrics.LyricsType
import it.fast4x.lrclib.models.Track
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LyricsTrackSelectorTest {

    private fun track(plain: String?, synced: String?) = Track(
        id = 1L,
        name = "Song",
        trackName = "Song",
        artistName = "Artist",
        albumName = "Album",
        tDuration = JsonPrimitive(200),
        instrumental = false,
        plainLyrics = plain,
        syncedLyrics = synced
    )

    @Test
    fun `a picked track with synced lyrics is saved as a Synced row flagged isEdited`() {
        val row = pickedLyrics("song1", track(plain = "plain", synced = "[00:01.00]synced"))

        assertEquals("song1", row.songId)
        assertEquals(LyricsType.Synced.name, row.type)
        assertEquals("[00:01.00]synced", row.data)
        // The flag keeps an automatic fetch from replacing the user's deliberate choice.
        assertTrue(row.isEdited)
    }

    @Test
    fun `a picked track without synced lyrics is saved as an Unsynced row flagged isEdited`() {
        val row = pickedLyrics("song1", track(plain = "plain text", synced = null))

        assertEquals(LyricsType.Unsynced.name, row.type)
        assertEquals("plain text", row.data)
        assertTrue(row.isEdited)
    }
}

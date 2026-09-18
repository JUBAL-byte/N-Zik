package app.n_zik.android.components.dialog.song

import app.n_zik.android.enums.lyrics.LyricsType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditLyricsDialogTest {

    @Test
    fun `saved edit row is flagged isEdited with the given song, type and text`() {
        val row = EditLyricsDialog.editedLyrics("song1", LyricsType.Synced, "[00:01.00]my line")

        assertTrue(row.isEdited)
        assertEquals("song1", row.songId)
        assertEquals(LyricsType.Synced.name, row.type)
        assertEquals("[00:01.00]my line", row.data)
    }
}

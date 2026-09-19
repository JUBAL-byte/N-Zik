package app.n_zik.android.components.player.lyrics

import app.n_zik.android.enums.lyrics.LyricsType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FetchAgainRowTypeTest {

    @Test
    fun `the displayed row type wins in every mode`() {
        LyricsType.entries.forEach { mode ->
            assertEquals(LyricsType.Synced.name, fetchAgainRowType(LyricsType.Synced.name, mode), "mode $mode")
        }
    }

    @Test
    fun `with nothing displayed a concrete mode resets its own row type`() {
        assertEquals(LyricsType.Karaoke.name, fetchAgainRowType(null, LyricsType.Karaoke))
        assertEquals(LyricsType.Synced.name, fetchAgainRowType(null, LyricsType.Synced))
        assertEquals(LyricsType.Unsynced.name, fetchAgainRowType(null, LyricsType.Unsynced))
    }

    @Test
    fun `in Auto with nothing displayed no row is written because Auto is not a stored type`() {
        assertNull(fetchAgainRowType(null, LyricsType.Auto))
    }
}

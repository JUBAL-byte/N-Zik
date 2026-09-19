package app.n_zik.android.utils.playlist

import android.content.Context
import android.net.Uri
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Issue #606 M2 — `LocalPlaylistSongs.editThumbnailLauncher` used to call
 * `saveImageToInternalStorage` (decode + scale + compress + write) synchronously on Main. It now
 * goes through [savePlaylistThumbnail], which runs it on `NzikDispatchers.DATA` and must still
 * forward the same arguments into the `thumbnail` directory and hand back the same result.
 */
class PlaylistThumbnailTest {

    @Test
    fun `savePlaylistThumbnail saves off the caller thread with the thumbnail directory and name`() = runBlocking {
        val callerThread = Thread.currentThread()
        val context = mockk<Context>()
        val picked = mockk<Uri>()
        val saved = mockk<Uri>()
        val calls = mutableListOf<List<Any?>>()
        val threads = mutableListOf<Thread>()

        val result = savePlaylistThumbnail(context, picked, "playlist_42") { c, u, dir, name ->
            threads += Thread.currentThread()
            calls += listOf(c, u, dir, name)
            saved
        }

        assertSame(saved, result)
        assertEquals(listOf(listOf(context, picked, "thumbnail", "playlist_42")), calls)
        assertNotEquals(callerThread, threads.single())
    }

    @Test
    fun `savePlaylistThumbnail returns null when the image could not be saved`() = runBlocking {
        val result = savePlaylistThumbnail(mockk<Context>(), mockk<Uri>(), "playlist_1") { _, _, _, _ -> null }

        assertNull(result)
    }
}

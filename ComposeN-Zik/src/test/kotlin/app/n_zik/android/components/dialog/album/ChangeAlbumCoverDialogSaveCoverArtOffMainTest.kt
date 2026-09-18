package app.n_zik.android.components.dialog.album

import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Issue #606 H9 review follow-up -- [ChangeAlbumCoverDialog.saveCoverArt] has no delete step
 * (unlike `ChangeCoverDialog.saveCoverArt`), so its only job is to forward its arguments to the
 * legacy `saveImageToInternalStorage` (`app.it.fast4x.rimusic.utils.FileUtils.kt`, out of scope
 * for editing) unchanged and return its result. This test pins that forwarding: a param-order
 * mistake here would make the mocked-static stub below not match, failing the test.
 */
class ChangeAlbumCoverDialogSaveCoverArtOffMainTest {

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `saveCoverArt delegates to saveImageToInternalStorage with the same arguments`() {
        mockkStatic("app.it.fast4x.rimusic.utils.FileUtilsKt")
        val context = mockk<Context>()
        val uri = mockk<Uri>()
        val savedUri = mockk<Uri>()
        every {
            app.it.fast4x.rimusic.utils.saveImageToInternalStorage(context, uri, "app_covers", "cover_album1.jpg")
        } returns savedUri

        val result = ChangeAlbumCoverDialog.saveCoverArt(context, uri, "app_covers", "cover_album1.jpg")

        assertEquals(savedUri, result)
    }
}

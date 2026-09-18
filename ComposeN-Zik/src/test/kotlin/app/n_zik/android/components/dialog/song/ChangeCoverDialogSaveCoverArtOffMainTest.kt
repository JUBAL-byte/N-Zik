package app.n_zik.android.components.dialog.song

import android.content.Context
import android.net.Uri
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Issue #606 H9 -- `ChangeCoverDialog.kt`'s `launcher` callback used to call
 * `File.delete()` and `saveImageToInternalStorage` (decode + scale + compress a bitmap)
 * synchronously, on the Main thread an `ActivityResultLauncher` callback resumes on. That work is
 * now extracted to [ChangeCoverDialog.saveCoverArt] and dispatched via
 * `withContext(NzikDispatchers.DATA)` from `coroutineScope.launch`, with the resulting Compose
 * state write (`value = ...`) happening only after that `withContext` returns -- back on Main.
 *
 * `saveImageToInternalStorage` itself is legacy (`app.it.fast4x.rimusic.utils.FileUtils.kt`, out
 * of scope for editing) and decodes real bitmaps via `BitmapFactory`, which needs a real Android
 * environment to shadow. Mocking it out here (the same technique `ShufflerTest` uses for
 * `Player.kt`'s top-level functions) keeps this test a plain, fast JVM unit test that verifies
 * exactly what [saveCoverArt] is responsible for: deleting the previous cover file before
 * delegating, forwarding its arguments unchanged, and running off the caller's thread once
 * dispatched to `NzikDispatchers.DATA` -- not the pixel-level behavior of the legacy decoder.
 */
class ChangeCoverDialogSaveCoverArtOffMainTest {

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `saveCoverArt deletes an existing old file before delegating to saveImageToInternalStorage`() {
        val oldFile = File.createTempFile("old_cover", ".jpg")
        assertTrue(oldFile.exists(), "precondition: temp file must exist before saveCoverArt runs")

        mockkStatic("app.it.fast4x.rimusic.utils.FileUtilsKt")
        val context = mockk<Context>()
        val uri = mockk<Uri>()
        val savedUri = mockk<Uri>()
        every {
            app.it.fast4x.rimusic.utils.saveImageToInternalStorage(context, uri, "app_covers", "cover_x.jpg")
        } returns savedUri

        val result = ChangeCoverDialog.saveCoverArt(context, uri, oldFile, "app_covers", "cover_x.jpg")

        assertFalse(oldFile.exists(), "saveCoverArt must delete the previous cover file before saving the new one")
        assertEquals(savedUri, result)
    }

    @Test
    fun `saveCoverArt still delegates to saveImageToInternalStorage when there is no old file`() {
        val oldFile = File.createTempFile("missing_cover", ".jpg")
        oldFile.delete()
        assertFalse(oldFile.exists(), "precondition: temp file must not exist before saveCoverArt runs")

        mockkStatic("app.it.fast4x.rimusic.utils.FileUtilsKt")
        val context = mockk<Context>()
        val uri = mockk<Uri>()
        val savedUri = mockk<Uri>()
        every {
            app.it.fast4x.rimusic.utils.saveImageToInternalStorage(context, uri, "app_covers", "cover_y.jpg")
        } returns savedUri

        val result = ChangeCoverDialog.saveCoverArt(context, uri, oldFile, "app_covers", "cover_y.jpg")

        assertEquals(savedUri, result)
    }

    @Test
    fun `saveCoverArt returns null when saveImageToInternalStorage returns null`() {
        val oldFile = File.createTempFile("old_cover", ".jpg")

        mockkStatic("app.it.fast4x.rimusic.utils.FileUtilsKt")
        val context = mockk<Context>()
        val uri = mockk<Uri>()
        every {
            app.it.fast4x.rimusic.utils.saveImageToInternalStorage(context, uri, "app_covers", "cover_z.jpg")
        } returns null

        val result = ChangeCoverDialog.saveCoverArt(context, uri, oldFile, "app_covers", "cover_z.jpg")

        assertEquals(null, result)
    }

    @Test
    fun `saveCoverArt runs off the caller's thread when dispatched to NzikDispatchers DATA`() = runBlocking {
        val oldFile = File.createTempFile("old_cover", ".jpg")
        mockkStatic("app.it.fast4x.rimusic.utils.FileUtilsKt")
        val context = mockk<Context>()
        val uri = mockk<Uri>()
        val savedUri = mockk<Uri>()
        val callerThreadName = Thread.currentThread().name
        var saveThreadName: String? = null
        every {
            app.it.fast4x.rimusic.utils.saveImageToInternalStorage(context, uri, "app_covers", "cover_w.jpg")
        } answers {
            saveThreadName = Thread.currentThread().name
            savedUri
        }

        val result = withContext(NzikDispatchers.DATA) {
            ChangeCoverDialog.saveCoverArt(context, uri, oldFile, "app_covers", "cover_w.jpg")
        }

        assertEquals(savedUri, result)
        assertNotEquals(
            "saveCoverArt's blocking work must not run on the caller's (UI-simulating) thread once dispatched to DATA",
            callerThreadName,
            saveThreadName
        )
    }
}

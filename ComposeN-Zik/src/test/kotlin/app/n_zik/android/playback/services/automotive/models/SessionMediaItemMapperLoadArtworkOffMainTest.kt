package app.n_zik.android.playback.services.automotive.models

import android.graphics.Bitmap
import app.n_zik.android.core.coil.ImageCacheFactory
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Issue #606 — [SessionMediaItemMapper]'s private `loadArtworkBytes` is called from the 5 public
 * non-suspend Android Auto mappers, so it cannot be made suspend. Its Coil I/O used to run on the
 * caller thread through a bare `runBlocking {}`; it now dispatches to `NzikDispatchers.DATA`.
 * [ImageCacheFactory.loadBitmap] must therefore run on a thread different from the caller's,
 * while the mapper still sets the artwork bytes on the media item.
 *
 * Robolectric + JUnit 4: `toUri()`/`MediaItem` touch framework classes that plain JVM stubs reject.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionMediaItemMapperLoadArtworkOffMainTest {

    private val callerThread = Thread.currentThread()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `mapArtistToMediaItem loadArtwork runs loadBitmap off the caller thread`() {
        val bitmap = mockk<Bitmap>(relaxed = true)
        val threads = CopyOnWriteArrayList<Thread>()
        mockkObject(ImageCacheFactory)
        coEvery { ImageCacheFactory.loadBitmap(any(), any()) } answers {
            threads += Thread.currentThread()
            bitmap
        }

        val item = SessionMediaItemMapper.mapArtistToMediaItem(
            parentId = "artist",
            id = "aBcDeF12345",
            name = "Some Artist",
            thumbnailUrl = "https://img.example.com/artist.jpg",
            loadArtwork = true,
        )

        assertEquals(1, threads.size)
        assertNotEquals(callerThread, threads.single())
        assertNotNull(item.mediaMetadata.artworkData)
    }
}

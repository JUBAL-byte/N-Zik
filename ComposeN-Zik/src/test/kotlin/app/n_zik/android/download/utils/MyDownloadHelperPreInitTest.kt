package app.n_zik.android.download.utils

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * gh-606 N1 — the UI accessors of [MyDownloadHelper] (`getDownload`, `isSongDownloaded`) must
 * stay safe before the blocking download-manager initialization: they read only the `downloads`
 * StateFlow. Touching the uninitialized lateinit `downloadManager` would throw
 * `UninitializedPropertyAccessException`, so these tests passing proves the accessors never
 * force the init.
 *
 * Also pins the wiring of [initDownloadManagerOffMain]: dispatched to
 * [NzikDispatchers.DATA] it runs off the caller's thread and never propagates an init
 * failure to the caller (the composition must not crash over a failed one-time init).
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine on the
 * JUnit 5 platform (the Robolectric runner only works with JUnit 4). Robolectric is used only so
 * `android.net.Uri` can back a real [DownloadRequest] and so an application [Context] is
 * available for the wiring tests — no mockk is needed for the media3 classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyDownloadHelperPreInitTest {

    @After
    fun resetHelperState() {
        MyDownloadHelper.downloads.value = emptyMap()
    }

    @Test
    fun `getDownload before init returns null without touching the download manager`() = runBlocking {
        MyDownloadHelper.downloads.value = emptyMap()

        val download = MyDownloadHelper.getDownload("song-unknown").first()

        assertNull(download)
    }

    @Test
    fun `getDownload returns the indexed download once the map is populated`() = runBlocking {
        val download = fakeDownload("song1", Download.STATE_STOPPED)
        MyDownloadHelper.downloads.value = mapOf("song1" to download)

        val result = MyDownloadHelper.getDownload("song1").first()

        assertSame(download, result)
    }

    @Test
    fun `isSongDownloaded before init is false`() {
        MyDownloadHelper.downloads.value = emptyMap()

        assertFalse(MyDownloadHelper.isSongDownloaded("song1"))
    }

    @Test
    fun `isSongDownloaded is true once a completed download is indexed`() {
        val download = fakeDownload("song2", Download.STATE_COMPLETED)
        MyDownloadHelper.downloads.value = mapOf("song2" to download)

        assertTrue(MyDownloadHelper.isSongDownloaded("song2"))
    }

    @Test
    fun `initDownloadManagerOffMain runs off the caller thread when dispatched to DATA`() = runBlocking {
        val callerThreadName = Thread.currentThread().name

        val runnerThreadName = withContext(NzikDispatchers.DATA) {
            initDownloadManagerOffMain(ApplicationProvider.getApplicationContext<Context>())
            Thread.currentThread().name
        }

        assertNotEquals(callerThreadName, runnerThreadName)
    }

    @Test
    fun `initDownloadManagerOffMain never propagates an init failure to the caller`() = runBlocking {
        val failingContext = mockk<Context>()

        initDownloadManagerOffMain(failingContext)
    }

    private fun fakeDownload(id: String, state: Int): Download {
        val request = DownloadRequest.Builder(id, Uri.parse("https://music.example.com/stream/$id")).build()
        return Download(request, state, 0L, 0L, 0L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)
    }
}

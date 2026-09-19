package app.n_zik.android.updater.services

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * gh-606 N3 — `UpdateDownloadManager.clearCache` must purge the APK directory it derives from
 * the context (the private `cleanupTempFiles` builds it from
 * `<external files>/Download/nzik_updates`) and reset the download state to
 * [UpdateDownloadManager.DownloadState.Idle]. The file-based core [UpdateDownloadManager.cleanupUpdateDir]
 * is covered by [UpdateDownloadManagerCleanupTest]; this test pins the context-to-directory
 * wiring that only a real [Context] can exercise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateDownloadManagerClearCacheTest {

    @Test
    fun `clearCache deletes the apks from the context-derived update dir and resets the state to Idle`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val downloadDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "nzik_updates"
        )
        downloadDir.mkdirs()
        File(downloadDir, "nzik-update-1.0.0.apk").writeText("apk")
        File(downloadDir, "nzik-update-1.1.0-beta.apk").writeText("apk")

        UpdateDownloadManager.clearCache(context)

        assertTrue(downloadDir.exists())
        assertTrue(downloadDir.listFiles().orEmpty().isEmpty())
        assertTrue(
            "clearCache must reset the download state to Idle",
            UpdateDownloadManager.downloadState.value is UpdateDownloadManager.DownloadState.Idle
        )
    }
}

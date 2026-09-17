package app.n_zik.android.components.dialog.song

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

/**
 * Issue #606 M7b — `EditMetadataDialog.kt`'s `pickerLauncher` callback used to call
 * `context.contentResolver.openInputStream(uri)?.use { it.readBytes() }` synchronously, on the
 * Main thread the `ActivityResultLauncher` callback resumes on. That read is now extracted to
 * [EditMetadataDialog.readCoverArtBytes] and dispatched via `withContext(NzikDispatchers.DATA)`
 * from `coroutineScope.launch`. The read itself has no UI-thread dependency, so the offload must
 * change neither its result (the same bytes) nor correctness for the same [Uri] — only which
 * thread runs it.
 */
class EditMetadataDialogReadCoverArtOffMainTest {

    private fun contextReturning(bytes: ByteArray, uri: Uri): Context {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } answers { ByteArrayInputStream(bytes) }
        val context = mockk<Context>()
        every { context.contentResolver } returns resolver
        return context
    }

    @Test
    fun `readCoverArtBytes dispatched to DATA returns the same bytes as calling it directly`() = runBlocking {
        val uri = mockk<Uri>()
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 42, 100)
        val context = contextReturning(bytes, uri)

        val direct = EditMetadataDialog.readCoverArtBytes(context, uri)
        val offloaded = withContext(NzikDispatchers.DATA) {
            EditMetadataDialog.readCoverArtBytes(contextReturning(bytes, uri), uri)
        }

        assertArrayEquals(bytes, direct)
        assertArrayEquals(bytes, offloaded)
    }

    @Test
    fun `readCoverArtBytes returns null when the resolver has nothing for the uri`() {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns null
        val context = mockk<Context>()
        every { context.contentResolver } returns resolver

        val result = EditMetadataDialog.readCoverArtBytes(context, uri)

        assertNull(result)
    }

    @Test
    fun `readCoverArtBytes runs off the caller's thread when dispatched to DATA`() = runBlocking {
        val uri = mockk<Uri>()
        val bytes = byteArrayOf(9, 8, 7)
        val callerThreadName = Thread.currentThread().name

        val executionThreadName = withContext(NzikDispatchers.DATA) {
            EditMetadataDialog.readCoverArtBytes(contextReturning(bytes, uri), uri)
            Thread.currentThread().name
        }

        assertNotEquals(
            "readCoverArtBytes must not run on the caller's (UI-simulating) thread once dispatched to DATA",
            callerThreadName,
            executionThreadName
        )
    }
}

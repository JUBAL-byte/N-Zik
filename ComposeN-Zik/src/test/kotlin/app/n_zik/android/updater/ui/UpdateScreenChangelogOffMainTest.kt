package app.n_zik.android.updater.ui

import android.content.Context
import android.content.res.Resources
import app.n_zik.android.R
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

/**
 * Issue #606 M7a — `UpdateScreen.kt`'s local changelog read (`R.raw.release_notes`) used to run
 * synchronously inside a `remember { }` block during composition (Main thread). It is now
 * extracted to the top-level [readLocalReleaseNotes] and dispatched via
 * `withContext(NzikDispatchers.DATA)` from a `LaunchedEffect`. The read itself has no UI-thread
 * dependency (plain resource I/O), so the offload must change neither its result nor
 * correctness for the same context — only which thread runs it. `Context`/`Resources` are mocked
 * (rather than resolved through Robolectric's real resource table) so the test exercises exactly
 * the read logic, independent of resource-merging setup.
 */
class UpdateScreenChangelogOffMainTest {

    private fun contextReturning(text: String): Context {
        val resources = mockk<Resources>()
        every { resources.openRawResource(R.raw.release_notes) } answers {
            ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
        }
        val context = mockk<Context>()
        every { context.resources } returns resources
        return context
    }

    @Test
    fun `readLocalReleaseNotes dispatched to DATA returns the same text as calling it directly`() = runBlocking {
        val text = "New:\n- Initial release\n"

        val direct = readLocalReleaseNotes(contextReturning(text))
        val offloaded = withContext(NzikDispatchers.DATA) { readLocalReleaseNotes(contextReturning(text)) }

        assertEquals(text, direct)
        assertEquals(text, offloaded)
    }

    @Test
    fun `readLocalReleaseNotes returns empty string when the read throws`() {
        val resources = mockk<Resources>()
        every { resources.openRawResource(R.raw.release_notes) } throws Resources.NotFoundException("missing")
        val context = mockk<Context>()
        every { context.resources } returns resources

        val result = readLocalReleaseNotes(context)

        assertEquals("", result)
    }

    @Test
    fun `readLocalReleaseNotes runs off the caller's thread when dispatched to DATA`() = runBlocking {
        val callerThreadName = Thread.currentThread().name

        val executionThreadName = withContext(NzikDispatchers.DATA) {
            readLocalReleaseNotes(contextReturning("some text"))
            Thread.currentThread().name
        }

        assertNotEquals(
            "readLocalReleaseNotes must not run on the caller's (UI-simulating) thread once dispatched to DATA",
            callerThreadName,
            executionThreadName
        )
    }
}

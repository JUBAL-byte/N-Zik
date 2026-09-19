package app.n_zik.android.components.import

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuardedImportTest {

    private val failures = mutableListOf<Exception>()
    private var databaseClosed = false
    private var restartPrompted = false

    private fun run(block: () -> Unit) = runGuardedImport(
        onFailure = { failures += it },
        isDatabaseClosed = { databaseClosed },
        showRestartPrompt = { restartPrompted = true },
        block = block
    )

    @Test
    fun `successful import neither reports a failure nor prompts a restart`() {
        run { databaseClosed = true }

        assertTrue(failures.isEmpty())
        assertFalse(restartPrompted)
    }

    @Test
    fun `failure with the database still open only reports the failure`() {
        val error = IllegalStateException("bad file")

        run { throw error }

        assertEquals(listOf<Exception>(error), failures)
        assertFalse(restartPrompted)
    }

    @Test
    fun `failure after the block closed the database also prompts a restart`() {
        val error = IOException("disk full")

        run {
            databaseClosed = true
            throw error
        }

        assertEquals(listOf<Exception>(error), failures)
        assertTrue(restartPrompted)
    }

    @Test
    fun `failure is reported before the restart prompt`() {
        val order = mutableListOf<String>()
        databaseClosed = true

        runGuardedImport(
            onFailure = { order += "failure" },
            isDatabaseClosed = { true },
            showRestartPrompt = { order += "restart" }
        ) { throw IllegalStateException("boom") }

        assertEquals(listOf("failure", "restart"), order)
    }

    @Test
    fun `cancellation is rethrown and never treated as a failed import`() {
        databaseClosed = true

        assertThrows(CancellationException::class.java) {
            run { throw CancellationException("cancelled") }
        }

        assertTrue(failures.isEmpty())
        assertFalse(restartPrompted)
    }

    @Test
    fun `restart prompt is still shown when onFailure itself throws`() {
        databaseClosed = true

        runGuardedImport(
            onFailure = { throw IllegalStateException("toast exploded") },
            isDatabaseClosed = { databaseClosed },
            showRestartPrompt = { restartPrompted = true },
            block = { throw IllegalStateException("import error") }
        )

        assertTrue(restartPrompted)
    }
}

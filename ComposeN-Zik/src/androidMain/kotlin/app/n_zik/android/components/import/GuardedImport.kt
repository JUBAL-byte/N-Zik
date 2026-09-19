package app.n_zik.android.components.import

import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * Runs [block] of a backup import, which may close the database before replacing its file.
 *
 * A failed import that already closed the database leaves the app running on it, and every screen
 * that queries it would crash: the restart prompt is shown so the user restarts instead.
 * [CancellationException] is rethrown untouched.
 *
 * @param onFailure Tells the user the import failed (log, toast). Never allowed to prevent
 *   the restart prompt: any exception it throws is caught and logged.
 * @param isDatabaseClosed Read after the failure, because [block] is what may have closed it
 * @param showRestartPrompt Asks the user to restart the app
 */
internal inline fun runGuardedImport(
    onFailure: (Exception) -> Unit,
    isDatabaseClosed: () -> Boolean,
    showRestartPrompt: () -> Unit,
    block: () -> Unit
) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        runCatching { onFailure(e) }
            .onFailure { Timber.tag("GuardedImport").e(it, "onFailure handler crashed") }
        if (isDatabaseClosed()) showRestartPrompt()
    }
}

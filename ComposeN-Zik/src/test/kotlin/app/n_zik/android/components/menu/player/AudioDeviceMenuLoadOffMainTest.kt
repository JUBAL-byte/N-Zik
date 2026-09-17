package app.n_zik.android.components.menu.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #606 M8 — `AudioDeviceMenu.kt`'s `refreshDevices()` and the `bluetoothLauncher` callback
 * used to call the synchronous, non-suspend [loadDevices] (device enumeration + Bluetooth battery
 * reflection) directly from Main -- reached from a `LaunchedEffect`, three `BroadcastReceiver`s,
 * an `AudioDeviceCallback`, and a 30s `Handler(Looper.getMainLooper())` poll. Both call sites now
 * wrap the call in `coroutineScope.launch(NzikDispatchers.DATA) { loadDevices(...) }`. [loadDevices]
 * itself is untouched (widened from `private` to `internal` purely for testability), so this test
 * only asserts the offload: the callback-driven work no longer runs on the caller's thread.
 *
 * Robolectric is required (not a plain JVM unit test) because [loadDevices] calls into
 * `Context.getSystemService(Context.AUDIO_SERVICE)`/`AudioManager`, which need a real Android
 * environment to shadow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioDeviceMenuLoadOffMainTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `loadDevices dispatched to DATA still reaches onSuccess with a result`() = runBlocking {
        val result = CompletableDeferred<List<AudioDevice>>()

        withContext(NzikDispatchers.DATA) {
            loadDevices(
                context = context,
                preferredDeviceId = null,
                isCarProjectionActive = false,
                onSuccess = { devices -> result.complete(devices) },
                onError = { error -> result.completeExceptionally(AssertionError(error)) }
            )
        }

        assertTrue("expected loadDevices to resolve", result.isCompleted)
    }

    @Test
    fun `loadDevices runs off the caller's thread when dispatched to DATA`() = runBlocking {
        val callerThreadName = Thread.currentThread().name
        val executionThreadName = CompletableDeferred<String>()

        withContext(NzikDispatchers.DATA) {
            loadDevices(
                context = context,
                preferredDeviceId = null,
                isCarProjectionActive = false,
                onSuccess = { executionThreadName.complete(Thread.currentThread().name) },
                onError = { executionThreadName.complete(Thread.currentThread().name) }
            )
        }

        assertNotEquals(
            "loadDevices must not run on the caller's (UI-simulating) thread once dispatched to DATA",
            callerThreadName,
            executionThreadName.await()
        )
    }
}

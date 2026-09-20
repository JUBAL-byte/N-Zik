package app.n_zik.android.extensions.discord

import android.content.Context
import androidx.media3.common.MediaItem
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import com.metrolist.music.discordrpc.DiscordRpcConnection
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Regression tests for the Discord "Enable browsing" setting being randomly ignored
 * (bmad-cis-problem-solving 2026-09-20). The manager must:
 * - never send a browsing presence while the setting is off,
 * - re-validate the setting right before the RPC write (an in-flight write started
 *   while browsing was on must be cancelled when it is disabled before the write),
 * - keep hasActiveMediaItem updated even when the token/network early-return fires,
 *   so later route changes cannot resurrect a browsing status on an outdated flag,
 * - clear on toggle-off and re-send on toggle-on when no media item is active.
 */
class DiscordBrowsingSettingTest {

    private val managers = mutableListOf<DiscordPresenceManager>()

    @AfterEach
    fun tearDown() {
        managers.forEach { it.onStop() }
        managers.clear()
        unmockkAll()
        DiscordUiState.currentRoute.value = null
    }

    private fun newManager(
        dispatcher: TestDispatcher,
        connection: DiscordRpcConnection,
        browsingEnabled: () -> Boolean,
        networkAvailable: Boolean = true,
        tokenValidator: (suspend (String) -> Boolean?)? = null,
    ): DiscordPresenceManager {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkAvailable(any()) } returns networkAvailable
        val manager = DiscordPresenceManager(
            context = mockk<Context>(relaxed = true),
            getToken = { "test-token" },
            getBrowsingEnabled = browsingEnabled,
            externalScope = CoroutineScope(dispatcher),
            connectionFactory = { connection },
            tokenValidator = tokenValidator ?: { true }
        )
        managers += manager
        return manager
    }

    private fun mediaItem(id: String = "dQw4w9WgXcQ") = MediaItem.Builder().setMediaId(id).build()

    /** Asserts how many times the browsing activity specifically was written to the gateway. */
    private fun assertBrowsingWrites(connection: DiscordRpcConnection, times: Int) {
        coVerify(exactly = times) {
            connection.setActivity(
                any(),
                any(),
                any(),
                "Browsing",
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `browsing disabled, route change never sends a browsing presence`() = runTest {
        val dispatcher = UnconfinedTestDispatcher()
        val connection = mockk<DiscordRpcConnection>(relaxed = true)
        var browsingEnabled = false
        newManager(dispatcher, connection, { browsingEnabled })

        DiscordUiState.currentRoute.value = "home"

        assertBrowsingWrites(connection, 0)
    }

    @Test
    fun `browsing enabled without media, route change sends the browsing presence`() = runTest {
        val dispatcher = UnconfinedTestDispatcher()
        val connection = mockk<DiscordRpcConnection>(relaxed = true)
        newManager(dispatcher, connection, { true })

        DiscordUiState.currentRoute.value = "home"

        assertBrowsingWrites(connection, 1)
    }

    @Test
    fun `browsing enabled with active media, route change never sends browsing`() = runTest {
        val dispatcher = UnconfinedTestDispatcher()
        val connection = mockk<DiscordRpcConnection>(relaxed = true)
        val manager = newManager(dispatcher, connection, { true })

        manager.onPlayingStateChanged(mediaItem(), isPlaying = true, position = 10_000, duration = 200_000)
        DiscordUiState.currentRoute.value = "home"

        assertBrowsingWrites(connection, 0)
    }

    @Test
    fun `browsing write in flight is cancelled when the setting is disabled before the RPC write`() = runTest {
        val dispatcher = UnconfinedTestDispatcher()
        val connection = mockk<DiscordRpcConnection>(relaxed = true)
        var browsingEnabled = true
        val validationGate = CompletableDeferred<Unit>()
        newManager(
            dispatcher,
            connection,
            { browsingEnabled },
            tokenValidator = { validationGate.await(); true }
        )

        DiscordUiState.currentRoute.value = "home"
        // The write is still parked inside token validation: nothing has been sent yet.
        assertBrowsingWrites(connection, 0)

        browsingEnabled = false
        validationGate.complete(Unit)

        assertBrowsingWrites(connection, 0)
    }

    @Test
    fun `media flag survives a network early-return, no browsing sent on the next route change`() = runTest {
        val dispatcher = UnconfinedTestDispatcher()
        val connection = mockk<DiscordRpcConnection>(relaxed = true)
        var browsingEnabled = false
        val manager = newManager(dispatcher, connection, { browsingEnabled }, networkAvailable = false)

        // Network down at the media event: the manager early-returns, but the flag
        // must still reflect the active media item.
        manager.onPlayingStateChanged(mediaItem(), isPlaying = true)

        browsingEnabled = true
        DiscordUiState.currentRoute.value = "home"

        assertBrowsingWrites(connection, 0)
    }

    @Test
    fun `toggle handler clears when disabled and re-sends when re-enabled without media`() = runTest {
        val dispatcher = UnconfinedTestDispatcher()
        val connection = mockk<DiscordRpcConnection>(relaxed = true)
        var browsingEnabled = true
        val manager = newManager(dispatcher, connection, { browsingEnabled })
        DiscordUiState.currentRoute.value = "home"
        assertBrowsingWrites(connection, 1)

        browsingEnabled = false
        manager.onBrowsingSettingChanged()
        coVerify(exactly = 1) { connection.clearActivity(any()) }
        assertBrowsingWrites(connection, 1)

        browsingEnabled = true
        manager.onBrowsingSettingChanged()
        assertBrowsingWrites(connection, 2)
    }
}

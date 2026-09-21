package app.n_zik.android.extensions.discord

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import com.metrolist.music.discordrpc.DiscordRpcConnection
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Item 7: two inactivity timers re-armed by every media event —
 * - 60 s of pause with no event → [DiscordRpcConnection.clearActivity] (connection kept, a
 *   resume is cheap),
 * - 10 min with no event at all → [DiscordRpcConnection.closeDirect] (terminal in the module).
 *   The next event must then create a FRESH connection (same pattern as the token change)
 *   and re-send the presence on it.
 */
class DiscordInactivityTimersTest {

    private val managers = mutableListOf<DiscordPresenceManager>()
    private val createdConnections = mutableListOf<DiscordRpcConnection>()

    @AfterEach
    fun tearDown() {
        managers.forEach { it.onStop() }
        managers.clear()
        createdConnections.clear()
        unmockkAll()
        DiscordUiState.currentRoute.value = null
    }

    private fun newManager(
        dispatcher: TestDispatcher,
        networkAvailable: () -> Boolean = { true },
    ): DiscordPresenceManager {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkAvailable(any()) } answers { networkAvailable() }
        val manager = DiscordPresenceManager(
            context = discordTestContext(),
            getToken = { "test-token" },
            getAdvancedSettings = { DiscordAdvancedSettings.DEFAULTS },
            externalScope = CoroutineScope(dispatcher),
            connectionFactory = {
                val connection = mockk<DiscordRpcConnection>(relaxed = true)
                // The manager watches both gateway signals on every connection it creates.
                every { connection.reconnectAbandoned } returns MutableStateFlow(false)
                every { connection.terminalCloseCode } returns MutableStateFlow(null)
                createdConnections += connection
                connection
            },
            tokenValidator = { true }
        )
        managers += manager
        return manager
    }

    private fun mediaItem(
        id: String = "dQw4w9WgXcQ",
        title: String = "Song",
        artist: String = "Artist",
        album: String = "Album",
    ) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()

    private fun requireSingleConnection(): DiscordRpcConnection {
        assertEquals(1, createdConnections.size, "exactly one connection expected so far")
        return createdConnections.first()
    }

    @Test
    fun `paused 60 s with no event clears the activity, connection kept`() = runTest {
        val manager = newManager(UnconfinedTestDispatcher(testScheduler))
        try {
            manager.onPlayingStateChanged(mediaItem(), isPlaying = false, position = 10_000, duration = 100_000)
            advanceTimeBy(5_001) // the debounced paused presence (t = 5001: the test scheduler
            // only runs events strictly before now + d)

            val connection = requireSingleConnection()
            coVerify(exactly = 1) {
                connection.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
            // The paused line keeps the frozen representation.
            val details = slot<String>()
            coVerify {
                connection.setActivity(
                    any(), any(), any(), capture(details), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
            assertEquals("⏸︎ Paused: Song", details.captured)
            // Item 10: the connection created by the write path emptied the artwork cache.
            coVerify(exactly = 1) { connection.clearArtworkCache() }

            advanceTimeBy(55_000) // t = 60001 > 60000: the pause-clear deadline, no event in between
            coVerify(exactly = 1) { connection.clearActivity(any()) }
            coVerify(exactly = 0) { connection.closeDirect() } // the connection is kept (onStop not called yet)
        } finally {
            manager.onStop()
        }
    }

    @Test
    fun `idle 10 min closes the connection, the next event re-creates it and re-sends presence`() = runTest {
        val manager = newManager(UnconfinedTestDispatcher(testScheduler))
        try {
            manager.onPlayingStateChanged(mediaItem(), isPlaying = false, position = 10_000, duration = 100_000)
            advanceTimeBy(600_001) // t = 600001 > 600000: the 60 s pause clear (t = 60 s) then the idle close

            val first = requireSingleConnection()
            coVerify(exactly = 1) { first.clearActivity(any()) }
            coVerify(exactly = 1) { first.closeDirect() } // the idle timer closed it (onStop not called yet)
            coVerify(exactly = 1) {
                first.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }

            // The module close is terminal: the next media event (same token) must create a brand-new
            // connection and re-send the presence on it.
            manager.onPlayingStateChanged(mediaItem(), isPlaying = false, position = 10_000, duration = 100_000)
            advanceTimeBy(5_001)

            assertEquals(2, createdConnections.size, "a fresh connection must be created after the idle close")
            val second = createdConnections[1]
            coVerify(exactly = 2) { first.closeDirect() } // the stale connection is closed before the new one
            // Item 10: both connections emptied the process-wide artwork cache at creation.
            coVerify(exactly = 1) { first.clearArtworkCache() }
            coVerify(exactly = 1) { second.clearArtworkCache() }
            coVerify(exactly = 1) {
                second.setActivity(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
        } finally {
            manager.onStop()
        }
    }
}

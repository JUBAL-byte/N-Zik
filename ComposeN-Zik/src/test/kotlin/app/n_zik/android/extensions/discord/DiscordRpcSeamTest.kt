package app.n_zik.android.extensions.discord

import com.metrolist.music.discordrpc.DiscordRpc
import com.metrolist.music.discordrpc.GatewayWebSocket
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * App-side lock on the [DiscordRpc.backgroundDispatcher] injection seam (issue #606, Goal G5):
 * mirrors the module's own DiscordRpcBackgroundDispatcherTest, because the standard app
 * verification path (:ComposeN-Zik:testDebugUnitTest) never invokes :discordrpc:test. The
 * host app wires the seam to NzikDispatchers.DATA in MainApplication.onCreate — these
 * assertions verify the seam the app relies on.
 */
class DiscordRpcSeamTest {

    // Captured at instance creation: each JUnit test gets a fresh instance, and every test
    // that overrides the seam restores this value in @AfterEach, so this is the module default.
    private val originalDispatcher = DiscordRpc.backgroundDispatcher

    @AfterEach
    fun restoreSeam() {
        DiscordRpc.backgroundDispatcher = originalDispatcher
    }

    @Test
    fun `seam defaults to Dispatchers IO before the host app wires it`() {
        assertSame(Dispatchers.IO, originalDispatcher,
            "standalone default must be Dispatchers.IO until MainApplication.onCreate overrides it")
    }

    @Test
    fun `GatewayWebSocket picks up the seam dispatcher in its coroutine context`() {
        val testDispatcher = UnconfinedTestDispatcher()
        DiscordRpc.backgroundDispatcher = testDispatcher

        val gateway = GatewayWebSocket(token = "t", os = "Android", browser = "b", device = "d")
        try {
            assertSame(testDispatcher, gateway.coroutineContext[ContinuationInterceptor],
                "GatewayWebSocket must run on the seam dispatcher set before construction")
        } finally {
            // The constructor allocates a Ktor HttpClient; release it (session is null here,
            // so close() only tears down the scope and jobs).
            gateway.close()
        }
    }
}

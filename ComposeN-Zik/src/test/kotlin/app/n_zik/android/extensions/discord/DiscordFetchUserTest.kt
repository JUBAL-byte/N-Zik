package app.n_zik.android.extensions.discord

import app.n_zik.android.core.network.client.NetworkClientFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Item 17 (upstream parity `fetchCurrentUser`): the display name read from `/users/@me` is
 * `global_name` (the user's nickname), falling back to `username` when absent. The avatar URL
 * is built from the hash, with the deterministic embed fallback when the user has no avatar.
 */
class DiscordFetchUserTest {

    private val client = mockk<OkHttpClient>()
    private val call = mockk<Call>()

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    /** Stubs the factory → client → call chain with a real OkHttp [Response] carrying [body]. */
    private fun stubUserResponse(body: String, code: Int = 200) {
        mockkObject(NetworkClientFactory)
        every { NetworkClientFactory.getClient() } returns client
        every { client.newCall(any()) } returns call
        val response = Response.Builder()
            .request(Request.Builder().url("https://discord.com/api/v9/users/@me").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody(null))
            .build()
        every { call.execute() } returns response
    }

    @Test
    fun `global_name wins over username when both are present`() = runTest {
        stubUserResponse(
            """{"id":"123","username":"plainuser","global_name":"Global Nick","avatar":"abc"}"""
        )

        val user = fetchDiscordUser("token")

        assertEquals("Global Nick", user?.first, "the display name must be global_name")
        assertEquals("https://cdn.discordapp.com/avatars/123/abc.png", user?.second)
    }

    @Test
    fun `username is the fallback when global_name is absent`() = runTest {
        stubUserResponse("""{"id":"42","username":"plainuser","avatar":""}""")

        val user = fetchDiscordUser("token")

        assertEquals("plainuser", user?.first)
        assertEquals("https://cdn.discordapp.com/embed/avatars/2.png", user?.second, "42 % 5 = 2")
    }

    @Test
    fun `a non-2xx response yields no user`() = runTest {
        stubUserResponse("""{"id":"123","username":"plainuser"}""", code = 401)

        assertNull(fetchDiscordUser("token"))
    }
}

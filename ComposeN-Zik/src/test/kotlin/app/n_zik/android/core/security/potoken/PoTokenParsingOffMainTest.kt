package app.n_zik.android.core.security.potoken

import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Issue #606 M15 — `PoTokenWebView.downloadAndRunBotguard()`/`onRunBotguardResult()` now wrap
 * `parseChallengeData`/`parseIntegrityTokenData` in `withContext(NzikDispatchers.MEDIA)` instead
 * of running them inline on Main (the dispatcher `makeBotguardServiceRequest`'s `handleResponseBody`
 * callback resumes on, since `PoTokenWebView.scope` is a `MainScope()` required by
 * `webView.evaluateJavascript`). Both parse functions (`JavaScriptUtil.kt`) are pure JSON
 * parsing + byte transforms with no thread affinity, so the offload must change neither their
 * result nor correctness — only which thread runs them. `webView.evaluateJavascript` itself is
 * untouched and still runs after the parse, back on the caller's (Main) dispatcher.
 */
class PoTokenParsingOffMainTest {

    private val rawChallengeData =
        """[["msg-1", null, null, "hash-1", "prog-1", "global-1", null, "blob-1"]]"""

    private val rawIntegrityTokenData = """["YWJj", 3600]"""

    @Test
    fun `parseChallengeData dispatched to MEDIA returns the same result as calling it directly`() = runBlocking {
        val direct = parseChallengeData(rawChallengeData)
        val offloaded = withContext(NzikDispatchers.MEDIA) { parseChallengeData(rawChallengeData) }

        assertEquals(direct, offloaded)
    }

    @Test
    fun `parseIntegrityTokenData dispatched to MEDIA returns the same result as calling it directly`() = runBlocking {
        val direct = parseIntegrityTokenData(rawIntegrityTokenData)
        val offloaded = withContext(NzikDispatchers.MEDIA) { parseIntegrityTokenData(rawIntegrityTokenData) }

        assertEquals(direct, offloaded)
    }

    @Test
    fun `parseChallengeData runs on a nzik-media thread when dispatched to MEDIA, not the caller's thread`() = runBlocking {
        val callerThreadName = Thread.currentThread().name

        val executionThreadName = withContext(NzikDispatchers.MEDIA) {
            parseChallengeData(rawChallengeData)
            Thread.currentThread().name
        }

        assertNotEquals(callerThreadName, executionThreadName)
        assertTrue(executionThreadName.startsWith("nzik-media-"), "expected nzik-media-* but was $executionThreadName")
    }

    @Test
    fun `parseIntegrityTokenData runs on a nzik-media thread when dispatched to MEDIA, not the caller's thread`() = runBlocking {
        val callerThreadName = Thread.currentThread().name

        val executionThreadName = withContext(NzikDispatchers.MEDIA) {
            parseIntegrityTokenData(rawIntegrityTokenData)
            Thread.currentThread().name
        }

        assertNotEquals(callerThreadName, executionThreadName)
        assertTrue(executionThreadName.startsWith("nzik-media-"), "expected nzik-media-* but was $executionThreadName")
    }
}

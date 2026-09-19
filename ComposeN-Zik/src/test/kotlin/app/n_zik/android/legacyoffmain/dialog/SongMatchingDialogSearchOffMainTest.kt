package app.n_zik.android.legacyoffmain.dialog

import app.it.fast4x.rimusic.ui.components.themed.fetchSongMatchResults
import app.n_zik.android.utils.coroutines.NzikDispatchers
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.searchPage
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Issue #606 G2 -- `SongMatchingDialog`'s `LaunchedEffect` used to run `Innertube.searchPage`
 * (network) directly on whatever dispatcher the effect resumes on (Main). It is now extracted,
 * unchanged, to [fetchSongMatchResults] (declared `internal` in `Dialog.kt`,
 * `app.it.fast4x.rimusic.*`, legacy) with the search wrapped in a single
 * `withContext(NzikDispatchers.DATA)`.
 *
 * This test file itself lives under `app.n_zik.android.*` -- not the legacy package -- per the
 * gh-606 spec's "no new file under app.it.fast4x.rimusic.*" boundary; `internal` visibility is
 * module-scoped in Kotlin, not package-scoped, so it still resolves [fetchSongMatchResults] from
 * here.
 *
 * `Dispatchers.setMain` installs a real dedicated single thread standing in for Main
 * (`== NzikDispatchers.UI`), and the dialog effect body is executed on that stand-in; the thread
 * the mocked `Innertube.searchPage` actually runs on is then asserted, proving the
 * `withContext(NzikDispatchers.DATA)` wrap is load-bearing, not just present in the source.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class SongMatchingDialogSearchOffMainTest {

    private val mainThreadDispatcher = newSingleThreadContext("song-match-dialog-test-main")

    /** The actual thread name backing [mainThreadDispatcher], probed once rather than assumed --
     *  `newSingleThreadContext`'s exact naming scheme isn't part of its public contract. */
    private lateinit var mainThreadName: String

    /** Strips the coroutines-debug `" @coroutine#N"` suffix `Thread.currentThread().name` gets
     *  when read from inside a coroutine (with the debug agent active, as under a JVM test run) --
     *  the suffix's number differs per coroutine even on the same real OS thread, so comparing raw
     *  names would flag two observations of the *same* thread as different. */
    private fun baseThreadName(name: String?) = name?.substringBefore(" @coroutine")

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(mainThreadDispatcher)
        mainThreadName = baseThreadName(
            runBlocking { withContext(mainThreadDispatcher) { Thread.currentThread().name } }
        )!!
        mockkStatic("it.fast4x.innertube.requests.SearchPageKt")
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
        mainThreadDispatcher.close()
        unmockkAll()
    }

    /**
     * MockK's suspend-mocking support unwraps any `returns` value that is itself a `kotlin.Result`
     * one level, which collides with `Innertube.searchPage`'s `Result<T>`-typed return; wrapping
     * the intended value in one extra `Result.success(...)` layer absorbs that single unwrap.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> resultAnswerFor(value: Result<T>): Result<T> =
        Result.success(value) as Result<T>

    @Test
    fun `fetchSongMatchResults returns the search page items on success`() = runBlocking {
        val songItem = Innertube.SongItem(null, null, null, null, null)
        val itemsPage = Innertube.ItemsPage(items = listOf(songItem), continuation = null)
        coEvery {
            Innertube.searchPage<Innertube.SongItem>(
                query = "query",
                params = any(),
                fromMusicShelfRendererContent = any()
            )
        } returns resultAnswerFor(Result.success(itemsPage))

        val items = fetchSongMatchResults("query")

        assertEquals(listOf(songItem), items)
    }

    @Test
    fun `fetchSongMatchResults returns an empty list when the search fails`() = runBlocking {
        coEvery {
            Innertube.searchPage<Innertube.SongItem>(
                query = "query",
                params = any(),
                fromMusicShelfRendererContent = any()
            )
        } returns resultAnswerFor(
            Result.failure<Innertube.ItemsPage<Innertube.SongItem>>(RuntimeException("network down"))
        )

        val items = fetchSongMatchResults("query")

        assertEquals(emptyList<Innertube.SongItem?>(), items)
    }

    @Test
    fun `fetchSongMatchResults runs the search off the Main stand-in thread`() = runBlocking {
        var searchThreadName: String? = null
        coEvery {
            Innertube.searchPage<Innertube.SongItem>(
                query = "query",
                params = any(),
                fromMusicShelfRendererContent = any()
            )
        } answers {
            searchThreadName = Thread.currentThread().name
            resultAnswerFor(
                Result.success(
                    Innertube.ItemsPage<Innertube.SongItem>(items = emptyList(), continuation = null)
                )
            )
        }

        withContext(NzikDispatchers.UI) {
            fetchSongMatchResults("query")
        }

        assertTrue(searchThreadName != null, "expected Innertube.searchPage to have been observed")
        assertNotEquals(
            "the search call must run on NzikDispatchers.DATA, not on the Main stand-in thread",
            mainThreadName,
            baseThreadName(searchThreadName)
        )
    }
}

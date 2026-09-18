package app.n_zik.android.components.player.lyrics

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.LyricsTable
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics
import com.metrolist.music.betterlyrics.BetterLyrics
import com.metrolist.music.betterlyrics.TTMLParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import it.fast4x.kugou.KuGou
import it.fast4x.lrclib.LrcLib
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class LyricsFetchWorkerTest {

    private val context = mockk<Context>(relaxed = true)
    private val metadata = MediaMetadata.Builder()
        .setTitle("Test Title")
        .setArtist("Test Artist")
        .build()

    private val lyricsDao = mockk<LyricsTable>()
    private val albumDao = mockk<AlbumTable>()
    private val upserts = mutableListOf<Lyrics>()

    private val lyricsUpdates = mutableListOf<Lyrics?>()
    private val errorUpdates = mutableListOf<Boolean>()
    private val checkedLrc = mutableListOf<Boolean>()
    private val checkedKugou = mutableListOf<Boolean>()
    private val checkedInnertube = mutableListOf<Boolean>()
    private val fetchingStates = mutableListOf<Boolean>()

    // LrcLib.lyrics()/lyricsUnsynced(), KuGou.lyrics() and Innertube.lyrics() are `suspend fun`s
    // whose own declared return type is `kotlin.Result<T>`. MockK cannot mock a suspend function
    // that returns Result<T> reliably (a known ABI-level limitation: the coroutine continuation's
    // own Result and the function's declared Result<T> get confused, corrupting the value at
    // runtime with a ClassCastException) -- confirmed here even with `coAnswers`, not just a bare
    // `returns null`. LyricsFetchWorker takes these three calls as constructor-injected lambdas
    // instead (same pattern as its dispatcher parameters), so tests control them as plain values
    // and call counts below, without MockK ever touching these specific suspend functions.
    private var lrcLibLyricsResult: Result<LrcLib.Lyrics?>? = Result.success(null)
    private var lrcLibLyricsUnsyncedResult: Result<LrcLib.Lyrics?>? = Result.success(null)
    private var kuGouLyricsResult: Result<KuGou.Lyrics?>? = Result.success(null)
    private var innertubeLyricsResult: Result<String?>? = Result.success(null)

    private var lrcLibLyricsCalls = 0
    private var lrcLibLyricsUnsyncedCalls = 0
    private var kuGouLyricsCalls = 0
    private var innertubeLyricsCalls = 0

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun setupMocks() {
        mockkObject(Database)
        every { Database.lyricsTable } returns lyricsDao
        every { Database.albumTable } returns albumDao
        every { lyricsDao.findAllBySongId(any()) } returns flowOf(emptyList())
        every { lyricsDao.findBySongIdAndType(any(), any()) } returns flowOf(null)
        every { lyricsDao.upsert(any()) } answers {
            upserts += firstArg<Lyrics>()
            1L
        }
        every { albumDao.findBySongId(any()) } returns flowOf(null)

        mockkObject(BetterLyrics)
        coEvery { BetterLyrics.fetchTTML(any(), any(), any(), any()) } returns null

        lrcLibLyricsResult = Result.success(null)
        lrcLibLyricsUnsyncedResult = Result.success(null)
        kuGouLyricsResult = Result.success(null)
        innertubeLyricsResult = Result.success(null)
        lrcLibLyricsCalls = 0
        lrcLibLyricsUnsyncedCalls = 0
        kuGouLyricsCalls = 0
        innertubeLyricsCalls = 0
    }

    private fun resetGlobals() {
        globalLastKaraokeAttemptMediaId = null
        globalLastSyncedAttemptMediaId = null
        globalLastUnSyncedAttemptMediaId = null
    }

    private fun worker(
        scheduler: TestCoroutineScheduler,
        mediaDispatcher: CoroutineDispatcher = StandardTestDispatcher(scheduler)
    ) = LyricsFetchWorker(
        dataDispatcher = StandardTestDispatcher(scheduler),
        mediaDispatcher = mediaDispatcher,
        uiDispatcher = StandardTestDispatcher(scheduler),
        lrcLibLyrics = { _, _, _, _ -> lrcLibLyricsCalls++; lrcLibLyricsResult },
        lrcLibLyricsUnsynced = { _, _, _, _ -> lrcLibLyricsUnsyncedCalls++; lrcLibLyricsUnsyncedResult },
        kuGouLyrics = { _, _, _ -> kuGouLyricsCalls++; kuGouLyricsResult },
        innertubeLyrics = { _ -> innertubeLyricsCalls++; innertubeLyricsResult }
    )

    private suspend fun fetch(worker: LyricsFetchWorker, lyricsType: LyricsType) {
        worker.fetch(
            context = context,
            mediaId = "song1",
            lyricsType = lyricsType,
            artistName = "Test Artist",
            title = "Test Title",
            mediaMetadata = metadata,
            durationProvider = { 213_000L },
            playerEnableLyricsPopupMessage = false,
            onLyricsUpdated = { lyricsUpdates += it },
            onErrorUpdated = { errorUpdates += it },
            onCheckedLrcUpdated = { checkedLrc += it },
            onCheckedKugouUpdated = { checkedKugou += it },
            onCheckedInnertubeUpdated = { checkedInnertube += it },
            onFetchingStateChanged = { fetchingStates += it }
        )
    }

    @Test
    fun `unsynced path saves LrcLib unsynced lyrics and skips other sources`() = runTest {
        setupMocks()
        resetGlobals()
        lrcLibLyricsUnsyncedResult = Result.success(LrcLib.Lyrics("plain lyrics text"))

        fetch(worker(testScheduler), LyricsType.Unsynced)

        assertEquals(1, upserts.size)
        assertEquals(LyricsType.Unsynced.name, upserts.first().type)
        assertEquals("plain lyrics text", upserts.first().data)
        assertEquals(1, lrcLibLyricsUnsyncedCalls)
        assertEquals(0, kuGouLyricsCalls)
        assertEquals(0, innertubeLyricsCalls)
        coVerify(exactly = 0) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
        assertEquals(listOf(true, false), fetchingStates)
        assertEquals(null, lyricsUpdates.last())
    }

    @Test
    fun `second fetch for same song is deduplicated without network`() = runTest {
        setupMocks()
        resetGlobals()

        fetch(worker(testScheduler), LyricsType.Karaoke)
        fetch(worker(testScheduler), LyricsType.Karaoke)

        coVerify(exactly = 1) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
        assertEquals(0, upserts.size)
        assertEquals("song1", globalLastKaraokeAttemptMediaId)
    }

    @Test
    fun `cancellation during duration wait aborts chain without network calls`() = runTest {
        setupMocks()
        resetGlobals()

        val fetchJob = launch {
            worker(testScheduler).fetch(
                context = context,
                mediaId = "song1",
                lyricsType = LyricsType.Karaoke,
                artistName = "Test Artist",
                title = "Test Title",
                mediaMetadata = metadata,
                durationProvider = { C.TIME_UNSET },
                playerEnableLyricsPopupMessage = false,
                onLyricsUpdated = { lyricsUpdates += it },
                onErrorUpdated = { errorUpdates += it },
                onCheckedLrcUpdated = { checkedLrc += it },
                onCheckedKugouUpdated = { checkedKugou += it },
                onCheckedInnertubeUpdated = { checkedInnertube += it },
                onFetchingStateChanged = { fetchingStates += it }
            )
        }
        delay(300)
        fetchJob.cancelAndJoin()

        assertTrue(fetchJob.isCancelled)
        coVerify(exactly = 0) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
        assertEquals(0, lrcLibLyricsCalls)
        assertEquals(0, kuGouLyricsCalls)
        assertEquals(0, upserts.size)
    }

    @Test
    fun `karaoke path parses TTML on media dispatcher and saves karaoke lyrics`() = runTest {
        setupMocks()
        resetGlobals()

        val mediaExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "media-test") }
        val fetchThreads = CopyOnWriteArrayList<String>()
        val parseThreads = CopyOnWriteArrayList<String>()
        val lrcThreads = CopyOnWriteArrayList<String>()
        val parsedLines = listOf(
            TTMLParser.ParsedLine(
                text = "Hello world",
                startTime = 1.0,
                words = listOf(
                    TTMLParser.ParsedWord("Hello", 1.0, 1.5),
                    TTMLParser.ParsedWord("world", 1.5, 2.0)
                )
            )
        )

        mockkObject(TTMLParser)
        every { TTMLParser.parseTTML(any()) } answers {
            parseThreads += Thread.currentThread().name
            parsedLines
        }
        every { TTMLParser.toLRC(any()) } answers {
            lrcThreads += Thread.currentThread().name
            "[00:01.00]Hello world\n<Hello:1.0:1.5|world:1.5:2.0>\n"
        }
        coEvery { BetterLyrics.fetchTTML(any(), any(), any(), any()) } answers {
            fetchThreads += Thread.currentThread().name
            "<tt></tt>"
        }

        fetch(worker(testScheduler, mediaDispatcher = mediaExecutor.asCoroutineDispatcher()), LyricsType.Karaoke)

        assertEquals(1, upserts.size)
        assertEquals(LyricsType.Karaoke.name, upserts.first().type)
        assertTrue(upserts.first().data.orEmpty().contains("[00:01.00]Hello world"))

        assertEquals(listOf("media-test"), parseThreads)
        assertEquals(listOf("media-test"), lrcThreads)
        assertEquals(1, fetchThreads.size)
        assertTrue(fetchThreads[0] != "media-test", "fetchTTML must not run on the media dispatcher")

        unmockkAll()
        mediaExecutor.shutdown()
    }

    @Test
    fun `synced path saves BetterLyrics synced lyrics with real TTML parser`() = runTest {
        setupMocks()
        resetGlobals()

        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="00:00:01.000">Hello world</p>
                </div>
              </body>
            </tt>
        """.trimIndent()
        coEvery { BetterLyrics.fetchTTML(any(), any(), any(), any()) } returns ttml

        fetch(worker(testScheduler), LyricsType.Synced)

        assertEquals(1, upserts.size)
        assertEquals(LyricsType.Synced.name, upserts.first().type)
        assertTrue(upserts.first().data.orEmpty().contains("[00:01.00]Hello world"))
        assertEquals(0, lrcLibLyricsCalls)
    }

    @Test
    fun `unsynced fallback uses YouTube when LrcLib fails`() = runTest {
        setupMocks()
        resetGlobals()
        lrcLibLyricsUnsyncedResult = Result.failure(Exception("lrc down"))
        innertubeLyricsResult = Result.success("youtube lyrics")

        fetch(worker(testScheduler), LyricsType.Unsynced)

        assertEquals(1, upserts.size)
        assertEquals(LyricsType.Unsynced.name, upserts.first().type)
        assertEquals("youtube lyrics", upserts.first().data)
        assertTrue(checkedInnertube.contains(true))
    }

    @Test
    fun `synced fallback chain falls through to KuGou`() = runTest {
        setupMocks()
        resetGlobals()
        val kugouText = "[00:01.00]Hello\n[00:02.00]World"
        lrcLibLyricsResult = Result.failure(Exception("lrc down"))
        kuGouLyricsResult = Result.success(KuGou.Lyrics(kugouText))

        fetch(worker(testScheduler), LyricsType.Synced)

        assertEquals(1, upserts.size)
        assertEquals(LyricsType.Synced.name, upserts.first().type)
        assertEquals(kugouText, upserts.first().data)
        assertEquals(0, innertubeLyricsCalls)
    }

    @Test
    fun `edited lyrics already stored trigger no network call and no write`() = runTest {
        setupMocks()
        resetGlobals()
        val edited = Lyrics("song1", LyricsType.Unsynced.name, "my own text", isEdited = true)
        every { lyricsDao.findAllBySongId(any()) } returns flowOf(listOf(edited))

        fetch(worker(testScheduler), LyricsType.Auto)

        assertEquals(0, lrcLibLyricsCalls)
        assertEquals(0, lrcLibLyricsUnsyncedCalls)
        assertEquals(0, kuGouLyricsCalls)
        assertEquals(0, innertubeLyricsCalls)
        coVerify(exactly = 0) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
        assertEquals(0, upserts.size)
        assertEquals(edited, lyricsUpdates.last())
    }

    @Test
    fun `fetched lyrics do not overwrite an edit saved while the fetch was in flight`() = runTest {
        setupMocks()
        resetGlobals()
        lrcLibLyricsUnsyncedResult = Result.success(LrcLib.Lyrics("fetched text"))
        // The emission the fetch decided on held no lyrics; the edit lands before the write.
        every { lyricsDao.findBySongIdAndType("song1", LyricsType.Unsynced.name) } returns
            flowOf(Lyrics("song1", LyricsType.Unsynced.name, "my own text", isEdited = true))

        fetch(worker(testScheduler), LyricsType.Unsynced)

        assertEquals(1, lrcLibLyricsUnsyncedCalls)
        assertEquals(0, upserts.size)
    }

    @Test
    fun `fetched lyrics replace a stored row that is edited but emptied`() = runTest {
        setupMocks()
        resetGlobals()
        lrcLibLyricsUnsyncedResult = Result.success(LrcLib.Lyrics("fetched text"))
        every { lyricsDao.findBySongIdAndType("song1", LyricsType.Unsynced.name) } returns
            flowOf(Lyrics("song1", LyricsType.Unsynced.name, "", isEdited = true))

        fetch(worker(testScheduler), LyricsType.Unsynced)

        assertEquals(1, upserts.size)
        assertEquals("fetched text", upserts.first().data)
        assertEquals(false, upserts.first().isEdited)
    }
}

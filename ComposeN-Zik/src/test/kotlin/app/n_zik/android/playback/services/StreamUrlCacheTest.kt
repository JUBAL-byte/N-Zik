package app.n_zik.android.playback.services

import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class StreamUrlCacheTest {
    @Test
    fun `fresh entry is returned with request headers`() {
        var now = 1_000L
        val cache = StreamUrlCache(currentTimeMillis = { now })
        val headers = mapOf("User-Agent" to "test-client")

        cache.put("song", "https://example.com/stream", headers, "WEB_REMIX", expiresInSeconds = 10, quality = InnerTubeXAudioQuality.AUTO)
        now += 9_999L

        assertEquals(CachedStreamUrl("https://example.com/stream", headers, "WEB_REMIX"), cache.get("song", InnerTubeXAudioQuality.AUTO))
    }

    @Test
    fun `entry is not returned for a different quality`() {
        val cache = StreamUrlCache(currentTimeMillis = { 1_000L })
        val generationBeforeMismatch = cache.generation("song")

        cache.put("song", "https://example.com/high", emptyMap(), "WEB_REMIX", expiresInSeconds = 10, quality = InnerTubeXAudioQuality.HIGH)

        assertNull(cache.get("song", InnerTubeXAudioQuality.LOW))
        assertEquals(CachedStreamUrl("https://example.com/high", emptyMap(), "WEB_REMIX"), cache.get("song", InnerTubeXAudioQuality.HIGH))
        assertEquals(generationBeforeMismatch, cache.generation("song"))
    }

    @Test
    fun `put with a different quality replaces the entry`() {
        val cache = StreamUrlCache(currentTimeMillis = { 1_000L })

        cache.put("song", "https://example.com/high", emptyMap(), "WEB_REMIX", expiresInSeconds = 10, quality = InnerTubeXAudioQuality.HIGH)
        cache.put("song", "https://example.com/low", emptyMap(), "WEB_REMIX", expiresInSeconds = 10, quality = InnerTubeXAudioQuality.LOW)

        assertNull(cache.get("song", InnerTubeXAudioQuality.HIGH))
        assertEquals("https://example.com/low", cache.get("song", InnerTubeXAudioQuality.LOW)?.url)
    }

    @Test
    fun `entry is evicted at expiry boundary`() {
        var now = 1_000L
        val cache = StreamUrlCache(currentTimeMillis = { now })
        val generationBeforeExpiry = cache.generation("song")

        cache.put("song", "https://example.com/stream", emptyMap(), "WEB_REMIX", expiresInSeconds = 10, quality = InnerTubeXAudioQuality.AUTO)
        now += 10_000L

        assertNull(cache.get("song", InnerTubeXAudioQuality.AUTO))
        assertEquals(generationBeforeExpiry + 1, cache.generation("song"))
        now = 1_000L
        assertNull(cache.get("song", InnerTubeXAudioQuality.AUTO))
    }

    @Test
    fun `entry can be invalidated explicitly`() {
        val cache = StreamUrlCache(currentTimeMillis = { 1_000L })
        cache.put("song", "https://example.com/stream", emptyMap(), "WEB_REMIX", expiresInSeconds = 10, quality = InnerTubeXAudioQuality.AUTO)

        cache.invalidate("song")

        assertNull(cache.get("song", InnerTubeXAudioQuality.AUTO))
    }

    @Test
    fun `least recently used entry is evicted at capacity`() {
        val cache = StreamUrlCache(maxEntries = 2, currentTimeMillis = { 1_000L })
        cache.put("first", "https://example.com/first", emptyMap(), "WEB_REMIX", expiresInSeconds = 10, quality = InnerTubeXAudioQuality.AUTO)
        cache.put("second", "https://example.com/second", emptyMap(), "WEB_REMIX", expiresInSeconds = 10, quality = InnerTubeXAudioQuality.AUTO)
        assertEquals("https://example.com/first", cache.get("first", InnerTubeXAudioQuality.AUTO)?.url)

        cache.put("third", "https://example.com/third", emptyMap(), "WEB_REMIX", expiresInSeconds = 10, quality = InnerTubeXAudioQuality.AUTO)

        assertNull(cache.get("second", InnerTubeXAudioQuality.AUTO))
        assertEquals("https://example.com/first", cache.get("first", InnerTubeXAudioQuality.AUTO)?.url)
        assertEquals("https://example.com/third", cache.get("third", InnerTubeXAudioQuality.AUTO)?.url)
    }

    @Test
    fun `concurrent access remains consistent`() {
        val cache = StreamUrlCache(maxEntries = 32, currentTimeMillis = { 1_000L })
        val executor = Executors.newFixedThreadPool(8)

        try {
            val tasks =
                (0 until 1_000).map { index ->
                    Callable {
                        val mediaId = "song-${index % 32}"
                        val url = "https://example.com/$index"
                        cache.put(mediaId, url, emptyMap(), "WEB_REMIX", expiresInSeconds = 10, quality = InnerTubeXAudioQuality.AUTO)
                        cache.get(mediaId, InnerTubeXAudioQuality.AUTO)
                        if (index % 5 == 0) cache.invalidate(mediaId)
                    }
                }

            executor.invokeAll(tasks).forEach { it.get() }
            cache.put("final", "https://example.com/final", emptyMap(), "WEB_REMIX", expiresInSeconds = 10, quality = InnerTubeXAudioQuality.AUTO)

            assertEquals("https://example.com/final", cache.get("final", InnerTubeXAudioQuality.AUTO)?.url)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `invalidation rejects a stale in-flight write`() {
        val cache = StreamUrlCache(currentTimeMillis = { 1_000L })
        val generationBeforeResolution = cache.generation("song")

        cache.invalidate("song")
        val inserted = cache.put(
            mediaId = "song",
            url = "https://example.com/stale",
            requestHeaders = emptyMap(),
            clientName = "WEB_REMIX",
            expiresInSeconds = 10,
            quality = InnerTubeXAudioQuality.AUTO,
            expectedGeneration = generationBeforeResolution,
        )

        assertEquals(false, inserted)
        assertNull(cache.get("song", InnerTubeXAudioQuality.AUTO))
    }

    @Test
    fun `invalidation does not reject another media item write`() {
        val cache = StreamUrlCache(currentTimeMillis = { 1_000L })
        val firstGeneration = cache.generation("first")

        cache.invalidate("second")
        val inserted = cache.put(
            mediaId = "first",
            url = "https://example.com/first",
            requestHeaders = emptyMap(),
            clientName = "WEB_REMIX",
            expiresInSeconds = 10,
            quality = InnerTubeXAudioQuality.AUTO,
            expectedGeneration = firstGeneration,
        )

        assertEquals(true, inserted)
        assertEquals("https://example.com/first", cache.get("first", InnerTubeXAudioQuality.AUTO)?.url)
    }

    @Test
    fun `cache preserves bounded range policy`() {
        val cache = StreamUrlCache(currentTimeMillis = { 1_000L })
        cache.put(
            mediaId = "song",
            url = "https://example.com/resolved",
            requestHeaders = mapOf("User-Agent" to "test-client"),
            clientName = "IOS",
            expiresInSeconds = 10,
            requireBoundedRange = true,
            rangeChunkSizeBytes = 1_024,
            quality = InnerTubeXAudioQuality.HIGH,
        )

        val stream = requireNotNull(cache.get("song", InnerTubeXAudioQuality.HIGH))
        assertEquals(true, stream.requireBoundedRange)
        assertEquals(1_024, stream.rangeChunkSizeBytes)
        assertEquals("test-client", stream.requestHeaders["User-Agent"])
    }
}

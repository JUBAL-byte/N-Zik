package app.n_zik.android.core.rescue

import app.n_zik.android.components.dialog.export.ExportSettingsDialog
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Unit tests for the parts of [RescueFiles] that do not need the Android framework.
 *
 * Anything needing a real [android.content.Context], [android.net.Uri] or
 * [android.database.sqlite.SQLiteDatabase] is out of scope for pure JUnit5 tests. This file covers:
 * SQLite header validation, the settings CSV round trip, encrypted key routing, and the
 * file-based cores of the destructive actions (clear cache, delete downloads) on temp dirs.
 */
class RescueFilesTest {

    // ──────────────────────────────────────────────────────────────────────
    // SQLite header validation
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `valid SQLite header is accepted`() {
        val header = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        assertTrue(RescueFiles.isValidSqliteHeader(header))
    }

    @Test
    fun `header with extra bytes after magic is accepted`() {
        val header = "SQLite format 3\u0000EXTRA_DATA_HERE".toByteArray(Charsets.US_ASCII)
        assertTrue(RescueFiles.isValidSqliteHeader(header))
    }

    @Test
    fun `non-SQLite header is rejected`() {
        val header = "This is not SQL".toByteArray(Charsets.US_ASCII)
        assertFalse(RescueFiles.isValidSqliteHeader(header))
    }

    @Test
    fun `empty header is rejected`() {
        assertFalse(RescueFiles.isValidSqliteHeader(ByteArray(0)))
    }

    @Test
    fun `short header is rejected`() {
        val header = "SQLite".toByteArray(Charsets.US_ASCII)
        assertFalse(RescueFiles.isValidSqliteHeader(header))
    }

    @Test
    fun `random binary data is rejected`() {
        val header = ByteArray(16) { (it * 37).toByte() }
        assertFalse(RescueFiles.isValidSqliteHeader(header))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Settings CSV round trip (writer + reader used by export/import)
    // ──────────────────────────────────────────────────────────────────────

    private fun roundTrip(entries: List<Triple<String, String, Any>>): List<Triple<String, String, String>> {
        val out = ByteArrayOutputStream()
        RescueFiles.writeSettingsCsv(out, entries)
        return RescueFiles.readSettingsCsv(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun `plain entries round trip`() {
        val result = roundTrip(
            listOf(
                Triple("String", "language", "en"),
                Triple("Int", "count", 42),
                Triple("Boolean", "flag", true)
            )
        )
        assertEquals(
            listOf(
                Triple("String", "language", "en"),
                Triple("Int", "count", "42"),
                Triple("Boolean", "flag", "true")
            ),
            result
        )
    }

    @Test
    fun `values with comma quote and line break round trip`() {
        // Multi-line values are exactly what a line-by-line parser breaks on.
        val original = "a \"complex\", value\nwith newline"
        val result = roundTrip(listOf(Triple("String", "key", original)))
        assertEquals(listOf(Triple("String", "key", original)), result)
    }

    @Test
    fun `empty value is preserved`() {
        val result = roundTrip(listOf(Triple("String", "key", "")))
        assertEquals(listOf(Triple("String", "key", "")), result)
    }

    @Test
    fun `header only file yields no rows`() {
        val rows = RescueFiles.readSettingsCsv(ByteArrayInputStream("Type,Key,Value\r\n".toByteArray()))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `rows without a key are dropped`() {
        val csv = "Type,Key,Value\r\nString,,orphan\r\nString,kept,v\r\n"
        val rows = RescueFiles.readSettingsCsv(ByteArrayInputStream(csv.toByteArray()))
        assertEquals(listOf(Triple("String", "kept", "v")), rows)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Encrypted key routing
    // ──────────────────────────────────────────────────────────────────────

    private val allKeysMap: Map<String, Any?> = RescueFiles.ALL_ENCRYPTED_KEYS.associateWith { "x" }

    private fun credentialKeys(ytb: Boolean, discord: Boolean, lastfm: Boolean): Set<String> =
        ExportSettingsDialog.buildCredentialEntries(allKeysMap, ytb, discord, lastfm)
            .map { it.second }
            .toSet()

    @Test
    fun `YouTube keys match the regular settings export`() {
        assertEquals(RescueFiles.YTB_KEYS.toSet(), credentialKeys(ytb = true, discord = false, lastfm = false))
    }

    @Test
    fun `Discord keys match the regular settings export`() {
        assertEquals(RescueFiles.DISCORD_KEYS.toSet(), credentialKeys(ytb = false, discord = true, lastfm = false))
    }

    @Test
    fun `Last fm keys match the regular settings export`() {
        assertEquals(RescueFiles.LASTFM_KEYS.toSet(), credentialKeys(ytb = false, discord = false, lastfm = true))
    }

    @Test
    fun `Last fm session key is treated as encrypted`() {
        assertTrue(lastfmSessionKey in RescueFiles.ALL_ENCRYPTED_KEYS)
    }

    @Test
    fun `normal preference keys are not encrypted`() {
        assertFalse("languageApp" in RescueFiles.ALL_ENCRYPTED_KEYS)
        assertFalse("persistentQueue" in RescueFiles.ALL_ENCRYPTED_KEYS)
        assertFalse("skipSilence" in RescueFiles.ALL_ENCRYPTED_KEYS)
    }

    @Test
    fun `YTB DISCORD and LASTFM key lists are disjoint`() {
        val ytb = RescueFiles.YTB_KEYS.toSet()
        val discord = RescueFiles.DISCORD_KEYS.toSet()
        val lastfm = RescueFiles.LASTFM_KEYS.toSet()

        assertTrue((ytb intersect discord).isEmpty(), "YTB and Discord keys overlap")
        assertTrue((ytb intersect lastfm).isEmpty(), "YTB and Last.fm keys overlap")
        assertTrue((discord intersect lastfm).isEmpty(), "Discord and Last.fm keys overlap")
    }

    @Test
    fun `ALL_ENCRYPTED_KEYS is the union of all three groups`() {
        val expected = (RescueFiles.YTB_KEYS + RescueFiles.DISCORD_KEYS + RescueFiles.LASTFM_KEYS).toSet()
        assertEquals(expected, RescueFiles.ALL_ENCRYPTED_KEYS.toSet())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Clear cache / delete downloads (file-based cores)
    // ──────────────────────────────────────────────────────────────────────

    private fun File.dirWithFile(name: String): File = apply {
        mkdirs()
        File(this, name).writeText("x")
    }

    @Test
    fun `clear cache removes caches but never downloaded media`(@TempDir tmp: File) {
        val cacheDir = File(tmp, "cache").apply { mkdirs() }
        val filesDir = File(tmp, "files").apply { mkdirs() }
        val externalCacheDir = File(tmp, "external").apply { mkdirs() }

        // Caches that must go, in both possible base dirs
        File(cacheDir, "exoplayer").dirWithFile("a.bin")
        File(cacheDir, "coil").dirWithFile("img.bin")
        File(filesDir, "exoplayer").dirWithFile("a.bin")
        File(filesDir, "coil").dirWithFile("img.bin")
        File(externalCacheDir, "http_cache").dirWithFile("resp.bin")
        File(cacheDir, "temp_123.tmp").writeText("x")

        // Downloaded media that must survive, wherever it lives
        File(cacheDir, "exo_downloads").dirWithFile("song.bin")
        File(filesDir, "exo_downloads").dirWithFile("song.bin")
        File(externalCacheDir, "exo_downloads").dirWithFile("song.bin")

        // Unrelated persistent file
        File(filesDir, "persistentQueue.data").writeText("keep")

        RescueFiles.clearCacheDirs(cacheDir, filesDir, externalCacheDir)

        assertFalse(File(cacheDir, "exoplayer").exists())
        assertFalse(File(cacheDir, "coil").exists())
        assertFalse(File(filesDir, "exoplayer").exists())
        assertFalse(File(filesDir, "coil").exists())
        assertFalse(File(externalCacheDir, "http_cache").exists())
        assertFalse(File(cacheDir, "temp_123.tmp").exists())

        assertTrue(File(cacheDir, "exo_downloads/song.bin").exists())
        assertTrue(File(filesDir, "exo_downloads/song.bin").exists())
        assertTrue(File(externalCacheDir, "exo_downloads/song.bin").exists())
        assertTrue(File(filesDir, "persistentQueue.data").exists())
    }

    @Test
    fun `clear cache tolerates missing directories`(@TempDir tmp: File) {
        val deleted = RescueFiles.clearCacheDirs(File(tmp, "nope1"), File(tmp, "nope2"), null)
        assertEquals(0, deleted)
    }

    @Test
    fun `delete downloads removes media and the index database from the databases dir`(@TempDir tmp: File) {
        val cacheDir = File(tmp, "cache").apply { mkdirs() }
        val filesDir = File(tmp, "files").apply { mkdirs() }
        val databasesDir = File(tmp, "databases").apply { mkdirs() }

        File(cacheDir, "exo_downloads").dirWithFile("song.bin")
        File(filesDir, "exo_downloads").dirWithFile("song.bin")
        val index = File(databasesDir, "exoplayer_internal.db").apply { writeText("x") }
        val indexJournal = File(databasesDir, "exoplayer_internal.db-journal").apply { writeText("x") }

        // Must survive: the app database and the streaming cache
        val appDb = File(databasesDir, "data.db").apply { writeText("keep") }
        File(cacheDir, "exoplayer").dirWithFile("stream.bin")

        RescueFiles.deleteDownloadFiles(listOf(cacheDir, filesDir), index)

        assertFalse(File(cacheDir, "exo_downloads").exists())
        assertFalse(File(filesDir, "exo_downloads").exists())
        assertFalse(index.exists(), "download index must be deleted with the media")
        assertFalse(indexJournal.exists())
        assertTrue(appDb.exists())
        assertTrue(File(cacheDir, "exoplayer/stream.bin").exists())
    }
}

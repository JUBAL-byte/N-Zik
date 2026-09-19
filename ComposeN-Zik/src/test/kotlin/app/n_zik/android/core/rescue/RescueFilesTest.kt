package app.n_zik.android.core.rescue

import android.content.SharedPreferences
import app.n_zik.android.components.dialog.export.ExportSettingsDialog
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import io.mockk.mockk
import io.mockk.verify
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

    // ──────────────────────────────────────────────────────────────────────
    // Database files: reset / restore / import
    // ──────────────────────────────────────────────────────────────────────

    /** Writes a database main file, plus the side files that are given, into [dir]. */
    private fun writeDb(dir: File, main: String, wal: String? = null, shm: String? = null): File {
        dir.mkdirs()
        val db = File(dir, "data.db").apply { writeText(main) }
        wal?.let { File(dir, "data.db-wal").writeText(it) }
        shm?.let { File(dir, "data.db-shm").writeText(it) }
        return db
    }

    @Test
    fun `moving a database takes its wal and shm along and leaves no stale side file behind`(@TempDir tmp: File) {
        val live = writeDb(File(tmp, "databases"), main = "live-main", wal = "live-wal", shm = "live-shm")
        val backup = writeDb(File(tmp, "backups"), main = "old-main", wal = "old-wal")
        File(tmp, "backups/data.db-journal").writeText("stale journal of the old backup")

        RescueFiles.moveDatabaseFiles(live, backup)

        assertEquals("live-main", backup.readText())
        assertEquals("live-wal", File(tmp, "backups/data.db-wal").readText())
        assertEquals("live-shm", File(tmp, "backups/data.db-shm").readText())
        assertFalse(File(tmp, "backups/data.db-journal").exists(), "old backup's journal must not survive")
        assertFalse(live.exists())
        assertFalse(File(tmp, "databases/data.db-wal").exists())
        assertFalse(File(tmp, "databases/data.db-shm").exists())
    }

    @Test
    fun `swapping databases exchanges them completely and swapping again restores the original`(@TempDir tmp: File) {
        val live = writeDb(File(tmp, "databases"), main = "A-main", wal = "A-wal")
        val backup = writeDb(File(tmp, "backups"), main = "B-main", shm = "B-shm")

        RescueFiles.swapDatabaseFiles(live, backup)

        assertEquals("B-main", live.readText())
        assertEquals("B-shm", File(tmp, "databases/data.db-shm").readText())
        assertFalse(File(tmp, "databases/data.db-wal").exists())
        assertEquals("A-main", backup.readText())
        assertEquals("A-wal", File(tmp, "backups/data.db-wal").readText())
        assertFalse(File(tmp, "backups/data.db-shm").exists())
        assertFalse(File(tmp, "backups/data.db.swap").exists(), "parking file must be gone")

        RescueFiles.swapDatabaseFiles(live, backup)

        assertEquals("A-main", live.readText())
        assertEquals("A-wal", File(tmp, "databases/data.db-wal").readText())
        assertEquals("B-main", backup.readText())
        assertEquals("B-shm", File(tmp, "backups/data.db-shm").readText())
    }

    @Test
    fun `swapping when there is no live database just brings the backup back`(@TempDir tmp: File) {
        val live = File(tmp, "databases/data.db").apply { parentFile.mkdirs() }
        val backup = writeDb(File(tmp, "backups"), main = "B-main")

        RescueFiles.swapDatabaseFiles(live, backup)

        assertEquals("B-main", live.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun `replacing the database removes the stale side files of the old one`(@TempDir tmp: File) {
        val live = writeDb(File(tmp, "databases"), main = "old", wal = "old-wal", shm = "old-shm")
        val imported = File(tmp, "databases/data.db.import").apply { writeText("new") }

        RescueFiles.replaceDatabaseFile(imported, live)

        assertEquals("new", live.readText())
        assertFalse(File(tmp, "databases/data.db-wal").exists(), "a stale wal must never be replayed onto the new database")
        assertFalse(File(tmp, "databases/data.db-shm").exists())
        assertFalse(imported.exists())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Settings backups / reset / restore
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `settings backup then swap brings the old settings back and keeps the current ones as backup`(@TempDir tmp: File) {
        val prefsDir = File(tmp, "shared_prefs").apply { mkdirs() }
        val backupDir = File(tmp, "backups")
        File(prefsDir, "preferences.xml").writeText("before")
        File(prefsDir, "secure_preferences.xml").writeText("secure-before")

        RescueFiles.backupSettingsFiles(prefsDir, backupDir)
        // The app then runs with other settings and no secure file
        File(prefsDir, "preferences.xml").writeText("after")
        File(prefsDir, "secure_preferences.xml").delete()

        RescueFiles.swapSettingsFiles(prefsDir, backupDir)

        assertEquals("before", File(prefsDir, "preferences.xml").readText())
        assertEquals("secure-before", File(prefsDir, "secure_preferences.xml").readText())
        assertEquals("after", File(backupDir, "preferences.xml").readText())
        assertFalse(File(backupDir, "secure_preferences.xml").exists(), "there was no live secure file to keep")
        assertFalse(File(backupDir, "preferences.xml.swap").exists(), "parking file must be gone")
    }

    @Test
    fun `backing up settings leaves no stale copy of a file that no longer exists`(@TempDir tmp: File) {
        val prefsDir = File(tmp, "shared_prefs").apply { mkdirs() }
        val backupDir = File(tmp, "backups").apply { mkdirs() }
        File(backupDir, "secure_preferences.xml").writeText("stale")
        File(prefsDir, "preferences.xml").writeText("current")

        RescueFiles.backupSettingsFiles(prefsDir, backupDir)

        assertEquals("current", File(backupDir, "preferences.xml").readText())
        assertFalse(File(backupDir, "secure_preferences.xml").exists())
    }

    @Test
    fun `deleting backups removes the whole directory and tolerates a missing one`(@TempDir tmp: File) {
        val backupDir = File(tmp, "rescue_backups").apply { mkdirs() }
        File(backupDir, "data.db").writeText("x")
        File(backupDir, "nested").apply { mkdirs() }

        RescueFiles.deleteBackupDir(backupDir)
        assertFalse(backupDir.exists())

        RescueFiles.deleteBackupDir(backupDir) // already gone: no exception
    }

    // ──────────────────────────────────────────────────────────────────────
    // Settings import routing (credentials vs regular settings)
    // ──────────────────────────────────────────────────────────────────────

    private fun editor() = mockk<SharedPreferences.Editor>(relaxed = true)

    @Test
    fun `credential keys go to the encrypted editor and the rest to the plain one`() {
        val plain = editor()
        val encrypted = editor()
        val rows = listOf(
            Triple("String", "ytCookie", "SECRET"),
            Triple("Boolean", "persistentQueue", "true"),
            Triple("Int", "count", "7"),
            Triple("Long", "big", "9000000000"),
            Triple("Float", "ratio", "1.5")
        )

        val stats = RescueFiles.applySettingRows(rows, plain, encrypted)

        verify { encrypted.putString("ytCookie", "SECRET") }
        verify(exactly = 0) { plain.putString("ytCookie", any()) }
        verify { plain.putBoolean("persistentQueue", true) }
        verify { plain.putInt("count", 7) }
        verify { plain.putLong("big", 9_000_000_000L) }
        verify { plain.putFloat("ratio", 1.5f) }
        assertEquals(RescueFiles.SettingsImportStats(imported = 5, encryptedSkipped = 0), stats)
    }

    @Test
    fun `credential keys are skipped and never written in clear when the encrypted store is unavailable`() {
        val plain = editor()
        val rows = listOf(
            Triple("String", "ytCookie", "SECRET"),
            Triple("String", "language", "en")
        )

        val stats = RescueFiles.applySettingRows(rows, plain, null)

        verify(exactly = 0) { plain.putString("ytCookie", any()) }
        verify { plain.putString("language", "en") }
        assertEquals(RescueFiles.SettingsImportStats(imported = 1, encryptedSkipped = 1), stats)
    }

    @Test
    fun `unknown types and unparsable numbers are not counted and do not abort the import`() {
        val plain = editor()
        val rows = listOf(
            Triple("HashSet", "tags", "a"),
            Triple("Int", "bad", "abc"),
            Triple("String", "ok", "v")
        )

        val stats = RescueFiles.applySettingRows(rows, plain, null)

        assertEquals(1, stats.imported)
        verify { plain.putString("ok", "v") }
        verify(exactly = 0) { plain.putInt(any(), any()) }
    }
}

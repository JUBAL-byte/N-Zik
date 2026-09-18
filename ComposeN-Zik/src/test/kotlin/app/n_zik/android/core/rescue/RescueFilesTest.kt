package app.n_zik.android.core.rescue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [RescueFiles] pure logic that does not require Android framework.
 *
 * Tests that need [android.content.Context], [android.net.Uri], or [android.database.sqlite.SQLiteDatabase]
 * are out of scope for pure JUnit5 tests (they would need Robolectric or instrumented tests).
 * This file covers: SQLite header validation, CSV parsing/escaping, and encrypted key routing.
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
    // CSV escaping
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `simple value is not escaped`() {
        assertEquals("hello", RescueFiles.csvEscape("hello"))
    }

    @Test
    fun `value with comma is quoted`() {
        assertEquals("\"hello,world\"", RescueFiles.csvEscape("hello,world"))
    }

    @Test
    fun `value with quotes is escaped`() {
        assertEquals("\"say \"\"hi\"\"\"", RescueFiles.csvEscape("say \"hi\""))
    }

    @Test
    fun `value with newline is quoted`() {
        assertEquals("\"line1\nline2\"", RescueFiles.csvEscape("line1\nline2"))
    }

    // ──────────────────────────────────────────────────────────────────────
    // CSV parsing
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `simple CSV line is parsed correctly`() {
        val fields = RescueFiles.parseCsvLine("String,myKey,myValue")
        assertEquals(listOf("String", "myKey", "myValue"), fields)
    }

    @Test
    fun `quoted field with comma is parsed correctly`() {
        val fields = RescueFiles.parseCsvLine("String,myKey,\"value,with,commas\"")
        assertEquals(listOf("String", "myKey", "value,with,commas"), fields)
    }

    @Test
    fun `escaped quotes in field are parsed correctly`() {
        val fields = RescueFiles.parseCsvLine("String,myKey,\"say \"\"hi\"\"\"")
        assertEquals(listOf("String", "myKey", "say \"hi\""), fields)
    }

    @Test
    fun `empty fields are preserved`() {
        val fields = RescueFiles.parseCsvLine("String,,")
        assertEquals(listOf("String", "", ""), fields)
    }

    @Test
    fun `roundtrip escape then parse preserves value`() {
        val original = "a \"complex\", value\nwith newline"
        val escaped = RescueFiles.csvEscape(original)
        val parsed = RescueFiles.parseCsvLine("String,key,$escaped")
        assertEquals(original, parsed[2])
    }

    // ──────────────────────────────────────────────────────────────────────
    // Encrypted key routing
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `YouTube keys are recognized as encrypted`() {
        assertTrue("ytCookie" in RescueFiles.ALL_ENCRYPTED_KEYS)
        assertTrue("ytVisitorData" in RescueFiles.ALL_ENCRYPTED_KEYS)
        assertTrue("enableYoutubeLogin" in RescueFiles.ALL_ENCRYPTED_KEYS)
    }

    @Test
    fun `Discord keys are recognized as encrypted`() {
        assertTrue("DiscordPersonalAccessToken" in RescueFiles.ALL_ENCRYPTED_KEYS)
        assertTrue("discord_avatar" in RescueFiles.ALL_ENCRYPTED_KEYS)
    }

    @Test
    fun `Last fm keys are recognized as encrypted`() {
        assertTrue("lastfmSession" in RescueFiles.ALL_ENCRYPTED_KEYS)
        assertTrue("lastfmUsername" in RescueFiles.ALL_ENCRYPTED_KEYS)
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
}

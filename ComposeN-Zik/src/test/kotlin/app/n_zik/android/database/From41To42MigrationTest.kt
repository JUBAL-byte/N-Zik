package app.n_zik.android.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.database.migration.From41To42Migration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Direct `migrate()` test of the 41 -> 42 migration on a raw in-memory SQLite database shaped
 * exactly like the full v41 schema (frozen `41.json` fixture).
 * The migration adds a nullable `downloadQuality` column to the Format table, so existing
 * rows must survive with a NULL value (downloads made before quality tracking existed).
 * Uses JUnit 4 annotations (@Before / @After) because Robolectric's @RunWith is a JUnit 4 concept.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class From41To42MigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun createV41Database() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // in-memory
                .callback(object : SupportSQLiteOpenHelper.Callback(41) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        db = helper.writableDatabase
        applySchemaFixture(db, loadSchemaFixture(41))
    }

    @After
    fun closeDb() {
        helper.close()
    }

    @Test
    fun `existing format rows are kept and get a null downloadQuality`() {
        db.execSQL("INSERT INTO Song (id, title, totalPlayTimeMs, position, isYoutubeSong) VALUES ('s1', 't1', 0, 0, 0)")
        db.execSQL(
            "INSERT INTO Format (songId, itag, mimeType, bitrate, contentLength, lastModified) " +
                "VALUES ('s1', 251, 'audio/webm; codecs=opus', 128000, 1000, 0)"
        )

        From41To42Migration.migrate(db)

        db.query("SELECT songId, itag, mimeType, bitrate, contentLength, downloadQuality FROM Format").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("s1", c.getString(0))
            assertEquals(251, c.getInt(1))
            assertEquals("audio/webm; codecs=opus", c.getString(2))
            assertEquals(128000, c.getInt(3))
            assertEquals(1000, c.getInt(4))
            assertNull(c.getString(5))
        }
    }

    @Test
    fun `downloadQuality column is a nullable TEXT column with a NULL default`() {
        From41To42Migration.migrate(db)

        db.query("PRAGMA table_info(`Format`)").use { c ->
            var found = false
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("name")) == "downloadQuality") {
                    found = true
                    assertEquals("TEXT", c.getString(c.getColumnIndexOrThrow("type")))
                    assertEquals(0, c.getInt(c.getColumnIndexOrThrow("notnull")))
                    assertEquals("NULL", c.getString(c.getColumnIndexOrThrow("dflt_value")))
                }
            }
            assertEquals(true, found)
        }
    }

    @Test
    fun `all other v41 columns are untouched`() {
        From41To42Migration.migrate(db)

        db.query("PRAGMA table_info(`Format`)").use { c ->
            val columns = linkedMapOf<String, String>()
            while (c.moveToNext()) {
                columns[c.getString(c.getColumnIndexOrThrow("name"))] =
                    c.getString(c.getColumnIndexOrThrow("type"))
            }
            assertEquals(
                linkedMapOf(
                    "songId" to "TEXT",
                    "itag" to "INTEGER",
                    "mimeType" to "TEXT",
                    "bitrate" to "INTEGER",
                    "contentLength" to "INTEGER",
                    "lastModified" to "INTEGER",
                    "loudnessDb" to "REAL",
                    "codecs" to "TEXT",
                    "sampleRate" to "INTEGER",
                    "perceptualLoudnessDb" to "REAL",
                    "audioChannels" to "INTEGER",
                    "playbackUrl" to "TEXT",
                    "downloadQuality" to "TEXT"
                ),
                columns
            )
        }
    }
}

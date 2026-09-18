package app.n_zik.android.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.database.migration.From40To41Migration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Direct `migrate()` test of the 40 -> 41 migration on a raw in-memory SQLite database shaped
 * exactly like the full v40 schema (frozen `40.json` fixture: tables, indexes, view).
 * The migration rebuilds six tables, so every row of every table must survive.
 * Uses JUnit 4 annotations (@Before / @After) because Robolectric's @RunWith is a JUnit 4 concept.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class From40To41MigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun createV40Database() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // in-memory
                .callback(object : SupportSQLiteOpenHelper.Callback(40) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        db = helper.writableDatabase
        applySchemaFixture(db, loadSchemaFixture(40))
    }

    @After
    fun closeDb() {
        helper.close()
    }

    @Test
    fun `existing lyrics rows are kept and get a null lastFetchedAt`() {
        db.execSQL("INSERT INTO Song (id, title, totalPlayTimeMs, position, isYoutubeSong) VALUES ('s1', 't1', 0, 0, 0), ('s2', 't2', 1, 1, 1)")
        db.execSQL("INSERT INTO Lyrics (songId, type, data, isEdited) VALUES ('s1', 'Karaoke', '<k>', 0), ('s1', 'Synced', '[00:01.00]a', 1)")
        db.execSQL("INSERT INTO Lyrics (songId, type, data, isEdited) VALUES ('s2', 'Unsynced', NULL, 0)")

        From40To41Migration.migrate(db)

        db.query("SELECT songId, type, data, isEdited, lastFetchedAt FROM Lyrics ORDER BY songId, type").use { c ->
            assertEquals(3, c.count)
            c.moveToNext()
            assertEquals("s1", c.getString(0))
            assertEquals("Karaoke", c.getString(1))
            assertEquals("<k>", c.getString(2))
            assertEquals(0, c.getInt(3))
            assertNull(c.getString(4))
            c.moveToNext()
            assertEquals("Synced", c.getString(1))
            assertEquals("[00:01.00]a", c.getString(2))
            assertEquals(1, c.getInt(3))
            assertNull(c.getString(4))
            c.moveToNext()
            assertEquals("s2", c.getString(0))
            assertEquals("Unsynced", c.getString(1))
            assertNull(c.getString(2))
            assertEquals(0, c.getInt(3))
            assertNull(c.getString(4))
        }

        // Rows of a rebuilt table survive the migration.
        db.query("SELECT id, title FROM Song ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToNext()
            assertEquals("s1", c.getString(0))
            assertEquals("t1", c.getString(1))
            c.moveToNext()
            assertEquals("s2", c.getString(0))
            assertEquals("t2", c.getString(1))
        }
    }

    @Test
    fun `lastFetchedAt column is a nullable INTEGER without default`() {
        From40To41Migration.migrate(db)

        db.query("PRAGMA table_info(`Lyrics`)").use { c ->
            var found = false
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("name")) == "lastFetchedAt") {
                    found = true
                    assertEquals("INTEGER", c.getString(c.getColumnIndexOrThrow("type")))
                    assertEquals(0, c.getInt(c.getColumnIndexOrThrow("notnull")))
                    assertNull(c.getString(c.getColumnIndexOrThrow("dflt_value")))
                }
            }
            assertEquals(true, found)
        }
    }
}

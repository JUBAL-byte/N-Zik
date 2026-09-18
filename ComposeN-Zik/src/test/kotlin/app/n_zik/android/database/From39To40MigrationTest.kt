package app.n_zik.android.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.database.migration.From39To40Migration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration 39 -> 40 (gh-765) on a raw in-memory SQLite database shaped like schema v39.
 * A faulty migration would wipe the base (`fallbackToDestructiveMigration`), so it must keep every row.
 * Uses JUnit 4 annotations (@Before / @After) because Robolectric's @RunWith is a JUnit 4 concept.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class From39To40MigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun createV39Database() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // in-memory
                .callback(object : SupportSQLiteOpenHelper.Callback(39) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        db = helper.writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS `Song` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))")
        // Lyrics exactly as declared in schemas/.../39.json
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `Lyrics` (`songId` TEXT NOT NULL, `type` TEXT NOT NULL, `data` TEXT, " +
                "PRIMARY KEY(`songId`, `type`), FOREIGN KEY(`songId`) REFERENCES `Song`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
    }

    @After
    fun closeDb() {
        helper.close()
    }

    @Test
    fun `existing lyrics rows are kept and get isEdited = 0`() {
        db.execSQL("INSERT INTO Song (id) VALUES ('s1'), ('s2')")
        db.execSQL("INSERT INTO Lyrics (songId, type, data) VALUES ('s1', 'Karaoke', '<k>'), ('s1', 'Synced', '[00:01.00]a')")
        db.execSQL("INSERT INTO Lyrics (songId, type, data) VALUES ('s2', 'Unsynced', NULL)")

        From39To40Migration.migrate(db)

        db.query("SELECT songId, type, data, isEdited FROM Lyrics ORDER BY songId, type").use { c ->
            assertEquals(3, c.count)
            c.moveToNext()
            assertEquals("s1", c.getString(0))
            assertEquals("Karaoke", c.getString(1))
            assertEquals("<k>", c.getString(2))
            assertEquals(0, c.getInt(3))
            c.moveToNext()
            assertEquals("Synced", c.getString(1))
            assertEquals("[00:01.00]a", c.getString(2))
            assertEquals(0, c.getInt(3))
            c.moveToNext()
            assertEquals("s2", c.getString(0))
            assertEquals("Unsynced", c.getString(1))
            assertNull(c.getString(2))
            assertEquals(0, c.getInt(3))
        }
    }

    @Test
    fun `isEdited column matches the definition Room expects for Boolean with defaultValue 0`() {
        From39To40Migration.migrate(db)

        db.query("PRAGMA table_info(`Lyrics`)").use { c ->
            var found = false
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("name")) == "isEdited") {
                    found = true
                    assertEquals("INTEGER", c.getString(c.getColumnIndexOrThrow("type")))
                    assertEquals(1, c.getInt(c.getColumnIndexOrThrow("notnull")))
                    assertEquals("0", c.getString(c.getColumnIndexOrThrow("dflt_value")))
                }
            }
            assertEquals(true, found)
        }
    }
}

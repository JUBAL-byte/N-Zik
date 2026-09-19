package app.n_zik.android.core.rescue

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * [RescueFiles.checkpointWal] against real SQLite (Robolectric runs the native library), which
 * the pure JUnit5 tests cannot do. These pin the data-safety behavior of the database export:
 * a WAL left by a crash is merged before the copy, a database in use is refused rather than
 * exported incomplete, and a corrupt database is never deleted by the rescue.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RescueFilesCheckpointTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun rowCount(dbFile: File): Int =
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT count(*) FROM t", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }

    @Test
    fun `checkpoint folds the wal into data db so a bare copy of it holds every row`() {
        val dbFile = File(tmp.root, "data.db")
        val writer = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            writer.enableWriteAheadLogging()
            writer.execSQL("CREATE TABLE t(x INTEGER)")
            repeat(20) { writer.execSQL("INSERT INTO t VALUES ($it)") }

            // The writer is still open, as after a crash: the rows sit in the -wal, not in data.db.
            RescueFiles.checkpointWal(dbFile)

            // This is what exportDatabase streams out: data.db on its own, without its -wal.
            val bareCopy = File(tmp.root, "export.db")
            dbFile.copyTo(bareCopy)
            assertEquals(20, rowCount(bareCopy))
        } finally {
            writer.close()
        }
    }

    @Test
    fun `a database in use is refused instead of exported incomplete`() {
        val dbFile = File(tmp.root, "data.db")
        val writer = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            writer.enableWriteAheadLogging()
            writer.execSQL("CREATE TABLE t(x INTEGER)")
            writer.execSQL("INSERT INTO t VALUES (1)")

            // Another connection (the main app process, in real life) holds the WAL write lock.
            writer.beginTransactionNonExclusive()
            try {
                RescueFiles.checkpointWal(dbFile)
                fail("checkpoint must not succeed while the database is in use")
            } catch (expected: RescueFiles.DatabaseBusyException) {
                // The UI turns this into the "close the app" message.
            } finally {
                writer.endTransaction()
            }
        } finally {
            writer.close()
        }
    }

    @Test
    fun `a corrupt database is left in place, never deleted by the rescue`() {
        val dbFile = File(tmp.root, "data.db")
        val garbage = ByteArray(8192) { (it * 31).toByte() }
        dbFile.writeBytes(garbage)

        try {
            RescueFiles.checkpointWal(dbFile)
            fail("a corrupt file cannot be checkpointed")
        } catch (expected: Exception) {
            // Any failure is fine here; what matters is what is left on disk.
        }

        // SQLite's default error handler deletes the file when it reports corruption at open time.
        assertTrue("the corrupt database must not be deleted", dbFile.exists())
        assertTrue("the corrupt database must not be modified", dbFile.readBytes().contentEquals(garbage))
    }
}

package app.n_zik.android.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 41 to version 42.
 *
 * Adds the nullable `downloadQuality` column to the Format table, which tracks which download
 * quality setting was used when a song was downloaded. Existing rows keep their data and get a
 * NULL value; pre-migration downloads are therefore treated as non-compliant by the
 * "Update downloads" feature and can be re-downloaded with the selected quality.
 */
val From41To42Migration = object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Format ADD COLUMN downloadQuality TEXT DEFAULT NULL")
    }
}

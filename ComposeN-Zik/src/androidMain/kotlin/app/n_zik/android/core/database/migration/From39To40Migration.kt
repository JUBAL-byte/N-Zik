package app.n_zik.android.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val From39To40Migration = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Lyrics ADD COLUMN isEdited INTEGER NOT NULL DEFAULT 0")
    }
}

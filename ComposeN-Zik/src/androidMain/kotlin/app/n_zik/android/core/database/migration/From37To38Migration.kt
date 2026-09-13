package app.n_zik.android.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val From37To38Migration = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Artist: MusicBrainz metadata
        db.execSQL("ALTER TABLE Artist ADD COLUMN genres TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Artist ADD COLUMN artistType TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Artist ADD COLUMN countryCode TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Artist ADD COLUMN beginYear INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE Artist ADD COLUMN tags TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Artist ADD COLUMN rating REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE Artist ADD COLUMN ratingVotes INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE Artist ADD COLUMN wikipediaUrl TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Artist ADD COLUMN wikipediaBio TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Artist ADD COLUMN description TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Artist ADD COLUMN disambiguation TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Artist ADD COLUMN links TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Artist ADD COLUMN mbId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Artist ADD COLUMN youtubeChannelId TEXT DEFAULT NULL")

        // Album: MusicBrainz metadata
        db.execSQL("ALTER TABLE Album ADD COLUMN genres TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Album ADD COLUMN originalYear INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE Album ADD COLUMN albumType TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Album ADD COLUMN tags TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Album ADD COLUMN rating REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE Album ADD COLUMN ratingVotes INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE Album ADD COLUMN wikipediaUrl TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Album ADD COLUMN wikipediaInfo TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Album ADD COLUMN links TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Album ADD COLUMN mbId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE Album ADD COLUMN youtubeAlbumId TEXT DEFAULT NULL")
    }
}

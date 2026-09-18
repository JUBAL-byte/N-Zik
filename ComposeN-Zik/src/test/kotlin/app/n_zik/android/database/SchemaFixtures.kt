package app.n_zik.android.database

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject

/**
 * Loads the frozen schema fixtures (copies of `ComposeN-Zik/schemas/.../{version}.json`) from the
 * test resources and applies them to a raw SQLite connection: every table, its indexes and the
 * views. `lyricsCreateSql` overrides the Lyrics table definition (the migration adds its
 * lastFetchedAt column, so it must be seeded from the v40 fixture even when the rest comes
 * from v41).
 */
internal fun loadSchemaFixture(version: Int): JSONObject = JSONObject(
    String(
        requireNotNull(
            Room::class.java.classLoader
                .getResourceAsStream("schemas/app.n_zik.android.core.database.DatabaseInitializer/$version.json")
        ) { "v$version schema fixture is missing from the test resources" }
            .readBytes(),
        Charsets.UTF_8
    )
)

internal fun lyricsCreateSqlOf(fixture: JSONObject): String {
    val entities = fixture.getJSONObject("database").getJSONArray("entities")
    for (i in 0 until entities.length()) {
        if (entities.getJSONObject(i).getString("tableName") == "Lyrics") {
            return entities.getJSONObject(i).getString("createSql")
        }
    }
    error("Lyrics table missing from schema fixture")
}

internal fun applySchemaFixture(db: SupportSQLiteDatabase, fixture: JSONObject, lyricsCreateSql: String? = null) {
    val database = fixture.getJSONObject("database")
    val entities = database.getJSONArray("entities")
    for (i in 0 until entities.length()) {
        val table = entities.getJSONObject(i)
        val name = table.getString("tableName")
        val createSql = if (name == "Lyrics" && lyricsCreateSql != null) lyricsCreateSql else table.getString("createSql")
        db.execSQL(createSql.replace("\${TABLE_NAME}", name))
    }
    for (i in 0 until entities.length()) {
        val table = entities.getJSONObject(i)
        val indices = table.optJSONArray("indices") ?: continue
        for (j in 0 until indices.length()) {
            db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table.getString("tableName")))
        }
    }
    val views = database.getJSONArray("views")
    for (i in 0 until views.length()) {
        val view = views.getJSONObject(i)
        db.execSQL(view.getString("createSql").replace("\${VIEW_NAME}", view.getString("viewName")))
    }
}

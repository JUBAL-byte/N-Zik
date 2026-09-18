package app.n_zik.android.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import app.it.fast4x.rimusic.models.Song

@Immutable
@Entity(
    primaryKeys = ["songId", "type"],
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
class Lyrics(
    val songId: String,
    val type: String,
    val data: String?,
    /** `true` when [data] was written by the user, which automatic fetches must never replace (gh-765). */
    @ColumnInfo(defaultValue = "0")
    val isEdited: Boolean = false,
    /**
     * Epoch millis of the last fetch that wrote [data]; `null` for rows stored before the column
     * existed or after a manual "fetch lyrics again" reset. Drives the 30-day refetch TTL.
     */
    @ColumnInfo
    val lastFetchedAt: Long? = null,
)





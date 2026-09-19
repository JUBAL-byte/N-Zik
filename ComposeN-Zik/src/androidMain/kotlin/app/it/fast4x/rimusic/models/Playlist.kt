package app.it.fast4x.rimusic.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix

@Immutable
@Entity
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val browseId: String? = null,
    val isEditable: Boolean = true,
    @ColumnInfo(defaultValue = "0") val isYoutubePlaylist: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isAutoSync: Boolean = false,
    @ColumnInfo(defaultValue = "-1") val position: Int = -1
) {
    fun cleanName() = cleanPrefix( this.name )

    /**
     * Whether this playlist can be bookmarked, i.e. saved to the YouTube Music library.
     * Only playlists linked to a real YouTube Music playlist can: a purely local playlist
     * (no [browseId]) or a locally modified one has nothing on YouTube Music to save.
     */
    fun canBeBookmarked(): Boolean = browseId?.startsWith( MODIFIED_PREFIX ) == false
}




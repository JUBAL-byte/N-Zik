package app.n_zik.android.extensions.discord

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Item 6: the upstream placeholder set — `{song.name}`, `{song.id}`, `{artist.name}`,
 * `{album.name}` — is the only template surface. Pure function, no resources involved.
 */
class DiscordTemplateRendererTest {

    private companion object {
        const val UNKNOWN_ALBUM = "Unknown Album"
    }

    @Test
    fun `all four placeholders are replaced`() {
        val rendered = DiscordTemplateRenderer.render(
            template = "Now: {song.name} by {artist.name} on {album.name} ({song.id})",
            title = "Song",
            artist = "Artist",
            album = "Album",
            songId = "dQw4w9WgXcQ",
            unknownAlbum = UNKNOWN_ALBUM,
        )
        assertEquals("Now: Song by Artist on Album (dQw4w9WgXcQ)", rendered)
    }

    @Test
    fun `a missing album renders the localized unknown-album fallback`() {
        assertEquals(
            UNKNOWN_ALBUM,
            DiscordTemplateRenderer.render("{album.name}", "Song", "Artist", album = null, unknownAlbum = UNKNOWN_ALBUM),
        )
    }

    @Test
    fun `a template without placeholders is returned unchanged`() {
        assertEquals("Browsing", DiscordTemplateRenderer.render("Browsing", "Song", "Artist", "Album", "id", UNKNOWN_ALBUM))
    }

    @Test
    fun `the song id defaults to an empty string`() {
        assertEquals("Track: ", DiscordTemplateRenderer.render("Track: {song.id}", "Song", "Artist", "Album", unknownAlbum = UNKNOWN_ALBUM))
    }

    @Test
    fun `the app version placeholder is replaced`() {
        val rendered = DiscordTemplateRenderer.render(
            template = "v{app.version}",
            title = "Song",
            artist = "Artist",
            album = "Album",
            unknownAlbum = UNKNOWN_ALBUM,
            version = "2.3.4",
        )
        assertEquals("v2.3.4", rendered)
    }

    @Test
    fun `the app version defaults to an empty string`() {
        assertEquals("v", DiscordTemplateRenderer.render("v{app.version}", "Song", "Artist", "Album", unknownAlbum = UNKNOWN_ALBUM))
    }

    @Test
    fun `the placeholder chips match the supported placeholders`() {
        assertEquals(
            listOf("{song.name}", "{artist.name}", "{album.name}", "{song.id}", "{app.version}"),
            DiscordTemplateRenderer.PLACEHOLDERS,
        )
    }
}

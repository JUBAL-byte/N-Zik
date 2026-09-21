package app.n_zik.android.extensions.discord

import timber.log.Timber

/**
 * Renders Discord presence templates with the upstream placeholder set (item 6):
 * `{song.name}`, `{song.id}`, `{artist.name}`, `{album.name}`.
 *
 * Pure function — trivially unit-testable, no context or resources involved.
 */
object DiscordTemplateRenderer {

    private const val TAG = "DiscordPresence"

    /**
     * Renders the template with the given values; `unknownAlbum` is the localized
     * fallback for `{album.name}` when the album is unknown (from strings.xml —
     * no hardcoded user-facing text).
     */
    fun render(
        template: String,
        title: String,
        artist: String,
        album: String?,
        songId: String = "",
        unknownAlbum: String,
    ): String {
        val result = template
            .replace("{song.name}", title)
            .replace("{song.id}", songId)
            .replace("{artist.name}", artist)
            .replace("{album.name}", album ?: unknownAlbum)
        Timber.tag(TAG).v("Template rendered: %s -> %s", template, result)
        return result
    }

    /** Placeholders offered as chips in the template dialogs (settings UI). */
    val PLACEHOLDERS = listOf(
        "{song.name}",
        "{artist.name}",
        "{album.name}",
        "{song.id}",
    )
}

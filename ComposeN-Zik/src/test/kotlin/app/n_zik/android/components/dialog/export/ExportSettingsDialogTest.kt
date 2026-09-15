package app.n_zik.android.components.dialog.export

import app.n_zik.android.extensions.lastfm.isLastfmNowPlayingEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobbleEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobblingEnabledKey
import app.n_zik.android.extensions.lastfm.lastfmAvatarUrlKey
import app.n_zik.android.extensions.lastfm.lastfmMaxScrobbleDelaySecondsKey
import app.n_zik.android.extensions.lastfm.lastfmMinTrackDurationSecondsKey
import app.n_zik.android.extensions.lastfm.lastfmScrobbleThresholdPercentKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.extensions.lastfm.lastfmUsernameKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests [ExportSettingsDialog.buildCredentialEntries]: each credential group
 * (YouTube / Discord / Last.fm) is exported independently, only when its
 * flag is set, with the value type name as first element.
 */
class ExportSettingsDialogTest {

    private val encryptedPrefs = mapOf(
        lastfmSessionKey to "session123",
        lastfmUsernameKey to "NEVARLeVrai",
        lastfmAvatarUrlKey to "https://s.example/avatar.jpg",
        isLastfmScrobblingEnabledKey to true,
        isLastfmNowPlayingEnabledKey to true,
        isLastfmScrobbleEnabledKey to true,
        lastfmMinTrackDurationSecondsKey to 30,
        lastfmScrobbleThresholdPercentKey to 50,
        lastfmMaxScrobbleDelaySecondsKey to 50,
        discordPersonalAccessTokenKey to "discord-token"
    )

    @Test
    fun `lastfm credentials are exported only when includeLastfm is true`() {
        val without = ExportSettingsDialog.buildCredentialEntries(encryptedPrefs, false, false, false)
        val with = ExportSettingsDialog.buildCredentialEntries(encryptedPrefs, false, false, true)

        assertTrue(without.isEmpty())
        assertEquals(
            setOf(
                lastfmSessionKey, lastfmUsernameKey, lastfmAvatarUrlKey, isLastfmScrobblingEnabledKey,
                isLastfmNowPlayingEnabledKey, isLastfmScrobbleEnabledKey, lastfmMinTrackDurationSecondsKey,
                lastfmScrobbleThresholdPercentKey, lastfmMaxScrobbleDelaySecondsKey
            ),
            with.map { it.second }.toSet()
        )
    }

    @Test
    fun `each credential group is independent of the others`() {
        val discordOnly = ExportSettingsDialog.buildCredentialEntries(encryptedPrefs, false, true, false)

        assertEquals(1, discordOnly.size)
        assertEquals(discordPersonalAccessTokenKey, discordOnly.single().second)
    }

    @Test
    fun `entries carry the value type name and the raw value`() {
        val with = ExportSettingsDialog.buildCredentialEntries(encryptedPrefs, false, false, true)
        val session = with.first { it.second == lastfmSessionKey }

        assertEquals("String", session.first)
        assertEquals("session123", session.third)
    }

    @Test
    fun `missing credentials are skipped without failing the export`() {
        val with = ExportSettingsDialog.buildCredentialEntries(
            mapOf(lastfmSessionKey to "session123"),
            false,
            false,
            true
        )

        assertEquals(1, with.size)
        assertEquals(lastfmSessionKey, with.single().second)
    }
}

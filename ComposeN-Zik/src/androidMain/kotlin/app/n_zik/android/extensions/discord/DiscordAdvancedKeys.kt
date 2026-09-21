package app.n_zik.android.extensions.discord

import android.content.Context
import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.encryptedPreferences

/**
 * Keys of the advanced Discord presence settings (item 6).
 *
 * Declared in this new app file rather than in the legacy `EncryptedPreferences.kt`
 * (out of scope): all values live in the same encrypted SharedPreferences, read and
 * written through the existing `context.encryptedPreferences` helper.
 */
const val isDiscordAdvancedModeKey = "isDiscordAdvancedMode"
const val discordAdvancedActivityTypeKey = "discordAdvancedActivityType"
const val discordAdvancedPausePresenceEnabledKey = "discordAdvancedPausePresenceEnabled"
const val discordAdvancedNameKey = "discordAdvancedName"
const val discordAdvancedStateTemplateKey = "discordAdvancedStateTemplate"
const val discordAdvancedDetailsTemplateKey = "discordAdvancedDetailsTemplate"
const val discordAdvancedPauseTemplateKey = "discordAdvancedPauseTemplate"
const val discordAdvancedButton1EnabledKey = "discordAdvancedButton1Enabled"
const val discordAdvancedButton1LabelKey = "discordAdvancedButton1Label"
const val discordAdvancedButton1UrlKey = "discordAdvancedButton1Url"
const val discordAdvancedButton2EnabledKey = "discordAdvancedButton2Enabled"
const val discordAdvancedButton2LabelKey = "discordAdvancedButton2Label"
const val discordAdvancedButton2UrlKey = "discordAdvancedButton2Url"

/** All advanced keys, for the service's encrypted-prefs listener re-sync (item 6). */
val discordAdvancedSettingKeys: Set<String> = setOf(
    isDiscordAdvancedModeKey,
    discordAdvancedActivityTypeKey,
    discordAdvancedPausePresenceEnabledKey,
    discordAdvancedNameKey,
    discordAdvancedStateTemplateKey,
    discordAdvancedDetailsTemplateKey,
    discordAdvancedPauseTemplateKey,
    discordAdvancedButton1EnabledKey,
    discordAdvancedButton1LabelKey,
    discordAdvancedButton1UrlKey,
    discordAdvancedButton2EnabledKey,
    discordAdvancedButton2LabelKey,
    discordAdvancedButton2UrlKey,
)

/**
 * User status carried by every presence update: fixed to "online" (PW-2 — the
 * user-status selector was removed; Discord manages idle itself, `since = 0` is
 * always carried so the "online since" timestamp is never reset by us).
 */
const val DISCORD_STATUS_ONLINE = "online"

/**
 * Snapshot of the advanced Discord presence settings (item 6), read from the encrypted
 * prefs on demand (the fork prefs are EncryptedSharedPreferences — there is no Flow to
 * collect; the service's prefs listener triggers the re-sync instead).
 *
 * [activityType] is the module `ActivityType` code: 0=playing, 2=listening (default),
 * 3=watching, 5=competing.
 */
data class DiscordAdvancedSettings(
    val advancedMode: Boolean,
    val activityType: Int,
    val pausePresenceEnabled: Boolean,
    val activityName: String,
    val stateTemplate: String,
    val detailsTemplate: String,
    val pauseTemplate: String,
    val button1Enabled: Boolean,
    val button1Label: String,
    val button1Url: String,
    val button2Enabled: Boolean,
    val button2Label: String,
    val button2Url: String,
) {
    companion object {
        /** Defaults: mode off, listening, pause presence on, empty templates, both buttons on. */
        val DEFAULTS = DiscordAdvancedSettings(
            advancedMode = false,
            activityType = 2,
            pausePresenceEnabled = true,
            activityName = "",
            stateTemplate = "",
            detailsTemplate = "",
            pauseTemplate = "",
            button1Enabled = true,
            button1Label = "",
            button1Url = "",
            button2Enabled = true,
            button2Label = "",
            button2Url = "",
        )

        /** Reads all advanced keys at once (single prefs access per presence update). */
        fun read(prefs: SharedPreferences): DiscordAdvancedSettings = DiscordAdvancedSettings(
            advancedMode = prefs.getBoolean(isDiscordAdvancedModeKey, false),
            activityType = prefs.getInt(discordAdvancedActivityTypeKey, 2),
            pausePresenceEnabled = prefs.getBoolean(discordAdvancedPausePresenceEnabledKey, true),
            activityName = prefs.getString(discordAdvancedNameKey, "").orEmpty(),
            stateTemplate = prefs.getString(discordAdvancedStateTemplateKey, "").orEmpty(),
            detailsTemplate = prefs.getString(discordAdvancedDetailsTemplateKey, "").orEmpty(),
            pauseTemplate = prefs.getString(discordAdvancedPauseTemplateKey, "").orEmpty(),
            button1Enabled = prefs.getBoolean(discordAdvancedButton1EnabledKey, true),
            button1Label = prefs.getString(discordAdvancedButton1LabelKey, "").orEmpty(),
            button1Url = prefs.getString(discordAdvancedButton1UrlKey, "").orEmpty(),
            button2Enabled = prefs.getBoolean(discordAdvancedButton2EnabledKey, true),
            button2Label = prefs.getString(discordAdvancedButton2LabelKey, "").orEmpty(),
            button2Url = prefs.getString(discordAdvancedButton2UrlKey, "").orEmpty(),
        )

        fun read(context: Context): DiscordAdvancedSettings = read(context.encryptedPreferences)
    }
}

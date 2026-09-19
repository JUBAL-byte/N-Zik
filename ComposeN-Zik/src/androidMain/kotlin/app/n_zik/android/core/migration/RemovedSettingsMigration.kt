package app.n_zik.android.core.migration

import android.content.SharedPreferences
import timber.log.Timber

/**
 * Drops the stored values of settings that no longer exist (issue #606): the "Advanced"
 * notification type and the cover-as-wallpaper feature, which only worked through it.
 *
 * The keys are literals on purpose: their constants were deleted with the settings, and this
 * object is the only place in the code base allowed to still name them.
 *
 * Idempotent and cheap: it writes nothing once the keys are gone, and `apply()` never blocks.
 */
object RemovedSettingsMigration {
    private const val TAG = "RemovedSettingsMigration"

    internal val REMOVED_KEYS = listOf("notificationType", "enableWallpaper", "wallpaperType")

    /**
     * @return `true` if at least one obsolete key was present and has been removed.
     */
    fun run(preferences: SharedPreferences): Boolean {
        val present = REMOVED_KEYS.filter(preferences::contains)
        if (present.isEmpty()) return false

        preferences.edit().apply { present.forEach(::remove) }.apply()
        Timber.tag(TAG).i("Removed obsolete settings: %s", present.joinToString())
        return true
    }
}

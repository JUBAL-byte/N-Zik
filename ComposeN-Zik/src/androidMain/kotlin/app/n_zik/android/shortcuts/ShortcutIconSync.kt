package app.n_zik.android.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import app.n_zik.android.MainActivity
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rescue.RescueActivity
import timber.log.Timber

internal const val GOOGLE_LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher"

internal const val SHORTCUT_SEARCH_ID = "search"
internal const val SHORTCUT_ALBUMS_ID = "albums"
internal const val SHORTCUT_ARTISTS_ID = "artists"
internal const val SHORTCUT_LIBRARY_ID = "library"
internal const val SHORTCUT_RESCUE_ID = "rescue"

/** All available shortcut IDs. */
internal val ALL_SHORTCUT_IDS =
    listOf(SHORTCUT_SEARCH_ID, SHORTCUT_ALBUMS_ID, SHORTCUT_ARTISTS_ID, SHORTCUT_LIBRARY_ID, SHORTCUT_RESCUE_ID)

/** Maximum number of active shortcuts (launcher display limit). */
internal const val MAX_ACTIVE_SHORTCUTS = 4

/** Default active shortcuts when no preference has been set. */
internal val DEFAULT_ACTIVE_SHORTCUT_IDS =
    listOf(SHORTCUT_ALBUMS_ID, SHORTCUT_ARTISTS_ID, SHORTCUT_LIBRARY_ID, SHORTCUT_RESCUE_ID)

// Preference keys for shortcut order/enabled state (stored in "preferences")
const val appShortcutsOrderKey = "appShortcutsOrder"
const val appShortcutsEnabledKey = "appShortcutsEnabled"

/**
 * True when the given package is the Google (Pixel) launcher.
 *
 * The Pixel launcher resolves (and caches) shortcut icons with its own configuration, so the
 * theme-adaptive `shortcut_icon` color shows up white there; on that launcher the icons must
 * stay black in both themes.
 */
internal fun isGoogleLauncher(homePackage: String?): Boolean = homePackage == GOOGLE_LAUNCHER_PACKAGE

/**
 * Package name of the default home launcher, or null when it cannot be resolved.
 *
 * Requires a `<queries>` entry for `ACTION_MAIN`/`CATEGORY_HOME` in the manifest (API 30+ package
 * visibility) -- without it this always resolves to null and [registerAppShortcuts] silently
 * falls through to the non-Pixel branch (verified on-device/emulator).
 */
internal fun homeLauncherPackage(packageManager: PackageManager): String? =
    runCatching {
        packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0
        )
    }.getOrNull()?.activityInfo?.packageName

/**
 * Shortcut order and enabled set, parsed from the two comma-separated preference values.
 *
 * Single source of truth shared by the launcher registration and the settings dialog, so they
 * cannot disagree. Guarantees, whatever the stored strings contain (blank, unknown ids,
 * duplicates, missing ids):
 * - [order] holds every id of [ALL_SHORTCUT_IDS] exactly once, stored order first;
 * - [enabled] always contains [SHORTCUT_RESCUE_ID] (locked), and falls back to
 *   [DEFAULT_ACTIVE_SHORTCUT_IDS] when nothing usable is stored.
 */
internal data class ShortcutConfig(val order: List<String>, val enabled: Set<String>)

internal fun parseShortcutConfig(orderStr: String?, enabledStr: String?): ShortcutConfig {
    val storedOrder = orderStr.orEmpty().split(",").filter { it in ALL_SHORTCUT_IDS }.distinct()
    val order = storedOrder + ALL_SHORTCUT_IDS.filter { it !in storedOrder }

    val storedEnabled = enabledStr.orEmpty().split(",").filter { it in ALL_SHORTCUT_IDS }
    val enabled = (storedEnabled.ifEmpty { DEFAULT_ACTIVE_SHORTCUT_IDS } + SHORTCUT_RESCUE_ID).toSet()

    return ShortcutConfig(order, enabled)
}

/**
 * The shortcut ids to register, in display order: enabled ones only, at most
 * [MAX_ACTIVE_SHORTCUTS]. Rescue is locked, so when more than the maximum are enabled the other
 * shortcuts give way, never Rescue.
 */
internal fun resolveActiveShortcutIds(orderStr: String?, enabledStr: String?): List<String> {
    val (order, enabled) = parseShortcutConfig(orderStr, enabledStr)
    val active = order.filter { it in enabled }
    if (active.size <= MAX_ACTIVE_SHORTCUTS) return active

    val kept = active.filter { it != SHORTCUT_RESCUE_ID }.take(MAX_ACTIVE_SHORTCUTS - 1).toSet()
    return active.filter { it == SHORTCUT_RESCUE_ID || it in kept }
}

/**
 * Same as above, reading the values from the `"preferences"` file. A value of an unexpected type
 * (corrupt preferences) is treated as absent instead of failing registration.
 */
internal fun resolveActiveShortcutIds(context: Context): List<String> {
    val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    val orderStr = runCatching { prefs.getString(appShortcutsOrderKey, null) }.getOrNull()
    val enabledStr = runCatching { prefs.getString(appShortcutsEnabledKey, null) }.getOrNull()
    return resolveActiveShortcutIds(orderStr, enabledStr)
}

/**
 * Registers the active launcher shortcuts as dynamic shortcuts, with an icon appropriate
 * to the active home launcher.
 *
 * The shortcuts are read from user preferences (order + enabled set). Rescue is always
 * included. At most [MAX_ACTIVE_SHORTCUTS] are registered.
 *
 * This is called from [app.n_zik.android.MainApplication.onCreate] early (before
 * `Dependencies.init`) so that the Rescue shortcut exists even if initialization fails.
 */
internal fun registerAppShortcuts(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
    val shortcutManager =
        runCatching { context.getSystemService(ShortcutManager::class.java) }.getOrNull() ?: return

    runCatching {
        val activeIds = resolveActiveShortcutIds(context)
        val blackIcons = isGoogleLauncher(homeLauncherPackage(context.packageManager))
        val registered = shortcutManager.setDynamicShortcuts(activeIds.map { buildShortcut(context, it, blackIcons) })
        if (registered) {
            Timber.tag("ShortcutIconSync").i(
                "Registered %d shortcuts (%s)%s",
                activeIds.size,
                activeIds.joinToString(","),
                if (blackIcons) " (Google launcher: black icons)" else ""
            )
        } else {
            // false = rate-limited: this runs on every main-process start, including background ones.
            Timber.tag("ShortcutIconSync").w("Shortcuts not registered (rate limited)")
        }
    }.onFailure {
        Timber.tag("ShortcutIconSync").e(it, "Failed to register shortcuts")
    }
}

/**
 * Label resource, icon drawable, and intent action for a shortcut id.
 *
 * Split out from [buildShortcut] so the id-to-resource mapping is unit-testable without a real
 * `Context` -- resource ids are compile-time constants, so this needs no Robolectric/Android
 * resources at all, unlike [buildShortcut] itself (`context.getString`) or [shortcutIcon]
 * (`ContextCompat.getDrawable`), which do.
 */
internal fun shortcutSpec(shortcutId: String): Triple<Int, Int, String> = when (shortcutId) {
    SHORTCUT_SEARCH_ID -> Triple(R.string.search, R.drawable.shortcut_search, MainActivity.action_search)
    SHORTCUT_ALBUMS_ID -> Triple(R.string.albums, R.drawable.shortcut_albums, MainActivity.action_albums)
    SHORTCUT_ARTISTS_ID -> Triple(R.string.artists, R.drawable.shortcut_artists, MainActivity.actions_artists)
    SHORTCUT_LIBRARY_ID -> Triple(R.string.playlists, R.drawable.shortcut_library, MainActivity.action_library)
    SHORTCUT_RESCUE_ID -> Triple(R.string.rescue_center, R.drawable.shortcut_rescue, ACTION_RESCUE)
    else -> error("Unknown shortcut id $shortcutId")
}

/** Intent action for the Rescue Center shortcut. Must match the manifest intent-filter. */
const val ACTION_RESCUE = "app.n_zik.android.action.rescue"

/**
 * The activity a shortcut opens. Rescue MUST open [RescueActivity] (its own process, no app
 * init): pointing it at [MainActivity] would send the user into the very crash it exists for.
 * Split out so this is unit-testable without a `Context`.
 */
internal fun shortcutTargetClass(shortcutId: String): Class<*> =
    if (shortcutId == SHORTCUT_RESCUE_ID) RescueActivity::class.java else MainActivity::class.java

private fun buildShortcut(context: Context, shortcutId: String, blackIcon: Boolean): ShortcutInfo {
    val (labelRes, drawableRes, action) = shortcutSpec(shortcutId)

    return ShortcutInfo.Builder(context, shortcutId)
        .setShortLabel(context.getString(labelRes))
        .setIcon(shortcutIcon(context, drawableRes, blackIcon))
        .setIntent(Intent(context, shortcutTargetClass(shortcutId)).setAction(action))
        .build()
}

/**
 * Split out from [buildShortcut] so the icon-type choice is testable: `ShortcutInfo` exposes no
 * public getter for the icon it was built with, so a test asserting on the built `ShortcutInfo`
 * directly cannot see which branch ran.
 */
internal fun shortcutIcon(context: Context, drawableRes: Int, blackIcon: Boolean): Icon =
    if (blackIcon) {
        Icon.createWithBitmap(blackShortcutIcon(context, drawableRes))
    } else {
        Icon.createWithResource(context, drawableRes)
    }

private fun blackShortcutIcon(context: Context, drawableRes: Int): Bitmap {
    val drawable = ContextCompat.getDrawable(context, drawableRes)
        ?: error("Missing shortcut drawable $drawableRes")
    val sizePx = (96 * context.resources.displayMetrics.density).toInt()
    return tintedBitmap(drawable, Color.BLACK, sizePx)
}

/**
 * Renders [drawable] tinted with [tint] into a square [sizePx] bitmap.
 *
 * Split out from [blackShortcutIcon] so the tint+render logic is unit-testable with a synthetic
 * `Drawable` -- this module's test source set does not have `includeAndroidResources` enabled
 * (turning it on regressed ~36 unrelated tests elsewhere via a JVM security-provider conflict), so
 * a real drawable resource cannot be resolved from a unit test; this function takes an
 * already-resolved `Drawable` and needs no resource lookup at all.
 */
internal fun tintedBitmap(drawable: Drawable, tint: Int, sizePx: Int): Bitmap {
    val mutable = drawable.mutate()
    DrawableCompat.setTint(mutable, tint)
    mutable.setBounds(0, 0, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    mutable.draw(Canvas(bitmap))
    return bitmap
}

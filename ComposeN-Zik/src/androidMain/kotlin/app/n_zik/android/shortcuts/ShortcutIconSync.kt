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
import timber.log.Timber

internal const val GOOGLE_LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher"

internal const val SHORTCUT_SEARCH_ID = "search"
internal const val SHORTCUT_ALBUMS_ID = "albums"
internal const val SHORTCUT_ARTISTS_ID = "artists"
internal const val SHORTCUT_LIBRARY_ID = "library"

internal val SHORTCUT_IDS =
    listOf(SHORTCUT_SEARCH_ID, SHORTCUT_ALBUMS_ID, SHORTCUT_ARTISTS_ID, SHORTCUT_LIBRARY_ID)

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
 * Registers the app's 4 launcher shortcuts as dynamic shortcuts (search, albums, artists,
 * library), with an icon appropriate to the active home launcher.
 *
 * These are *not* declared as manifest (static) shortcuts: a manifest shortcut is immutable at
 * the platform level (Android's `ShortcutService` throws `IllegalArgumentException: Manifest
 * shortcut ID=... may not be manipulated via APIs` from `setDynamicShortcuts`, `updateShortcuts`,
 * `disableShortcuts` -- every mutation, verified on-device/emulator), so there is no way to swap
 * a manifest shortcut's icon per launcher at runtime; a dynamic shortcut has no such restriction.
 * The trade-off is that these shortcuts only exist once the app has been launched at least once
 * after install (dynamic shortcuts are registered from [MainActivity.onCreate], unlike manifest
 * ones which are available immediately).
 *
 * On the Google (Pixel) launcher the 4 shortcuts get a fixed black bitmap icon. On any other
 * launcher they get the theme-adaptive `shortcut_*` drawables (resolved by the launcher's own
 * day/night configuration, exactly as a manifest shortcut's icon would be).
 */
internal fun registerAppShortcuts(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
    val shortcutManager =
        runCatching { context.getSystemService(ShortcutManager::class.java) }.getOrNull() ?: return

    runCatching {
        val blackIcons = isGoogleLauncher(homeLauncherPackage(context.packageManager))
        shortcutManager.setDynamicShortcuts(SHORTCUT_IDS.map { buildShortcut(context, it, blackIcons) })
        Timber.tag("ShortcutIconSync").i(
            if (blackIcons) "Google launcher: registered shortcuts with fixed black icons"
            else "Registered shortcuts with theme-adaptive icons"
        )
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
    else -> error("Unknown shortcut id $shortcutId")
}

private fun buildShortcut(context: Context, shortcutId: String, blackIcon: Boolean): ShortcutInfo {
    val (labelRes, drawableRes, action) = shortcutSpec(shortcutId)
    return ShortcutInfo.Builder(context, shortcutId)
        .setShortLabel(context.getString(labelRes))
        .setIcon(shortcutIcon(context, drawableRes, blackIcon))
        .setIntent(Intent(context, MainActivity::class.java).setAction(action))
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

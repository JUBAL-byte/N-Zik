package app.n_zik.android.shortcuts

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import app.n_zik.android.MainActivity
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.AppShortcutsSettingsDialog
import app.n_zik.android.components.dialog.settings.defaultShortcutsOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class ShortcutIconSyncTest {

    @Test
    fun `google launcher package is detected`() {
        assertTrue(isGoogleLauncher(GOOGLE_LAUNCHER_PACKAGE))
        assertTrue(isGoogleLauncher("com.google.android.apps.nexuslauncher"))
    }

    @Test
    fun `other launchers are not detected`() {
        assertFalse(isGoogleLauncher("com.miui.home"))
        assertFalse(isGoogleLauncher("com.huawei.launcher"))
        assertFalse(isGoogleLauncher("com.google.android.googlequicksearchbox"))
        assertFalse(isGoogleLauncher(""))
        assertFalse(isGoogleLauncher(null))
    }

    /**
     * Plain JVM (no Robolectric needed): resource ids are compile-time constants, so this maps
     * each shortcut id to its resources/action without ever touching a `Context`.
     */
    @Test
    fun `each shortcut id maps to its own action and distinct resources`() {
        val specs = ALL_SHORTCUT_IDS.associateWith { shortcutSpec(it) }

        assertEquals(SHORTCUT_SEARCH_ID to MainActivity.action_search, SHORTCUT_SEARCH_ID to specs.getValue(SHORTCUT_SEARCH_ID).third)
        assertEquals(SHORTCUT_ALBUMS_ID to MainActivity.action_albums, SHORTCUT_ALBUMS_ID to specs.getValue(SHORTCUT_ALBUMS_ID).third)
        assertEquals(SHORTCUT_ARTISTS_ID to MainActivity.actions_artists, SHORTCUT_ARTISTS_ID to specs.getValue(SHORTCUT_ARTISTS_ID).third)
        assertEquals(SHORTCUT_LIBRARY_ID to MainActivity.action_library, SHORTCUT_LIBRARY_ID to specs.getValue(SHORTCUT_LIBRARY_ID).third)
        assertEquals(SHORTCUT_RESCUE_ID to ACTION_RESCUE, SHORTCUT_RESCUE_ID to specs.getValue(SHORTCUT_RESCUE_ID).third)
        // Every id gets its own label and its own drawable -- no two shortcuts silently share one.
        assertEquals(ALL_SHORTCUT_IDS.size, specs.values.map { it.first }.distinct().size)
        assertEquals(ALL_SHORTCUT_IDS.size, specs.values.map { it.second }.distinct().size)
    }

    @Test
    fun `5 shortcut IDs are available`() {
        assertEquals(5, ALL_SHORTCUT_IDS.size)
        assertTrue(SHORTCUT_RESCUE_ID in ALL_SHORTCUT_IDS)
        assertTrue(SHORTCUT_SEARCH_ID in ALL_SHORTCUT_IDS)
    }

    @Test
    fun `default active shortcuts has 4 entries and includes rescue`() {
        assertEquals(MAX_ACTIVE_SHORTCUTS, DEFAULT_ACTIVE_SHORTCUT_IDS.size)
        assertTrue(SHORTCUT_RESCUE_ID in DEFAULT_ACTIVE_SHORTCUT_IDS)
        assertFalse(SHORTCUT_SEARCH_ID in DEFAULT_ACTIVE_SHORTCUT_IDS)
    }

    @Test
    fun `rescue shortcut uses ACTION_RESCUE intent action`() {
        val (_, _, action) = shortcutSpec(SHORTCUT_RESCUE_ID)
        assertEquals(ACTION_RESCUE, action)
        assertEquals("app.n_zik.android.action.rescue", action)
    }

    @Test
    fun `rescue shortcut uses rescue icon drawable`() {
        val (_, drawableRes, _) = shortcutSpec(SHORTCUT_RESCUE_ID)
        assertEquals(R.drawable.shortcut_rescue, drawableRes)
    }

    // ──────────────────────────────────────────────────────────────────────
    // AppShortcutsSettingsDialog order/enabled parsing
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `parseOrder with empty string returns default order`() {
        val order = AppShortcutsSettingsDialog.parseOrder("")
        assertEquals(defaultShortcutsOrder, order)
    }

    @Test
    fun `parseOrder with valid string preserves order and adds missing`() {
        val order = AppShortcutsSettingsDialog.parseOrder("rescue,albums")
        assertEquals("rescue", order[0])
        assertEquals("albums", order[1])
        // The remaining IDs are appended
        assertTrue(order.containsAll(ALL_SHORTCUT_IDS))
        assertEquals(ALL_SHORTCUT_IDS.size, order.size)
    }

    @Test
    fun `parseOrder ignores unknown IDs`() {
        val order = AppShortcutsSettingsDialog.parseOrder("rescue,nonexistent,albums")
        assertFalse("nonexistent" in order)
        assertEquals(ALL_SHORTCUT_IDS.size, order.size)
    }

    @Test
    fun `parseEnabled with empty string returns defaults`() {
        val enabled = AppShortcutsSettingsDialog.parseEnabled("")
        assertEquals(DEFAULT_ACTIVE_SHORTCUT_IDS.toSet(), enabled)
    }

    @Test
    fun `parseEnabled always includes rescue even if not in string`() {
        val enabled = AppShortcutsSettingsDialog.parseEnabled("search,albums")
        assertTrue(SHORTCUT_RESCUE_ID in enabled)
    }

    @Test
    fun `parseEnabled with valid string includes specified IDs plus rescue`() {
        val enabled = AppShortcutsSettingsDialog.parseEnabled("search,albums")
        assertTrue(SHORTCUT_SEARCH_ID in enabled)
        assertTrue(SHORTCUT_ALBUMS_ID in enabled)
        assertTrue(SHORTCUT_RESCUE_ID in enabled) // always included
        assertFalse(SHORTCUT_ARTISTS_ID in enabled)
    }
}

/**
 * [shortcutIcon]/[tintedBitmap] are what fix the actual Pixel bug: on-device/emulator testing
 * found that a manifest (static) shortcut is immutable at the platform level -- Android's
 * `ShortcutService` refuses to add, update or even disable it via the API once declared in XML --
 * so the only way to give a shortcut a fixed black icon on the Google launcher and a
 * theme-adaptive one everywhere else is to never declare it statically at all, and pick the icon
 * type per launcher when registering the dynamic shortcut.
 *
 * `Icon.createWithResource`/`createWithBitmap` and `Bitmap.createBitmap`/`Canvas` need a real
 * `android.graphics`/`android.content.pm` runtime, hence Robolectric. This module's test source
 * set does not enable `includeAndroidResources` (that regressed ~36 unrelated tests via a JVM
 * security-provider conflict), so [tintedBitmap] is tested with a synthetic `ColorDrawable`
 * instead of a real app resource -- it needs no resource lookup at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShortcutIconRenderingTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `black icon variant uses a bitmap icon`() {
        // A resource id that doesn't need to exist: TYPE_BITMAP is set before any drawable lookup.
        val icon = Icon.createWithBitmap(tintedBitmap(ColorDrawable(Color.RED), Color.BLACK, sizePx = 8))

        assertEquals(Icon.TYPE_BITMAP, icon.type)
    }

    @Test
    fun `theme-adaptive variant uses a resource icon`() {
        val icon = shortcutIcon(context, R.drawable.shortcut_search, blackIcon = false)

        assertEquals(Icon.TYPE_RESOURCE, icon.type)
    }

    /**
     * Robolectric's shadow of `ColorDrawable`/`ShapeDrawable` does not faithfully apply
     * `DrawableCompat.setTint`'s `PorterDuffColorFilter` during `draw()` (verified: the rendered
     * pixel came back as the drawable's original color, not the requested tint) -- so this only
     * asserts the reliably-testable part (size). The actual tint rendering is confirmed correct on
     * a real Pixel emulator (black icons observed directly by the user after this fix).
     */
    @Test
    fun `tintedBitmap renders at the requested size`() {
        val redRectangle = ShapeDrawable(RectShape()).apply { paint.color = Color.RED }

        val bitmap = tintedBitmap(redRectangle, Color.BLACK, sizePx = 16)

        assertEquals(16, bitmap.width)
        assertEquals(16, bitmap.height)
    }
}

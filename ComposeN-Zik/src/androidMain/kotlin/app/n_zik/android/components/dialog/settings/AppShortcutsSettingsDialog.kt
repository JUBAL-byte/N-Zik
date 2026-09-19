package app.n_zik.android.components.dialog.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.Dialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.n_zik.android.shortcuts.DEFAULT_ACTIVE_SHORTCUT_IDS
import app.n_zik.android.shortcuts.MAX_ACTIVE_SHORTCUTS
import app.n_zik.android.shortcuts.SHORTCUT_ALBUMS_ID
import app.n_zik.android.shortcuts.SHORTCUT_ARTISTS_ID
import app.n_zik.android.shortcuts.SHORTCUT_LIBRARY_ID
import app.n_zik.android.shortcuts.SHORTCUT_RESCUE_ID
import app.n_zik.android.shortcuts.SHORTCUT_SEARCH_ID
import app.n_zik.android.shortcuts.appShortcutsEnabledKey
import app.n_zik.android.shortcuts.appShortcutsOrderKey
import app.n_zik.android.shortcuts.parseShortcutConfig
import app.n_zik.android.shortcuts.registerAppShortcuts
import app.n_zik.android.shortcuts.resolveActiveShortcutIds
import app.kreate.android.me.knighthat.utils.Toaster
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Definition for a shortcut item in the configuration dialog.
 */
data class ShortcutDef(
    val id: String,
    val iconRes: Int,
    val labelRes: Int,
    val defaultEnabled: Boolean,
    /** Whether this shortcut can be disabled. Rescue is locked (always enabled). */
    val locked: Boolean = false
)

/** Default order for all available shortcuts (including disabled ones). */
val defaultShortcutsOrder = listOf(
    SHORTCUT_SEARCH_ID,
    SHORTCUT_ALBUMS_ID,
    SHORTCUT_ARTISTS_ID,
    SHORTCUT_LIBRARY_ID,
    SHORTCUT_RESCUE_ID
)

fun buildShortcutDefs(): Map<String, ShortcutDef> = mapOf(
    SHORTCUT_SEARCH_ID to ShortcutDef(
        SHORTCUT_SEARCH_ID, R.drawable.shortcut_search, R.string.search,
        defaultEnabled = false
    ),
    SHORTCUT_ALBUMS_ID to ShortcutDef(
        SHORTCUT_ALBUMS_ID, R.drawable.shortcut_albums, R.string.albums,
        defaultEnabled = true
    ),
    SHORTCUT_ARTISTS_ID to ShortcutDef(
        SHORTCUT_ARTISTS_ID, R.drawable.shortcut_artists, R.string.artists,
        defaultEnabled = true
    ),
    SHORTCUT_LIBRARY_ID to ShortcutDef(
        SHORTCUT_LIBRARY_ID, R.drawable.shortcut_library, R.string.playlists,
        defaultEnabled = true
    ),
    SHORTCUT_RESCUE_ID to ShortcutDef(
        SHORTCUT_RESCUE_ID, R.drawable.shortcut_rescue, R.string.rescue_center,
        defaultEnabled = true,
        locked = true
    )
)

/**
 * Dialog for customizing app launcher shortcuts (order + toggle).
 * Follows the same pattern as [HomeTabsSettingsDialog].
 *
 * Rules:
 * - Max [MAX_ACTIVE_SHORTCUTS] active at once.
 * - Rescue is always enabled (locked).
 * - Preferences stored as comma-separated strings in `"preferences"`.
 */
object AppShortcutsSettingsDialog : Dialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.app_shortcuts_settings)

    override var isActive: Boolean by mutableStateOf(false)

    /** Every shortcut id once, stored order first, missing ids appended. */
    fun parseOrder(serialized: String): List<String> = parseShortcutConfig(serialized, null).order

    /** Enabled ids: Rescue is always in, defaults apply when nothing usable is stored. */
    fun parseEnabled(serialized: String): Set<String> = parseShortcutConfig(null, serialized).enabled

    fun loadPrefs(prefs: SharedPreferences): Pair<MutableList<String>, MutableList<Boolean>> {
        val orderStr = runCatching { prefs.getString(appShortcutsOrderKey, null) }.getOrNull()
        val enabledStr = runCatching { prefs.getString(appShortcutsEnabledKey, null) }.getOrNull()
        val order = parseShortcutConfig(orderStr, enabledStr).order
        // Same resolution as the launcher registration, cap included: the dialog never shows more
        // active shortcuts than actually get registered.
        val active = resolveActiveShortcutIds(orderStr, enabledStr).toSet()
        return order.toMutableList() to order.map { it in active }.toMutableList()
    }

    private fun savePrefs(prefs: SharedPreferences, order: List<String>, toggles: Map<String, Boolean>) {
        val editor = prefs.edit()
        editor.putString(appShortcutsOrderKey, order.joinToString(","))
        val enabledIds = order.filter { toggles[it] == true }
        editor.putString(appShortcutsEnabledKey, enabledIds.joinToString(","))
        editor.apply()
    }

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("preferences", Context.MODE_PRIVATE) }
        val defs = remember { buildShortcutDefs() }

        val initial = remember { loadPrefs(prefs) }

        var workingOrder by remember { mutableStateOf(initial.first) }
        var workingToggles by remember { mutableStateOf(initial.second) }

        val lazyListState = rememberLazyListState()

        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val order = workingOrder.toMutableList()
            val toggles = workingToggles.toMutableList()
            val fromIndex = order.indexOf(from.key)
            val toIndex = order.indexOf(to.key)
            if (fromIndex != -1 && toIndex != -1) {
                val item = order.removeAt(fromIndex)
                order.add(toIndex, item)
                val checkedItem = toggles.removeAt(fromIndex)
                toggles.add(toIndex, checkedItem)
                workingOrder = order
                workingToggles = toggles
            }
        }

        val shortcutItems = workingOrder.mapNotNull { shortcutId ->
            defs[shortcutId]?.let { def ->
                ToggleItem(
                    id = def.id,
                    iconRes = def.iconRes,
                    label = stringResource(def.labelRes),
                    preferenceKey = "shortcut_${def.id}_enabled",
                    defaultValue = def.defaultEnabled
                )
            }
        }

        ToggleListDialog(
            items = shortcutItems,
            lazyListState = lazyListState,
            reorderableState = reorderableState,
            pinnedItemCount = 0,
            enforceMinOneChecked = true,
            maxChecked = MAX_ACTIVE_SHORTCUTS,
            lockedCheckedIds = setOf(SHORTCUT_RESCUE_ID),
            checkedStatesOverride = workingToggles.toList(),
            onCheckedChange = { index, newValue ->
                val shortcutId = workingOrder.getOrNull(index) ?: return@ToggleListDialog
                val def = defs[shortcutId]

                // Rescue cannot be disabled
                if (def?.locked == true && !newValue) {
                    Toaster.s(R.string.app_shortcuts_rescue_locked)
                    return@ToggleListDialog
                }

                // Check max active count before enabling
                if (newValue) {
                    val currentActive = workingToggles.count { it }
                    if (currentActive >= MAX_ACTIVE_SHORTCUTS) {
                        Toaster.s(context.getString(R.string.app_shortcuts_max_reached, MAX_ACTIVE_SHORTCUTS))
                        return@ToggleListDialog
                    }
                }

                val newToggles = workingToggles.toMutableList()
                newToggles[index] = newValue
                workingToggles = newToggles
            },
            onReset = {
                workingOrder = defaultShortcutsOrder.toMutableList()
                workingToggles = defaultShortcutsOrder.map { id ->
                    id in DEFAULT_ACTIVE_SHORTCUT_IDS
                }.toMutableList()
            },
            onCancel = {
                hideDialog()
            },
            onConfirm = {
                val toggleMap = mutableMapOf<String, Boolean>()
                workingOrder.forEachIndexed { index, id ->
                    toggleMap[id] = workingToggles[index]
                }
                savePrefs(prefs, workingOrder, toggleMap)
                // Re-register shortcuts with the updated preferences
                registerAppShortcuts(context)
                Toaster.s(R.string.toast_preference_saved)
                hideDialog()
            }
        )
    }

    fun reset(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val defaultToggles = buildShortcutDefs().mapValues { it.value.defaultEnabled }
        savePrefs(prefs, defaultShortcutsOrder, defaultToggles)
        // Re-register shortcuts with defaults
        registerAppShortcuts(context)
    }
}

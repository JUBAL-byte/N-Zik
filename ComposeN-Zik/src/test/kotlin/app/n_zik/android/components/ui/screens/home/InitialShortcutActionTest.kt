package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.MainActivity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InitialShortcutActionTest {

    @Test
    fun `fresh launch keeps the launch intent action`() {
        assertEquals(MainActivity.action_search, initialShortcutAction(MainActivity.action_search, isRestoredInstance = false))
        assertEquals("android.intent.action.MAIN", initialShortcutAction("android.intent.action.MAIN", isRestoredInstance = false))
    }

    @Test
    fun `fresh launch without an action stays empty`() {
        assertNull(initialShortcutAction(null, isRestoredInstance = false))
    }

    @Test
    fun `recreated activity never re-applies the launch intent action`() {
        assertNull(initialShortcutAction(MainActivity.action_albums, isRestoredInstance = true))
        assertNull(initialShortcutAction("android.intent.action.MAIN", isRestoredInstance = true))
    }
}

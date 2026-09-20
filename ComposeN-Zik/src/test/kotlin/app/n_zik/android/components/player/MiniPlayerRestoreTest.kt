package app.n_zik.android.components.player

import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class MiniPlayerRestoreTest {

    private val collapsedBound = 120.dp

    private fun sheet(isDismissed: Boolean): PlayerSheetState {
        val sheetState = mockk<PlayerSheetState>()
        every { sheetState.isDismissed } returns isDismissed
        every { sheetState.collapsedBound } returns collapsedBound
        every { sheetState.snapTo(any()) } just Runs
        return sheetState
    }

    @Test
    fun `a dismissed sheet is brought back to the collapsed mini-player`() {
        val sheetState = sheet(isDismissed = true)

        sheetState.showMiniplayerIfDismissed()

        verify(exactly = 1) { sheetState.snapTo(collapsedBound) }
    }

    @Test
    fun `a sheet that is already showing is left untouched`() {
        val sheetState = sheet(isDismissed = false)

        sheetState.showMiniplayerIfDismissed()

        verify(exactly = 0) { sheetState.snapTo(any()) }
    }
}

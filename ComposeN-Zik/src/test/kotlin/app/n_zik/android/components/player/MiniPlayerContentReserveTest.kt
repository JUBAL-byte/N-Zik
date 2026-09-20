package app.n_zik.android.components.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MiniPlayerContentReserveTest {

    @Test
    fun `keeps the full room while the header is shown`() {
        assertEquals(
            200f,
            miniPlayerContentReservePx(reservePx = 200f, scrollOffsetPx = 0f, hideRangePx = 400f),
        )
    }

    @Test
    fun `shrinks proportionally while the header slides out`() {
        assertEquals(
            100f,
            miniPlayerContentReservePx(reservePx = 200f, scrollOffsetPx = -200f, hideRangePx = 400f),
        )
    }

    @Test
    fun `leaves no empty band once everything is gone`() {
        assertEquals(
            0f,
            miniPlayerContentReservePx(reservePx = 200f, scrollOffsetPx = -400f, hideRangePx = 400f),
        )
        assertEquals(
            0f,
            miniPlayerContentReservePx(reservePx = 200f, scrollOffsetPx = -900f, hideRangePx = 400f),
        )
    }

    @Test
    fun `without a hide range the room is kept`() {
        assertEquals(
            200f,
            miniPlayerContentReservePx(reservePx = 200f, scrollOffsetPx = -50f, hideRangePx = 0f),
        )
    }
}

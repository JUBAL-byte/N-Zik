package app.n_zik.android.components.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SwipeActionLatchTest {

    @Test
    fun `the first call of a gesture fires`() {
        assertTrue(SwipeActionLatch().tryFire())
    }

    @Test
    fun `later calls of the same gesture do not fire`() {
        val latch = SwipeActionLatch()
        latch.tryFire()

        assertFalse(latch.tryFire())
        assertFalse(latch.tryFire())
    }

    @Test
    fun `a swipe past the threshold that reports on every pixel runs the action once`() {
        val latch = SwipeActionLatch()
        var actions = 0

        // confirmValueChange is called again for each drag delta while the finger stays past the threshold
        repeat(200) { if (latch.tryFire()) actions++ }

        assertEquals(1, actions)
    }

    @Test
    fun `releasing rearms the latch for the next gesture`() {
        val latch = SwipeActionLatch()
        latch.tryFire()

        latch.release()

        assertTrue(latch.tryFire())
        assertFalse(latch.tryFire())
    }

    @Test
    fun `releasing an unused latch keeps it usable`() {
        val latch = SwipeActionLatch()

        latch.release()

        assertTrue(latch.tryFire())
    }
}

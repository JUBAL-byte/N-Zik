package app.n_zik.android.components.player

/**
 * Lets a swipe action run once per gesture.
 *
 * `SwipeToDismissBox` calls `confirmValueChange` again on every drag delta while the finger stays
 * past the threshold (the state never settles because the callback answers false), so an action
 * placed there ran once per pixel of movement. [tryFire] answers true for the first call only;
 * [release] rearms it once the gesture is over.
 *
 * Not thread-safe: meant to be used from the main thread, like the gesture callbacks.
 */
class SwipeActionLatch {
    private var fired = false

    /** True the first time it is called since the latch was created or last released. */
    fun tryFire(): Boolean {
        if (fired) return false
        fired = true
        return true
    }

    /** Rearms the latch so the next gesture can fire its action. */
    fun release() {
        fired = false
    }
}

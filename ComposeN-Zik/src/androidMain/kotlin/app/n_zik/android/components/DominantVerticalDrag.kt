package app.n_zik.android.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/**
 * Whether a drag that just went past the touch slop is mostly vertical.
 *
 * [detectVerticalDragGestures][androidx.compose.foundation.gestures.detectVerticalDragGestures]
 * starts as soon as the vertical part alone passes the slop, so a swipe that is mainly horizontal
 * but drifts a little up or down still grabbed the sheet and opened or closed it.
 *
 * @param overSlop The movement past the slop, in pixels.
 */
internal fun isMostlyVerticalDrag(overSlop: Offset): Boolean =
    overSlop.y != 0f && abs(overSlop.y) >= abs(overSlop.x)

/**
 * Watches the ongoing movement of a drag that is already following the finger, and reports the
 * moment it turns mostly horizontal.
 *
 * [isMostlyVerticalDrag] only judges the movement at the start of the drag, so a drag that began
 * vertically would keep following the finger after it turned sideways: the sideways slide's
 * vertical drift would still move the sheet, and the release fling would still use the vertical
 * velocity of the phase before the slide. [turnsHorizontal] rechecks the direction continuously:
 * every [recheckDistance] pixels of accumulated movement (a tumbling window), once the movement
 * is more horizontal than vertical, the drag must stop following the finger.
 *
 * A perfect tie (45 degrees) counts as vertical, consistent with [isMostlyVerticalDrag], and
 * movement shorter than [recheckDistance] is never judged, so a shaky but overall vertical drag
 * keeps following.
 *
 * @param recheckDistance The distance, in pixels, the tumbling window must cover before the
 *        direction is judged. The touch slop is the natural size: a sideways slide is recognized
 *        as soon as it covers one slop.
 */
internal class OngoingDragDirectionMonitor(private val recheckDistance: Float) {
    private var windowX = 0f
    private var windowY = 0f
    private var windowDistance = 0f

    /**
     * Feed one movement delta, in pixels.
     *
     * @return True the moment a full window turns more horizontal than vertical.
     */
    fun turnsHorizontal(deltaX: Float, deltaY: Float): Boolean {
        windowX += deltaX
        windowY += deltaY
        windowDistance += Offset(deltaX, deltaY).getDistance()
        if (windowDistance < recheckDistance) return false
        val horizontal = abs(windowX) > abs(windowY)
        windowX = 0f
        windowY = 0f
        windowDistance = 0f
        return horizontal
    }
}

/**
 * Same as `detectVerticalDragGestures`, but a drag only counts while it is mostly vertical: at
 * the start (see [isMostlyVerticalDrag]) and throughout the gesture (see
 * [OngoingDragDirectionMonitor]). A mostly horizontal movement, before or mid-gesture, is
 * neither consumed nor followed, so it cannot move the sheet.
 *
 * When the drag stops following mid-gesture, [onDragAbort] is called and the movement is handed
 * back to the other consumers until the pointer is released. On release [onDragEnd] still runs,
 * reporting the abort so the sheet settles without flinging; on cancel [onDragCancel] runs as
 * usual.
 */
internal suspend fun PointerInputScope.detectMostlyVerticalDragGestures(
    onDragEnd: (aborted: Boolean) -> Unit = { _ -> },
    onDragCancel: () -> Unit = {},
    onDragAbort: () -> Unit = {},
    onVerticalDrag: (change: PointerInputChange, dragAmount: Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var initialOverSlop = 0f
        val drag = awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
            if (isMostlyVerticalDrag(overSlop)) {
                change.consume()
                initialOverSlop = overSlop.y
            }
        }
        if (drag != null) {
            onVerticalDrag(drag, initialOverSlop)
            val direction = OngoingDragDirectionMonitor(viewConfiguration.touchSlop)
            var aborted = false
            val completed = verticalDrag(drag.id) { change ->
                if (aborted) return@verticalDrag
                val delta = change.positionChange()
                if (direction.turnsHorizontal(delta.x, delta.y)) {
                    aborted = true
                    onDragAbort()
                    return@verticalDrag
                }
                onVerticalDrag(change, delta.y)
                change.consume()
            }
            if (completed) onDragEnd(aborted) else onDragCancel()
        }
    }
}

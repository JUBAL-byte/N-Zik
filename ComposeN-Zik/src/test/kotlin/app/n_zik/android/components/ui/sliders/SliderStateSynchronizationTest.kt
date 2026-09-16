package app.n_zik.android.components.ui.sliders

import android.app.Application
import androidx.compose.material3.SliderState
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SliderStateSynchronizationTest {
    @Test
    fun externalValueAndRangeChangesUpdateTheComposedSliderState() = runTest {
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frameClock)
        val runner = launch(frameClock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(NoNodesApplier(), recomposer)
        val externalValue = mutableFloatStateOf(12.5f)
        val externalRange = mutableStateOf(10f..20f)
        var observedState: SliderState? = null
        var frameTime = 0L

        fun recompose() {
            Snapshot.sendApplyNotifications()
            runCurrent()
            frameTime += 16_000_000L
            frameClock.sendFrame(frameTime)
            runCurrent()
        }

        try {
            composition.setContent {
                val state = rememberControlledSliderState(
                    externalValue.floatValue,
                    externalRange.value,
                )
                SideEffect { observedState = state }
            }
            recompose()
            val initial = requireNotNull(observedState)
            assertEquals(12.5f, initial.value, 0f)
            assertEquals(10f..20f, initial.trackRange)
            assertEquals(0, initial.steps)

            externalValue.floatValue = 17.25f
            recompose()
            assertSame(initial, observedState)
            assertEquals(17.25f, requireNotNull(observedState).value, 0f)

            externalRange.value = -5f..5f
            recompose()
            val changedRange = requireNotNull(observedState)
            assertNotSame(initial, changedRange)
            assertEquals(-5f..5f, changedRange.trackRange)
            assertEquals(5f, changedRange.value, 0f)
            assertEquals(0, changedRange.steps)

            externalValue.floatValue = -2.75f
            recompose()
            assertSame(changedRange, observedState)
            assertEquals(-2.75f, changedRange.value, 0f)
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.cancel()
        }
    }

    private class NoNodesApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }
}

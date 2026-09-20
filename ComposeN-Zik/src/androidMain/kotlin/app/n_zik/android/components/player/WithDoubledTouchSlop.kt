package app.n_zik.android.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration

/**
 * Makes the drag gestures inside [content] wait for twice the usual movement before they start.
 *
 * Used on a horizontal swipe that sits in a surface with a vertical drag (the mini-player): on a
 * slow diagonal drag the vertical gesture then reaches its threshold first and wins, instead of
 * the swipe starting and running its action. The song rows use the same doubling.
 */
@Composable
fun WithDoubledTouchSlop(content: @Composable () -> Unit) {
    val current = LocalViewConfiguration.current
    val doubled = remember(current) {
        object : ViewConfiguration by current {
            override val touchSlop: Float
                get() = current.touchSlop * 2f
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides doubled, content = content)
}

package app.n_zik.android.utils.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.excludeMediaItems
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext

/**
 * Issue #606 H8 (paired with M10 at the call sites that also build the list from
 * `Song.asMediaItem`) -- legacy `Player.addNext`/`Player.enqueue`
 * (`app.it.fast4x.rimusic.utils.Player.kt`) filter the incoming list through `excludeMediaItems`
 * whenever the caller passes a non-null [Context], and that filter does up to 3 `runBlocking` DB
 * reads (disliked songs/artists/albums). Every `app.n_zik.android.*` call site used to call
 * `addNext`/`enqueue` directly, on whatever thread the click handler already ran on -- Main, in
 * every case this lot fixes -- blocking the UI on those DB reads.
 *
 * `Player.kt` is legacy (`app.it.fast4x.rimusic.*`) and out of scope for editing (AD-4 tier 1:
 * fixable only from the caller, not the callee). These two suspend extension functions are the
 * caller-side fix, factored once and reused by every `app.n_zik.android.*` site that adds/enqueues
 * a filtered list: run the filtering on [NzikDispatchers.DATA], then call the legacy
 * `addNext`/`enqueue` with `context = null` so its own (synchronous) filtering branch is skipped --
 * the list has already been filtered, so skipping it also avoids filtering twice -- and the actual
 * queue mutation (`player.*`, ExoPlayer) happens exactly once, since `withContext` resumes on
 * whatever context launched the enclosing coroutine once its block completes. These functions are
 * dispatcher-agnostic themselves; they don't guarantee that resumption context is Main -- it's
 * Main only because every current caller happens to launch its coroutine from Main.
 */
@UnstableApi
suspend fun Player.addNextOffMain(mediaItems: List<MediaItem>, context: Context) {
    val filteredMediaItems = withContext(NzikDispatchers.DATA) { excludeMediaItems(mediaItems, context) }
    addNext(filteredMediaItems, context = null)
}

@UnstableApi
suspend fun Player.enqueueOffMain(mediaItems: List<MediaItem>, context: Context) {
    val filteredMediaItems = withContext(NzikDispatchers.DATA) { excludeMediaItems(mediaItems, context) }
    enqueue(filteredMediaItems, context = null)
}

package app.n_zik.android.extensions.lastfm

import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.utils.albumTitleOrDb
import app.n_zik.android.utils.artistTextOrDb
import app.n_zik.android.utils.titleOrDb
import it.fast4x.lastfm.LastFm
import it.fast4x.lastfm.models.LastFmApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.min

/**
 * Scrobble manager for LastFM.
 *
 * Metrolist scrobble semantics:
 * - Now Playing is sent when an eligible online track starts.
 * - The scrobble fires after min(50% of duration, 50s) with the
 *   epoch-seconds timestamp of the track start.
 * - Pausing freezes the remaining delay, resuming continues it.
 * - A new track transition cancels the pending job.
 */
class LastFmScrobbleManager(
    private val context: Context,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val TAG = "LastFmScrobble"
        private const val MIN_TRACK_DURATION_MS = 30_000L
        private const val MAX_SCROBBLE_DELAY_MS = 50_000L
        private const val SCROBBLE_THRESHOLD_RATIO = 0.5
        // Last.fm official docs: 9 = Invalid session key - please re-authenticate
        private const val SESSION_EXPIRED_CODE = 9

        /**
         * Scrobble delay in ms: min(50% of track duration, 50s).
         */
        internal fun computeScrobbleDelayMs(durationMs: Long): Long {
            if (durationMs <= 0L) return 0L
            return min((durationMs * SCROBBLE_THRESHOLD_RATIO).toLong(), MAX_SCROBBLE_DELAY_MS)
        }
    }

    private enum class Phase { Idle, Active, Paused, Done }

    // Dedicated Main scope for the session-expired toast: the service's prefs
    // listener destroys this manager as soon as the session key is removed,
    // which cancels externalScope before a toast launched on it would run.
    private val toastScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile
    private var trackJob: Job? = null
    @Volatile
    private var phase = Phase.Idle
    @Volatile
    private var trackedMediaId: String? = null
    @Volatile
    private var remainingMs: Long = 0L
    @Volatile
    private var timerStartedAt: Long = 0L
    @Volatile
    private var trackStartedAtEpochSeconds: Long = 0L
    @Volatile
    private var destroyed = false

    private var cachedArtist: String? = null
    private var cachedTitle: String? = null
    private var cachedAlbum: String? = null

    /**
     * Called on media item transition (new track or queue cleared).
     */
    @Synchronized
    fun onTrackTransition(mediaItem: MediaItem?, duration: Long) {
        if (destroyed) return
        trackJob?.cancel()
        trackJob = null
        resetState()
        trackedMediaId = mediaItem?.mediaId
        if (mediaItem == null || mediaItem.isLocal) return
        if (duration <= MIN_TRACK_DURATION_MS) return
        startTrack(mediaItem, duration, resume = false)
    }

    /**
     * Called on play/pause state changes.
     */
    @Synchronized
    fun onPlayingStateChanged(isPlaying: Boolean, mediaItem: MediaItem?, duration: Long) {
        if (destroyed) return
        if (mediaItem == null) {
            trackJob?.cancel()
            trackJob = null
            resetState()
            trackedMediaId = null
            return
        }
        if (mediaItem.mediaId != trackedMediaId) return

        if (isPlaying) {
            when (phase) {
                Phase.Idle -> {
                    // Duration was unknown at transition time; start now that it is known
                    if (mediaItem.isLocal) return
                    if (duration > MIN_TRACK_DURATION_MS) startTrack(mediaItem, duration, resume = false)
                }
                Phase.Active -> {
                    // Same item still playing: no-op, ExoPlayer can fire duplicate callbacks
                }
                Phase.Paused -> {
                    if (remainingMs > 0L) {
                        startTrack(mediaItem, duration, resume = true)
                    } else {
                        phase = Phase.Done
                    }
                }
                Phase.Done -> {
                    // Already scrobbled
                }
            }
        } else {
            if (phase == Phase.Active) pauseTimer()
        }
    }

    /**
     * Cancels the pending job and releases the scope.
     * The dedicated toastScope is intentionally NOT cancelled: a session
     * expired in flight must still be able to toast the re-login prompt.
     */
    @Synchronized
    fun destroy() {
        if (destroyed) return
        destroyed = true
        trackJob?.cancel()
        trackJob = null
        resetState()
        trackedMediaId = null
        externalScope.cancel()
    }

    private fun resetState() {
        phase = Phase.Idle
        remainingMs = 0L
        timerStartedAt = 0L
        trackStartedAtEpochSeconds = 0L
        cachedArtist = null
        cachedTitle = null
        cachedAlbum = null
    }

    private fun startTrack(mediaItem: MediaItem, duration: Long, resume: Boolean) {
        val mediaId = mediaItem.mediaId
        if (!resume) {
            trackStartedAtEpochSeconds = clock() / 1000L
            remainingMs = computeScrobbleDelayMs(duration)
            // Start the clock before the job launches so a pause during the
            // Now Playing call still deducts elapsed time in pauseTimer()
            timerStartedAt = clock()
        }
        phase = Phase.Active
        trackJob = externalScope.launch {
            val artist = cachedArtist ?: mediaItem.artistTextOrDb()
            val title = cachedTitle ?: mediaItem.titleOrDb()
            val album = cachedAlbum ?: mediaItem.albumTitleOrDb()
            if (artist.isBlank() || title.isBlank()) {
                // Empty metadata (no metadata + no DB row): nothing to send
                phase = Phase.Done
                return@launch
            }
            cachedArtist = artist
            cachedTitle = title
            cachedAlbum = album
            if (mediaId != trackedMediaId || destroyed) return@launch

            if (!resume) {
                LastFm.updateNowPlaying(artist, title, album).onFailure { e ->
                    Timber.tag(TAG).w(e, "Now Playing failed for '$title' by '$artist'")
                    handleFailure(e)
                }
                if (mediaId != trackedMediaId || destroyed) return@launch
            }

            if (resume) timerStartedAt = clock()
            delay(remainingMs)
            if (mediaId != trackedMediaId || destroyed) return@launch
            phase = Phase.Done
            sendScrobble(artist, title, album)
        }
    }

    private fun pauseTimer() {
        trackJob?.cancel()
        trackJob = null
        if (timerStartedAt > 0L) {
            remainingMs -= clock() - timerStartedAt
            if (remainingMs < 0L) remainingMs = 0L
            timerStartedAt = 0L
        }
        phase = Phase.Paused
    }

    private fun sendScrobble(artist: String, title: String, album: String?) {
        externalScope.launch {
            LastFm.scrobble(artist, title, trackStartedAtEpochSeconds, album)
                .onSuccess {
                    Timber.tag(TAG).d("Scrobbled '$title' by '$artist'")
                }
                .onFailure { e ->
                    Timber.tag(TAG).e(e, "Scrobble failed for '$title' by '$artist'")
                    handleFailure(e)
                }
        }
    }

    private fun handleFailure(e: Throwable) {
        if (e is LastFmApiException && e.errorCode == SESSION_EXPIRED_CODE) {
            onSessionExpired()
        }
    }

    private fun onSessionExpired() {
        runCatching {
            context.encryptedPreferences.edit {
                remove(lastfmSessionKey)
                remove(lastfmUsernameKey)
            }
            LastFm.sessionKey = null
        }.onFailure {
            Timber.tag(TAG).e(it, "Failed to clear expired LastFM session")
        }
        toastScope.launch {
            Toaster.e(R.string.lastfm_session_expired)
        }
    }
}

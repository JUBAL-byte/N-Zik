package app.n_zik.android.extensions.discord

import android.content.Context
import androidx.media3.common.MediaItem
import app.n_zik.android.core.network.utils.isNetworkAvailable
import app.n_zik.android.R
import app.n_zik.android.utils.artistTextOrDb
import app.kreate.android.me.knighthat.utils.Toaster
import com.metrolist.music.discordrpc.DiscordRpcConnection
import com.metrolist.music.discordrpc.entities.Timestamps
import com.metrolist.music.discordrpc.ActivityType
import com.metrolist.music.discordrpc.entities.Button
import kotlinx.coroutines.CoroutineScope
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber
import app.n_zik.android.core.network.client.NetworkClientFactory
import java.io.IOException
import com.metrolist.music.discordrpc.SuperProperties
import android.os.Build


class DiscordPresenceManager(
    private val context: Context,
    private val getToken: () -> String?,
    private val getBrowsingEnabled: () -> Boolean = { true },
    private val externalScope: CoroutineScope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA),
    private val connectionFactory: (String) -> DiscordRpcConnection = { defaultConnection(it) },
    private val tokenValidator: (suspend (String) -> Boolean?)? = null
) {
    companion object {
        private const val APPLICATION_ID = "1379051016007454760"
        /**
         * Debounce delay for presence updates.
         * When the user skips songs rapidly, each skip resets this timer.
         * Only after 5 seconds of stability does the update actually fire.
         * This prevents RPC spam, wrong-song flashes, and false "paused" states.
         */
        private const val DEBOUNCE_DELAY_MS = 5000L

        /** Default RPC connection (production); tests inject their own factory. */
        internal fun defaultConnection(token: String): DiscordRpcConnection = DiscordRpcConnection(
            token = token,
            os = "Android",
            browser = "Discord Android",
            device = Build.DEVICE,
            userAgent = SuperProperties.userAgent,
            superPropertiesBase64 = SuperProperties.superPropertiesBase64
        )
    }

    private var rpc: DiscordRpcConnection? = null
    private var lastToken: String? = null
    private var lastMediaItem: MediaItem? = null
    private var lastPosition: Long = 0L
    private var isStopped = false
    private val discordScope = externalScope
    private var refreshJob: Job? = null
    private var debounceJob: Job? = null
    private val client = NetworkClientFactory.getClientWithTimeout(10L, 10L)
    private val appStartTime = System.currentTimeMillis()

    /**
     * Tracks whether the player currently has an active media item loaded.
     * This is true when music is playing OR paused.
     * This is false only when no media item exists (no miniplayer).
     *
     * This flag is the single source of truth for deciding whether to show
     * browsing status or music status on route changes.
     */
    private var hasActiveMediaItem = false

    init {
        discordScope.launch {
            DiscordUiState.currentRoute.collect { route ->
                if (!isStopped && !hasActiveMediaItem && route != null) {
                    if (getBrowsingEnabled()) {
                        sendBrowsingPresence(route)
                    } else {
                        rpc?.clearActivity()
                    }
                }
            }
        }
    }

    private fun getSmallImageUrl(): String {
        return "https://raw.githubusercontent.com/N-Zik-Group/N-Zik/main/assets/discord/fallback_app.png?v=2"
    }

    private fun getLargeImageFallback(): String {
        return "https://raw.githubusercontent.com/N-Zik-Group/N-Zik/main/assets/discord/fallback_album.png?v=2"
    }

    /**
     * Validate the token
     */
    internal suspend fun validateToken(token: String): Boolean? = withContext(NzikDispatchers.DATA) {
        if (!context.isNetworkAvailable) return@withContext null
        val request = Request.Builder()
            .url("https://discord.com/api/v9/users/@me")
            .header("Authorization", token)
            .get()
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }.getOrElse { exception ->
            if (exception.message?.contains("429") == true || exception.message?.contains("Too Many Requests") == true) {
                Timber.tag("DiscordPresence").d("Rate limited by Discord API during token validation")
                null // Treat as network error to retry later
            } else {
                Timber.tag("DiscordPresence").e(exception, "Error validating token: ${exception.message}")
                if (exception is IOException) {
                    null
                } else {
                    false
                }
            }
        }
    }

    fun onPlayingStateChanged(mediaItem: MediaItem?, isPlaying: Boolean, position: Long = 0L, duration: Long = 0L, now: Long = System.currentTimeMillis(), getCurrentPosition: (() -> Long)? = null, isPlayingProvider: (() -> Boolean)? = null) {
        if (isStopped) return

        // Update the player state BEFORE any conditional return below: a transient
        // token/network condition must never leave hasActiveMediaItem stale,
        // otherwise the route collector and onBrowsingSettingChanged() would make
        // Browsing decisions on an outdated player state (browsing setting ignored).
        lastMediaItem = mediaItem
        lastPosition = position

        // Update the active media item flag:
        // true when a media item exists (playing or paused), false when null (no player)
        hasActiveMediaItem = mediaItem != null

        val token = getToken() ?: return
        if (token.isEmpty()) return

        if (!context.isNetworkAvailable) {
            return
        }

        refreshJob?.cancel()
        refreshJob = null

        if (token != lastToken) {
            rpc?.closeDirect()
            rpc = connectionFactory(token)
            lastToken = token
        }

        if (mediaItem == null) {
            // No media item = no music at all → show browsing if enabled and we have a route
            debounceJob?.cancel()
            if (getBrowsingEnabled()) {
                DiscordUiState.currentRoute.value?.let { route ->
                    sendBrowsingPresence(route)
                }
            } else {
                discordScope.launch { rpc?.clearActivity() }
            }
            return
        }

        // Cancel any pending debounced update — a new event supersedes it.
        debounceJob?.cancel()

        // Debounce: wait DEBOUNCE_DELAY_MS before actually sending the presence update.
        // If another event arrives before the delay elapses, this job is cancelled
        // and a new one starts. This prevents RPC spam during rapid skipping.
        debounceJob = discordScope.launch {
            delay(DEBOUNCE_DELAY_MS)
            if (isStopped) return@launch
            // Media item exists → always show music status (playing or paused), never browsing
            if (isPlaying) {
                sendPlayingPresence(mediaItem, position, duration)
            } else {
                sendPausedPresence(duration, now, position)
            }
        }
    }

    private var cachedPausedTimestamp: Long = 0L

    /**
     * Send the "Paused" presence with the frozen time.
     */
    private fun sendPausedPresence(duration: Long, now: Long, pausedPosition: Long) {
        if (isStopped) return
        val mediaItem = lastMediaItem ?: return
        var frozenTimestamp = now - pausedPosition
        
        val mediaId = mediaItem.mediaId
        if (cachedMediaId == mediaId && wasPaused) {
            // Keep the previous frozen timestamp to avoid UI resets
            frozenTimestamp = cachedPausedTimestamp
        } else {
            cachedMediaId = mediaId
            cachedPausedTimestamp = frozenTimestamp
            wasPaused = true
        }

        val title = mediaItem.mediaMetadata.title?.toString().takeIf { !it.isNullOrBlank() } ?: context.getString(R.string.unknown_title)
        val artist = mediaItem.artistTextOrDb().takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_artist)
        discordScope.launch {
            if (isStopped) return@launch
            sendActivity(
                mediaItem = mediaItem,
                details = "⏸︎ Paused: $title",
                state = artist,
                start = frozenTimestamp,
                end = frozenTimestamp,
                status = "online",
                paused = true
            )
        }
    }

    private fun formatRouteName(route: String): String {
        return route.split("?").first().split("/").first().replaceFirstChar { it.uppercase() }
    }

    private fun sendBrowsingPresence(route: String) {
        if (isStopped) return
        val formattedRoute = formatRouteName(route)
        Timber.tag("DiscordPresence").d("Browsing presence requested: route=$formattedRoute, enabled=${getBrowsingEnabled()}, hasMediaItem=$hasActiveMediaItem")
        discordScope.launch {
            if (isStopped) return@launch
            sendActivity(
                mediaItem = null,
                details = "Browsing",
                state = formattedRoute,
                start = appStartTime,
                end = 0L,
                status = "online",
                paused = false,
                browsingPrecondition = { getBrowsingEnabled() && !hasActiveMediaItem }
            )
        }
    }

    /**
     * Send a custom discord activity
     */
    private suspend fun sendActivity(
        mediaItem: MediaItem?,
        details: String,
        state: String,
        start: Long,
        end: Long,
        status: String,
        paused: Boolean,
        browsingPrecondition: (() -> Boolean)? = null
    ) {
        if (isStopped) return
        val token = getToken() ?: return
        if (token.isEmpty()) return

        if (token != lastToken) {
            val validate = tokenValidator ?: { validateToken(it) }
            when (validate(token)) {
                false -> {
                    Timber.tag("DiscordPresence").e("Invalid token, stopping presence updates")
                    withContext(NzikDispatchers.UI) {
                        Toaster.e(R.string.discord_token_text_invalid)
                    }
                    return
                }
                null -> {
                    Timber.tag("DiscordPresence").w("Network error while updating presence, skipping.")
                    return
                }
                true -> { /* Token is valid, continue */ }
            }

            rpc?.closeDirect()
            rpc = connectionFactory(token)
            lastToken = token
        }

        // Browsing writes are re-validated right before the RPC write, not only at
        // launch time: token validation above can take seconds, and the user may
        // have disabled browsing (or started music) in that window.
        if (browsingPrecondition != null && !browsingPrecondition()) {
            Timber.tag("DiscordPresence").d("Browsing presence write cancelled: setting disabled or media item active before the write")
            return
        }

        val rawUri = mediaItem?.mediaMetadata?.artworkUri?.toString()
        val largeImageUrl = if (rawUri != null && rawUri.startsWith("http")) rawUri else getLargeImageFallback()
        val smallImageUrl = getSmallImageUrl()
        val largeTextValue = if (state.isNotBlank()) "$details - $state" else details
        val buttonsList = mutableListOf(Button(label = context.getString(R.string.txt_get_n_zik), url = "https://github.com/N-Zik-Group/N-Zik/"))
        if (mediaItem != null) {
            buttonsList.add(Button(label = context.getString(R.string.txt_listen_to_ytmusic), url = "https://music.youtube.com/watch?v=${mediaItem.mediaId}"))
        }
        
        runCatching {
            rpc?.setActivity(
                applicationId = APPLICATION_ID,
                name = "N-Zik",
                details = details,
                state = state,
                type = ActivityType.LISTENING,
                timestamps = Timestamps(
                    start = start,
                    end = if (end > 0L) end else null
                ),
                largeImage = largeImageUrl,
                smallImage = smallImageUrl,
                largeText = largeTextValue,
                smallText = "v${getVersionName(context)}",
                buttons = buttonsList,
                status = status,
                since = System.currentTimeMillis()
            )
        }.onFailure {
            Timber.tag("DiscordPresence").w("Error setting Discord activity: ${it.message}")
        }
    }

    /**
     * Close the discord presence (STOP)
     */
    fun onStop() {
        isStopped = true
        debounceJob?.cancel()
        refreshJob?.cancel()
        rpc?.closeDirect()
        discordScope.cancel()
    }

    /**
     * Called when the discord browsing setting is toggled
     */
    fun onBrowsingSettingChanged() {
        if (isStopped || hasActiveMediaItem) {
            Timber.tag("DiscordPresence").d("Browsing setting changed, ignored: stopped=$isStopped, hasMediaItem=$hasActiveMediaItem (music status takes priority)")
            return
        }
        if (getBrowsingEnabled()) {
            DiscordUiState.currentRoute.value?.let { route ->
                sendBrowsingPresence(route)
            }
        } else {
            Timber.tag("DiscordPresence").d("Browsing setting disabled, clearing activity")
            discordScope.launch { rpc?.clearActivity() }
        }
    }

    /**
     * Get the version name of the app
     */
    fun getVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private var cachedMediaId: String? = null
    private var cachedStartTime: Long = 0L
    private var cachedEndTime: Long = 0L
    private var wasPaused: Boolean = false

    private fun sendPlayingPresence(mediaItem: MediaItem, position: Long, duration: Long) {
        val currentTime = System.currentTimeMillis()
        var calculatedStartTime = currentTime - position
        var end = if (duration > 0) currentTime + (duration - position) else 0L

        val mediaId = mediaItem.mediaId
        if (cachedMediaId == mediaId && !wasPaused) {
            // Allow up to 2.5 seconds of drift to account for execution delays
            if (kotlin.math.abs(calculatedStartTime - cachedStartTime) < 2500L) {
                calculatedStartTime = cachedStartTime
                end = cachedEndTime
            } else {
                cachedStartTime = calculatedStartTime
                cachedEndTime = end
            }
        } else {
            cachedMediaId = mediaId
            cachedStartTime = calculatedStartTime
            cachedEndTime = end
            wasPaused = false
        }

        val title = mediaItem.mediaMetadata.title?.toString().takeIf { !it.isNullOrBlank() } ?: context.getString(R.string.unknown_title)
        val artist = mediaItem.artistTextOrDb().takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_artist)
        discordScope.launch {
            sendActivity(
                mediaItem = mediaItem,
                details = title,
                state = artist,
                start = calculatedStartTime,
                end = end,
                status = "online",
                paused = false
            )
        }
    }


}

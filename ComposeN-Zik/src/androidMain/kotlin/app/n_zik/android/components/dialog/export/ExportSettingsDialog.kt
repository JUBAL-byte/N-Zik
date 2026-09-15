package app.n_zik.android.components.dialog.export

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import app.n_zik.android.BuildConfig
import app.n_zik.android.extensions.lastfm.isLastfmNowPlayingEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobbleEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobblingEnabledKey
import app.n_zik.android.extensions.lastfm.lastfmAvatarUrlKey
import app.n_zik.android.extensions.lastfm.lastfmMaxScrobbleDelaySecondsKey
import app.n_zik.android.extensions.lastfm.lastfmMinTrackDurationSecondsKey
import app.n_zik.android.extensions.lastfm.lastfmScrobbleThresholdPercentKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.extensions.lastfm.lastfmUsernameKey
import app.it.fast4x.rimusic.utils.discordAvatarKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.discordUsernameKey
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.enableYouTubeSyncKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.isDiscordBrowsingEnabledKey
import app.it.fast4x.rimusic.utils.isDiscordPresenceEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.useYtLoginOnlyForBrowseKey
import app.it.fast4x.rimusic.utils.ytAccountChannelHandleKey
import app.it.fast4x.rimusic.utils.ytAccountEmailKey
import app.it.fast4x.rimusic.utils.ytAccountNameKey
import app.it.fast4x.rimusic.utils.ytAccountThumbnailKey
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.it.fast4x.rimusic.utils.ytDataSyncIdKey
import app.it.fast4x.rimusic.utils.ytVisitorDataKey
import androidx.compose.runtime.MutableState

class ExportSettingsDialog private constructor(
    private val launcher: ManagedActivityResultLauncher<String, Uri?>,
    private val context: Context,
    private val includeYtbState: MutableState<Boolean>,
    private val includeDiscordState: MutableState<Boolean>,
    private val includeLastfmState: MutableState<Boolean>
) {
    companion object {
        private fun onExport(
            uri: Uri,
            context: Context,
            includeYtb: Boolean,
            includeDiscord: Boolean,
            includeLastfm: Boolean
        ) = CoroutineScope( Dispatchers.IO ).launch {
            runCatching {
                Timber.tag("ExportSettingsDialog").d("Starting settings export...")
                val entries: MutableList<Triple<String, String, Any>> = context.preferences
                    .all
                    .map {
                        val value = it.value ?: Unit
                        val type = value::class.simpleName ?: "null"
                        Triple( type, it.key, value )
                    }
                    .filter { it.first != "null" && it.third !== Unit }
                    .toMutableList()

                if (includeYtb || includeDiscord || includeLastfm) {
                    entries.addAll(buildCredentialEntries(context.encryptedPreferences.all, includeYtb, includeDiscord, includeLastfm))
                }

                Timber.tag("ExportSettingsDialog").d("Found ${entries.size} settings entries")

                context.contentResolver
                    .openOutputStream( uri )
                    ?.use { outStream ->
                        csvWriter().open( outStream ) {
                            writeRow( "Type", "Key", "Value" )
                            flush()
                            entries.forEach {
                                writeRow( it.first, it.second, it.third )
                            }
                            close()
                        }
                        Timber.tag("ExportSettingsDialog").d("Settings export complete")
                    } ?: Timber.tag("ExportSettingsDialog").w("Failed to open output stream")
            }.onFailure { e ->
                Timber.tag("ExportSettingsDialog").e(e, "Settings export failed")
            }
        }

        internal fun buildCredentialEntries(
            encryptedPrefs: Map<String, Any?>,
            includeYtb: Boolean,
            includeDiscord: Boolean,
            includeLastfm: Boolean
        ): List<Triple<String, String, Any>> {
            val ytbKeys = listOf(
                ytCookieKey,
                ytVisitorDataKey,
                ytDataSyncIdKey,
                ytAccountNameKey,
                ytAccountEmailKey,
                ytAccountChannelHandleKey,
                ytAccountThumbnailKey,
                enableYouTubeLoginKey,
                enableYouTubeSyncKey,
                useYtLoginOnlyForBrowseKey
            )
            val discordKeys = listOf(
                discordPersonalAccessTokenKey,
                discordAvatarKey,
                discordUsernameKey,
                isDiscordPresenceEnabledKey,
                isDiscordBrowsingEnabledKey
            )
            val lastfmKeys = listOf(
                lastfmSessionKey,
                lastfmUsernameKey,
                lastfmAvatarUrlKey,
                isLastfmScrobblingEnabledKey,
                isLastfmNowPlayingEnabledKey,
                isLastfmScrobbleEnabledKey,
                lastfmMinTrackDurationSecondsKey,
                lastfmScrobbleThresholdPercentKey,
                lastfmMaxScrobbleDelaySecondsKey
            )
            val entries = mutableListOf<Triple<String, String, Any>>()
            if (includeYtb) {
                ytbKeys.forEach { key ->
                    encryptedPrefs[key]?.let { value ->
                        val type = value::class.simpleName ?: "null"
                        if (type != "null") entries.add(Triple(type, key, value))
                    }
                }
            }
            if (includeDiscord) {
                discordKeys.forEach { key ->
                    encryptedPrefs[key]?.let { value ->
                        val type = value::class.simpleName ?: "null"
                        if (type != "null") entries.add(Triple(type, key, value))
                    }
                }
            }
            if (includeLastfm) {
                lastfmKeys.forEach { key ->
                    encryptedPrefs[key]?.let { value ->
                        val type = value::class.simpleName ?: "null"
                        if (type != "null") entries.add(Triple(type, key, value))
                    }
                }
            }
            return entries
        }

        @Composable
        operator fun invoke( context: Context ): ExportSettingsDialog {
            val includeYtbState = remember { mutableStateOf(false) }
            val includeDiscordState = remember { mutableStateOf(false) }
            val includeLastfmState = remember { mutableStateOf(false) }
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument( "text/csv" )
            ) { uri ->
                Timber.tag("ExportSettingsDialog").d("File picker callback received, uri: $uri")
                uri ?: return@rememberLauncherForActivityResult
                val ytb = includeYtbState.value
                val discord = includeDiscordState.value
                val lastfm = includeLastfmState.value
                onExport( uri, context, ytb, discord, lastfm )
            }
            return remember(launcher, context) {
                ExportSettingsDialog(launcher, context, includeYtbState, includeDiscordState, includeLastfmState)
            }
        }
    }

    fun export(includeYtb: Boolean = false, includeDiscord: Boolean = false, includeLastfm: Boolean = false) {
        includeYtbState.value = includeYtb
        includeDiscordState.value = includeDiscord
        includeLastfmState.value = includeLastfm
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val fileName = "${BuildConfig.APP_NAME} $date Settings"
        Timber.tag("ExportSettingsDialog").d("Launching file picker with name: $fileName.csv")
        launcher.launch("$fileName.csv")
    }
}

package app.n_zik.android.core.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.n_zik.android.BuildConfig
import app.n_zik.android.core.database.Database
import app.n_zik.android.extensions.lastfm.isLastfmNowPlayingEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobbleEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobblingEnabledKey
import app.n_zik.android.extensions.lastfm.lastfmAvatarUrlKey
import app.n_zik.android.extensions.lastfm.lastfmMaxScrobbleDelaySecondsKey
import app.n_zik.android.extensions.lastfm.lastfmMinTrackDurationSecondsKey
import app.n_zik.android.extensions.lastfm.lastfmScrobbleThresholdPercentKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.extensions.lastfm.lastfmUsernameKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import app.it.fast4x.rimusic.utils.*
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import java.time.Year

object BackupManager {
    const val PREF_INTERVAL = "autoBackupIntervalKey"
    const val PREF_DB_CHANGE = "autoBackupOnDbChangeKey"
    const val PREF_URI = "autoBackupUriKey"
    const val PREF_MAX_BACKUPS = "autoBackupMaxBackupsKey"
    const val PREF_CUSTOM_INTERVAL_VALUE = "autoBackupCustomIntervalValueKey"
    const val PREF_CUSTOM_INTERVAL_UNIT = "autoBackupCustomIntervalUnitKey"
    const val PREF_SCHEDULE_HOUR = "autoBackupScheduleHourKey"
    const val PREF_SCHEDULE_DAY_OF_WEEK = "autoBackupScheduleDayOfWeekKey"
    const val PREF_SCHEDULE_DAY_OF_MONTH = "autoBackupScheduleDayOfMonthKey"
    const val PREF_TARGET = "autoBackupTargetKey"
    const val PREF_INCLUDE_YTB = "autoBackupIncludeYtbKey"
    const val PREF_INCLUDE_DISCORD = "autoBackupIncludeDiscordKey"
    const val PREF_INCLUDE_LASTFM = "autoBackupIncludeLastfmKey"
    const val PREF_PRE_INSTALL = "autoBackupPreInstallKey"

    const val TARGET_DATABASE = 0
    const val TARGET_SETTINGS = 1
    const val TARGET_BOTH = 2

    const val INTERVAL_NONE = 0
    const val INTERVAL_HOURLY = 1
    const val INTERVAL_DAILY = 2
    const val INTERVAL_WEEKLY = 3
    const val INTERVAL_CUSTOM = 4
    const val INTERVAL_MONTHLY = 5

    const val UNIT_MINUTES = 1
    const val UNIT_HOURS = 2
    const val UNIT_DAYS = 3
    const val UNIT_WEEKS = 4
    const val UNIT_MONTHS = 5

    private const val WORK_NAME = "AutoBackupWorker"

    fun scheduleBackup(context: Context, interval: Int) {
        Timber.tag("AutoBackup").d("scheduleBackup called with interval: $interval")
        val workManager = WorkManager.getInstance(context)
        if (interval == INTERVAL_NONE) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val now = LocalDateTime.now()
        var targetTime = now

        when (interval) {
            INTERVAL_HOURLY -> {
                targetTime = now.plusHours(1)
            }
            INTERVAL_CUSTOM -> {
                val value = prefs.getInt(PREF_CUSTOM_INTERVAL_VALUE, 12).toLong()
                val unit = prefs.getInt(PREF_CUSTOM_INTERVAL_UNIT, UNIT_HOURS)
                targetTime = when (unit) {
                    UNIT_MINUTES -> now.plusMinutes(value)
                    UNIT_HOURS -> now.plusHours(value)
                    UNIT_DAYS -> now.plusDays(value)
                    UNIT_WEEKS -> now.plusWeeks(value)
                    UNIT_MONTHS -> now.plusMonths(value)
                    else -> now.plusHours(value)
                }
            }
            INTERVAL_DAILY -> {
                val hour = prefs.getInt(PREF_SCHEDULE_HOUR, 2)
                targetTime = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
                if (!targetTime.isAfter(now)) {
                    targetTime = targetTime.plusDays(1)
                }
            }
            INTERVAL_WEEKLY -> {
                val hour = prefs.getInt(PREF_SCHEDULE_HOUR, 2)
                val dayOfWeek = prefs.getInt(PREF_SCHEDULE_DAY_OF_WEEK, 1) // 1 = Monday
                targetTime = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
                
                while (targetTime.dayOfWeek.value != dayOfWeek || !targetTime.isAfter(now)) {
                    targetTime = targetTime.plusDays(1)
                }
            }
            INTERVAL_MONTHLY -> {
                val hour = prefs.getInt(PREF_SCHEDULE_HOUR, 2)
                val dayOfMonth = prefs.getInt(PREF_SCHEDULE_DAY_OF_MONTH, 1)
                targetTime = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
                
                val maxDays = targetTime.month.length(Year.isLeap(targetTime.year.toLong()))
                targetTime = targetTime.withDayOfMonth(minOf(dayOfMonth, maxDays))
                
                if (!targetTime.isAfter(now)) {
                    targetTime = targetTime.plusMonths(1)
                    val nextMaxDays = targetTime.month.length(Year.isLeap(targetTime.year.toLong()))
                    targetTime = targetTime.withDayOfMonth(minOf(dayOfMonth, nextMaxDays))
                }
            }
        }

        val initialDelay = ChronoUnit.MILLIS.between(now, targetTime)
        Timber.tag("AutoBackup").d("scheduleBackup: Enqueuing work with delay $initialDelay ms (Target time: $targetTime)")

        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun triggerOnChangeBackup(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val interval = prefs.getInt(PREF_INTERVAL, INTERVAL_NONE)
        if (interval == INTERVAL_NONE) return

        val enabled = prefs.getBoolean(PREF_DB_CHANGE, false)

        if (!enabled) return

        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay(1, TimeUnit.MINUTES) // Debounce 1 minute
            .build()


        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun executePreInstallBackup(context: Context): Boolean {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(PREF_PRE_INSTALL, false)
        if (!enabled) return true

        val uriString = prefs.getString(PREF_URI, "") ?: ""
        if (uriString.isEmpty()) {
            Timber.tag("AutoBackup").w("Pre-install backup: no URI configured")
            return false
        }

        Timber.tag("AutoBackup").i("Pre-install backup triggered")
        return executeBackup(context, uriString)
    }

    suspend fun executeBackup(context: Context, uriString: String): Boolean {
        Timber.tag("AutoBackup").i("Starting executeBackup to URI: $uriString")
        if (uriString.isEmpty()) return false
        val treeUri = Uri.parse(uriString)

        return withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
                val target = prefs.getInt(PREF_TARGET, TARGET_DATABASE)
                val includeYtb = prefs.getBoolean(PREF_INCLUDE_YTB, false)
                val includeDiscord = prefs.getBoolean(PREF_INCLUDE_DISCORD, false)
                val includeLastfm = prefs.getBoolean(PREF_INCLUDE_LASTFM, false)
                Timber.tag("AutoBackup").d("executeBackup settings: Target=$target, IncludeYtb=$includeYtb, IncludeDiscord=$includeDiscord, IncludeLastfm=$includeLastfm")
                
                var success = true
                val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))

                if (target == TARGET_DATABASE || target == TARGET_BOTH) {
                    Timber.tag("AutoBackup").d("Starting database backup...")
                    Database.checkpoint()
                    val fileName = "${BuildConfig.APP_NAME}_${date}_AutoBackup.sqlite"
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri, DocumentsContract.getTreeDocumentId(treeUri)
                    )
                    val newDocUri = DocumentsContract.createDocument(
                        context.contentResolver, docUri, "application/vnd.sqlite3", fileName
                    )
                    if (newDocUri != null) {
                        context.contentResolver.openOutputStream(newDocUri)?.use { outStream ->
                            val dbFile = context.getDatabasePath(Database.FILE_NAME)
                            FileInputStream(dbFile).use { inStream ->
                                inStream.copyTo(outStream)
                            }
                            Timber.tag("AutoBackup").i("Database backup successful to $fileName")
                        }
                    } else {
                        Timber.tag("AutoBackup").e("Failed to create document for database backup")
                        success = false
                    }
                }

                if (target == TARGET_SETTINGS || target == TARGET_BOTH) {
                    Timber.tag("AutoBackup").d("Starting settings backup...")
                    val fileName = "${BuildConfig.APP_NAME}_${date}_Settings_AutoBackup.csv"
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri, DocumentsContract.getTreeDocumentId(treeUri)
                    )
                    val newDocUri = DocumentsContract.createDocument(
                        context.contentResolver, docUri, "text/csv", fileName
                    )
                    if (newDocUri != null) {
                        exportSettings(context, newDocUri, includeYtb, includeDiscord, includeLastfm)
                        Timber.tag("AutoBackup").i("Settings backup successful to $fileName")
                    } else {
                        Timber.tag("AutoBackup").e("Failed to create document for settings backup")
                        success = false
                    }
                }

                Timber.tag("AutoBackup").d("Enforcing retention policy...")
                enforceRetentionPolicy(context, treeUri)
                Timber.tag("AutoBackup").i("executeBackup finished. Success: $success")
                success
            } catch (e: Exception) {
                Timber.tag("AutoBackup").e(e, "executeBackup failed with exception")
                false
            }
        }
    }

    private fun enforceRetentionPolicy(context: Context, treeUri: Uri) {
        Timber.tag("AutoBackup").d("enforceRetentionPolicy started")
        try {
            val maxBackups = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
                .getInt(PREF_MAX_BACKUPS, 5)
                
            if (maxBackups == -1) {
                Timber.tag("AutoBackup").d("enforceRetentionPolicy: Max backups is unlimited (-1). Skipping.")
                return
            }
            Timber.tag("AutoBackup").d("enforceRetentionPolicy: Max backups allowed is $maxBackups")

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            
            val cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null,
                null,
                "${DocumentsContract.Document.COLUMN_LAST_MODIFIED} DESC"
            )

            cursor?.use {
                var dbCount = 0
                var settingsCount = 0
                while (it.moveToNext()) {
                    val displayName = it.getString(1) ?: ""
                    val isDbBackup = displayName.contains("AutoBackup") && displayName.endsWith(".sqlite")
                    val isSettingsBackup = displayName.contains("Settings_AutoBackup") && displayName.endsWith(".csv")
                    
                    if (isDbBackup) {
                        dbCount++
                        if (dbCount > maxBackups) {
                            val docId = it.getString(0)
                            val deleteUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            DocumentsContract.deleteDocument(context.contentResolver, deleteUri)
                            Timber.tag("AutoBackup").i("enforceRetentionPolicy: Deleted old DB backup: $displayName")
                        }
                    } else if (isSettingsBackup) {
                        settingsCount++
                        if (settingsCount > maxBackups) {
                            val docId = it.getString(0)
                            val deleteUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            DocumentsContract.deleteDocument(context.contentResolver, deleteUri)
                            Timber.tag("AutoBackup").i("enforceRetentionPolicy: Deleted old settings backup: $displayName")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("AutoBackup").e(e, "Failed to enforce retention policy")
        }
    }

    private suspend fun exportSettings(context: Context, uri: Uri, includeYtb: Boolean, includeDiscord: Boolean, includeLastfm: Boolean) {
        val entries: MutableList<Triple<String, String, Any>> = context.preferences
            .all
            .map {
                val value = it.value ?: Unit
                val type = value::class.simpleName ?: "null"
                Triple(type, it.key, value)
            }
            .filter { it.first != "null" && it.third !== Unit }
            .toMutableList()

        Timber.tag("AutoBackup").d("exportSettings: Base settings count: ${entries.size}")

        if (includeYtb || includeDiscord || includeLastfm) {
            val ytbKeys = listOf(
                ytCookieKey, ytVisitorDataKey, ytDataSyncIdKey, ytAccountNameKey, ytAccountEmailKey,
                ytAccountChannelHandleKey, ytAccountThumbnailKey, enableYouTubeLoginKey, enableYouTubeSyncKey,
                useYtLoginOnlyForBrowseKey
            )
            val discordKeys = listOf(
                discordPersonalAccessTokenKey, discordAvatarKey, discordUsernameKey,
                isDiscordPresenceEnabledKey, isDiscordBrowsingEnabledKey
            )
            val lastfmKeys = listOf(
                lastfmSessionKey, lastfmUsernameKey, lastfmAvatarUrlKey, isLastfmScrobblingEnabledKey,
                isLastfmNowPlayingEnabledKey, isLastfmScrobbleEnabledKey, lastfmMinTrackDurationSecondsKey,
                lastfmScrobbleThresholdPercentKey, lastfmMaxScrobbleDelaySecondsKey
            )
            val encryptedPrefs = context.encryptedPreferences.all
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
        }

        Timber.tag("AutoBackup").d("exportSettings: Final settings count to write: ${entries.size} (Include Ytb: $includeYtb, Include Discord: $includeDiscord, Include Lastfm: $includeLastfm)")

        context.contentResolver.openOutputStream(uri)?.use { outStream ->
            csvWriter().open(outStream) {
                writeRow("Type", "Key", "Value")
                flush()
                entries.forEach { writeRow(it.first, it.second, it.third) }
                close()
            }
        }
    }

    suspend fun verifyBackupLocation(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val uriString = prefs.getString(PREF_URI, "") ?: ""
        if (uriString.isEmpty()) return

        try {
            val treeUri = Uri.parse(uriString)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri)
            )
            
            var exists = false
            try {
                val cursor = withContext(Dispatchers.IO) {
                    context.contentResolver.query(
                        docUri,
                        arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                        null, null, null
                    )
                }
                cursor?.use {
                    if (it.moveToFirst()) exists = true
                }
            } catch (e: Exception) {
                exists = false
            }

            if (!exists) {
                Timber.tag("AutoBackup").w("Backup location is invalid or missing. Resetting PREF_URI.")
                prefs.edit().putString(PREF_URI, "").apply()
                // Stop the backup from running and reset interval to avoid silent failures
                prefs.edit().putInt(PREF_INTERVAL, INTERVAL_NONE).apply()
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            }
        } catch (e: Exception) {
            Timber.tag("AutoBackup").e(e, "Error verifying backup location")
            prefs.edit().putString(PREF_URI, "").apply()
            prefs.edit().putInt(PREF_INTERVAL, INTERVAL_NONE).apply()
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

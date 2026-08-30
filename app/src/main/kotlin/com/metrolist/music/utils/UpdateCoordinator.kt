package com.metrolist.music.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.metrolist.music.BuildConfig
import com.metrolist.music.MainActivity
import com.metrolist.music.R
import com.metrolist.music.constants.CheckForUpdatesKey
import com.metrolist.music.constants.LastUpdateCheckTimeKey
import com.metrolist.music.constants.UpdateNotificationsEnabledKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object UpdateCoordinator {
    const val UPDATE_NOTIFICATION_ID = 1001

    @Volatile
    var pendingUpdateVersion: String? = null

    @Volatile
    var downloadedApkReady: Boolean = false
        private set

    suspend fun checkForUpdates(
        context: Context,
        notifyIfAvailable: Boolean = true,
        autoDownload: Boolean = true,
    ): Result<Pair<ReleaseInfo?, Boolean>> {
        if (!BuildConfig.UPDATER_AVAILABLE) {
            return Result.success(null to false)
        }

        val app = context.applicationContext
        val updatesEnabled = app.dataStore.get(CheckForUpdatesKey, true)
        if (!updatesEnabled) {
            pendingUpdateVersion = null
            downloadedApkReady = false
            return Result.success(null to false)
        }

        return withContext(Dispatchers.IO) {
            Updater.checkForUpdate(forceRefresh = notifyIfAvailable).onSuccess { (releaseInfo, hasUpdate) ->
                app.safeDataStoreEdit { prefs ->
                    prefs[LastUpdateCheckTimeKey] = System.currentTimeMillis()
                }
                if (releaseInfo != null && hasUpdate) {
                    pendingUpdateVersion = releaseInfo.versionName
                    if (notifyIfAvailable && app.dataStore.get(UpdateNotificationsEnabledKey, true)) {
                        showUpdateNotification(app, releaseInfo.versionName)
                    }
                    if (autoDownload) {
                        preloadApk(app, releaseInfo)
                    }
                } else {
                    pendingUpdateVersion = null
                    downloadedApkReady = false
                }
            }.onFailure {
                Timber.w(it, "Update check failed")
            }
        }
    }

    /**
     * Download the APK in the background so Install can open the system installer immediately.
     */
    suspend fun preloadApk(
        context: Context,
        releaseInfo: ReleaseInfo? = Updater.getCachedLatestRelease(),
    ): Boolean =
        withContext(Dispatchers.IO) {
            val info = releaseInfo ?: return@withContext false
            val url = Updater.getDownloadUrlForCurrentVariant(info) ?: return@withContext false
            val apk = ApkInstaller.apkFile(context)
            if (apk.exists() && apk.length() > 1024 && downloadedApkReady) {
                return@withContext true
            }
            Updater
                .downloadApk(url, apk)
                .onSuccess {
                    downloadedApkReady = true
                    Timber.i("Update APK preloaded: %s (%d bytes)", it.absolutePath, it.length())
                }.onFailure {
                    downloadedApkReady = false
                    Timber.w(it, "Failed to preload update APK")
                }.isSuccess
        }

    fun isApkReady(context: Context): Boolean {
        val apk = ApkInstaller.apkFile(context)
        return downloadedApkReady && apk.exists() && apk.length() > 1024
    }

    fun consumePendingUpdate(): String? {
        val version = pendingUpdateVersion
        pendingUpdateVersion = null
        return version?.takeIf { Updater.isUpdateAvailable(BuildConfig.VERSION_NAME, it) }
    }

    fun showUpdateNotification(
        context: Context,
        versionName: String,
    ) {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_SHOW_UPDATE_DIALOG, true)
            }
        val pending =
            PendingIntent.getActivity(
                context,
                UPDATE_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, "updates")
                .setSmallIcon(R.drawable.update)
                .setContentTitle(context.getString(R.string.update_available_title))
                .setContentText(versionName)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
        }
    }

    const val EXTRA_SHOW_UPDATE_DIALOG = "show_update_dialog"
}

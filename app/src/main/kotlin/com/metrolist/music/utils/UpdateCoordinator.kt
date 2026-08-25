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
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object UpdateCoordinator {
    const val UPDATE_NOTIFICATION_ID = 1001

    @Volatile
    var pendingUpdateVersion: String? = null

    suspend fun checkForUpdates(
        context: Context,
        notifyIfAvailable: Boolean = true,
    ): Result<Pair<ReleaseInfo?, Boolean>> {
        if (!BuildConfig.UPDATER_AVAILABLE) {
            return Result.success(null to false)
        }

        val app = context.applicationContext
        val updatesEnabled = app.dataStore.get(CheckForUpdatesKey, true)
        if (!updatesEnabled) {
            pendingUpdateVersion = null
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
                } else {
                    pendingUpdateVersion = null
                }
            }.onFailure {
                Timber.w(it, "Update check failed")
            }
        }
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

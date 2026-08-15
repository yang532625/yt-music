/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/**
 * Installs a downloaded APK over the existing package. Android keeps app data
 * (settings, login cookies, library) when applicationId and signing cert match.
 */
object ApkInstaller {
    fun apkFile(context: Context): File = File(context.cacheDir, "updates/YT-Music.apk")

    fun canRequestInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent =
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        }
    }

    fun install(context: Context, apk: File = apkFile(context)) {
        require(apk.exists()) { "APK not found" }
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                apk,
            )
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }

    suspend fun downloadAndInstall(context: Context, downloadUrl: String) {
        val apk = apkFile(context)
        Updater.downloadApk(downloadUrl, apk).getOrThrow()
        install(context, apk)
    }
}

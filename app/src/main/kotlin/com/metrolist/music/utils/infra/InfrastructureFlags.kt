package com.metrolist.music.utils.infra

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.metrolist.music.BuildConfig

object InfrastructureFlags {
    @Volatile
    var pipEnabled: Boolean = true
        private set

    @Volatile
    var backgroundEnabled: Boolean = true
        private set

    @Volatile
    var minAppVersion: String = BuildConfig.VERSION_NAME
        private set

    @Volatile
    var apkUpdateRecommended: Boolean = false
        internal set

    fun applyRemoteConfig(config: FirebaseRemoteConfig) {
        pipEnabled = config.getBoolean("pip_enabled")
        backgroundEnabled = config.getBoolean("background_enabled")
        minAppVersion = config.getString("min_app_version").ifBlank { BuildConfig.VERSION_NAME }
    }
}

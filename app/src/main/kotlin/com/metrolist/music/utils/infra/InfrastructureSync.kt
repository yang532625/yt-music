package com.metrolist.music.utils.infra

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.music.BuildConfig
import com.metrolist.music.R
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object InfrastructureSync {
    private const val WORK_NAME = "ytm-infra-sync"
    private val started = AtomicBoolean(false)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        runCatching { applyRemoteConfig() }
        runCatching {
            val request =
                PeriodicWorkRequestBuilder<InfrastructureSyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build()
            WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }.onFailure { Timber.w(it, "WorkManager infra sync not started") }
    }

    suspend fun syncOnce() {
        applyRemoteConfig()
        val config = FirebaseRemoteConfig.getInstance()
        runCatching { config.fetchAndActivate().await() }
        InfrastructureFlags.applyRemoteConfig(config)
        val overlayJson = config.getString("innertube_config")
        if (overlayJson.isNotBlank() && overlayJson != "{}") {
            applyOverlayJson(overlayJson)
        } else {
            refreshFromPublicSources()
        }
    }

    private fun applyRemoteConfig() {
        val config = FirebaseRemoteConfig.getInstance()
        val settings =
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build()
        config.setConfigSettingsAsync(settings)
        config.setDefaultsAsync(R.xml.remote_config_defaults)
        InfrastructureFlags.applyRemoteConfig(config)
        config.fetchAndActivate().addOnCompleteListener {
            InfrastructureFlags.applyRemoteConfig(config)
            val overlayJson = config.getString("innertube_config")
            if (overlayJson.isNotBlank() && overlayJson != "{}") {
                applyOverlayJson(overlayJson)
            }
        }
    }

    private fun applyOverlayJson(raw: String) {
        runCatching {
            val root = JSONObject(raw)
            InfrastructureFlags.apkUpdateRecommended = root.optBoolean("apkUpdateRecommended", false)
            val preferred = jsonStringList(root.optJSONArray("preferredClientOrder"))
            val clientsArray = root.optJSONArray("clients") ?: return@runCatching
            val clients = buildList {
                for (i in 0 until clientsArray.length()) {
                    val map = clientsArray.optJSONObject(i) ?: continue
                    val name = map.optString("clientName")
                    if (name.isBlank()) continue
                    add(
                        YouTubeClient(
                            clientName = name,
                            clientVersion = map.optString("clientVersion"),
                            clientId = map.optString("clientId"),
                            userAgent = map.optString("userAgent"),
                            friendlyName = map.optString("friendlyName").ifBlank { null },
                        ),
                    )
                }
            }
            YouTubeClient.applyRemoteOverlay(clients, preferred)
            Timber.i("Applied InnerTube overlay from Remote Config: %d clients", clients.size)
        }.onFailure { Timber.w(it, "Invalid innertube_config JSON") }
    }

    private fun jsonStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private suspend fun refreshFromPublicSources() {
        val source =
            runCatching {
                java.net.URI.create(
                    "https://raw.githubusercontent.com/LuanRT/YouTube.js/main/src/core/Constants.ts",
                ).toURL().openConnection().apply {
                    setRequestProperty("User-Agent", "YT-Music/${BuildConfig.VERSION_NAME}")
                    connectTimeout = 15000
                    readTimeout = 15000
                }.getInputStream().bufferedReader().use { it.readText() }
            }.getOrNull() ?: return
        val regex =
            Regex(
                "(WEB_REMIX|WEB_CREATOR|TVHTML5|ANDROID|IOS|WEB)\\s*[:=][\\s\\S]{0,400}?VERSION['\"]?\\s*[:=]\\s*['\"]([\\d.]+)['\"]",
                RegexOption.IGNORE_CASE,
            )
        val versions =
            regex.findAll(source)
                .map { it.groupValues[1].uppercase() to it.groupValues[2] }
                .filter { (name, version) ->
                    if (name == "WEB" || name.startsWith("WEB_") || name == "TVHTML5") {
                        version.contains(Regex("20\\d{2}"))
                    } else {
                        version.matches(Regex("^\\d{2}\\.\\d{2}.*"))
                    }
                }.toMap()
        if (versions.isEmpty()) return
        val clients =
            YouTubeClient.allClients().map { local ->
                val next = versions[local.clientName] ?: return@map local
                local.copy(
                    clientVersion = next,
                    userAgent =
                        if (local.userAgent.contains(local.clientVersion)) {
                            local.userAgent.replace(local.clientVersion, next)
                        } else {
                            local.userAgent
                        },
                )
            }
        YouTubeClient.applyRemoteOverlay(clients, versions.keys.toList())
        Timber.i("Applied public InnerTube overlay: %s", versions)
    }
}

class InfrastructureSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        runCatching {
            InfrastructureSync.syncOnce()
            Result.success()
        }.getOrElse {
            Timber.w(it, "Infrastructure sync worker failed")
            Result.retry()
        }
}

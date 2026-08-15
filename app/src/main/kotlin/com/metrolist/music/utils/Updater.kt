/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.music.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val description: String,
    val releaseDate: String,
    val assets: List<ReleaseAsset>
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val architecture: String,
    val variant: String // "foss" or "gms"
)

object Updater {
    private val client = HttpClient(CIO)
    var lastCheckTime = -1L
        private set

    private var cachedReleaseInfo: ReleaseInfo? = null
    private var cachedAllReleases: List<ReleaseInfo> = emptyList()

    private const val CHECK_INTERVAL_MILLIS = 2 * 60 * 60 * 1000L
    const val GITHUB_REPO = "yang532625/yt-music"
    const val RELEASES_URL = "https://github.com/$GITHUB_REPO/releases"
    private const val API_LATEST = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val API_ALL = "https://api.github.com/repos/$GITHUB_REPO/releases?per_page=20"
    private const val USER_AGENT = "YT-Music-Android (${BuildConfig.VERSION_NAME})"

    fun compareVersions(v1: String, v2: String): Int {
        val v1Parts = v1.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val v2Parts = v2.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(v1Parts.size, v2Parts.size)

        for (i in 0 until maxLength) {
            val part1 = v1Parts.getOrNull(i) ?: 0
            val part2 = v2Parts.getOrNull(i) ?: 0
            when {
                part1 > part2 -> return 1
                part1 < part2 -> return -1
            }
        }
        return 0
    }

    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return compareVersions(latestVersion, currentVersion) > 0
    }

    private fun getCurrentAppVariant(): Pair<String, String> {
        val architecture = BuildConfig.ARCHITECTURE
        val variant = if (BuildConfig.CAST_AVAILABLE) "gms" else "foss"
        return architecture to variant
    }

    private fun classifyAsset(name: String): Pair<String, String> {
        val lower = name.lowercase()
        val variant = when {
            "gms" in lower || "cast" in lower -> "gms"
            "izzy" in lower -> "izzy"
            else -> "foss"
        }
        val architecture = when {
            "arm64" in lower -> "arm64-v8a"
            "armeabi" in lower || "armv7" in lower -> "armeabi-v7a"
            "x86_64" in lower -> "x86_64"
            else -> "universal"
        }
        return architecture to variant
    }

    private fun parseGithubRelease(json: JSONObject): ReleaseInfo? {
        val assetsJson = json.optJSONArray("assets") ?: JSONArray()
        val assets = buildList {
            for (i in 0 until assetsJson.length()) {
                val asset = assetsJson.getJSONObject(i)
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val (architecture, variant) = classifyAsset(name)
                add(
                    ReleaseAsset(
                        name = name,
                        downloadUrl = asset.getString("browser_download_url"),
                        size = asset.optLong("size", 0L),
                        architecture = architecture,
                        variant = variant,
                    ),
                )
            }
        }
        if (assets.isEmpty()) return null

        val tagName = json.optString("tag_name")
        val versionName = tagName.removePrefix("v").ifBlank {
            json.optString("name").removePrefix("v")
        }

        return ReleaseInfo(
            tagName = tagName.ifBlank { versionName },
            versionName = versionName,
            description = json.optString("body", ""),
            releaseDate = json.optString("published_at", ""),
            assets = assets,
        )
    }

    private suspend fun githubGet(url: String): String =
        client
            .get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }.bodyAsText()

    suspend fun getLatestRelease(forceRefresh: Boolean = false): Result<ReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (cachedReleaseInfo != null && !forceRefresh) {
                    return@runCatching cachedReleaseInfo!!
                }

                val releaseInfo =
                    parseGithubRelease(JSONObject(githubGet(API_LATEST)))
                        ?: error("Latest GitHub release has no APK")

                cachedReleaseInfo = releaseInfo
                lastCheckTime = System.currentTimeMillis()
                releaseInfo
            }
        }

    suspend fun getAllReleases(forceRefresh: Boolean = false): Result<List<ReleaseInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (cachedAllReleases.isNotEmpty() && !forceRefresh) {
                    return@runCatching cachedAllReleases
                }

                val array = JSONArray(githubGet(API_ALL))
                val releases = buildList {
                    for (i in 0 until array.length()) {
                        parseGithubRelease(array.getJSONObject(i))?.let(::add)
                    }
                }
                cachedAllReleases = releases
                if (cachedReleaseInfo == null) {
                    cachedReleaseInfo = releases.firstOrNull()
                }
                releases
            }
        }

    fun getDownloadUrlForCurrentVariant(releaseInfo: ReleaseInfo): String? {
        val (currentArch, currentVariant) = getCurrentAppVariant()

        return releaseInfo.assets
            .find { it.architecture == currentArch && it.variant == currentVariant }
            ?.downloadUrl
            ?: releaseInfo.assets.find { it.variant == currentVariant }?.downloadUrl
            ?: releaseInfo.assets.find { it.name.endsWith(".apk", ignoreCase = true) }?.downloadUrl
    }

    fun getAllDownloadUrls(releaseInfo: ReleaseInfo): Map<String, String> {
        return releaseInfo.assets.associate { "${it.architecture}-${it.variant}" to it.downloadUrl }
    }

    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<Pair<ReleaseInfo?, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val shouldFetch = forceRefresh ||
                    (System.currentTimeMillis() - lastCheckTime) > CHECK_INTERVAL_MILLIS

                if (!shouldFetch && cachedReleaseInfo != null) {
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.VERSION_NAME,
                        cachedReleaseInfo!!.versionName
                    )
                    return@runCatching cachedReleaseInfo!! to hasUpdate
                }

                val result = getLatestRelease(forceRefresh = true)
                if (result.isSuccess) {
                    val releaseInfo = result.getOrThrow()
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.VERSION_NAME,
                        releaseInfo.versionName
                    )
                    releaseInfo to hasUpdate
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            }
        }

    fun getLatestDownloadUrl(): String? {
        return cachedReleaseInfo?.let { getDownloadUrlForCurrentVariant(it) }
    }

    fun getCachedLatestRelease(): ReleaseInfo? = cachedReleaseInfo

    suspend fun downloadApk(
        url: String,
        destination: File,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                destination.parentFile?.mkdirs()
                val bytes =
                    client
                        .get(url) {
                            header(HttpHeaders.UserAgent, USER_AGENT)
                            header(HttpHeaders.Accept, "application/octet-stream")
                        }.bodyAsBytes()
                require(bytes.size > 1024) { "Downloaded file is too small to be an APK" }
                destination.writeBytes(bytes)
                destination
            }
        }
}

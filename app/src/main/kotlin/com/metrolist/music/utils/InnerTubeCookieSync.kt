package com.metrolist.music.utils

import android.content.Context
import android.webkit.CookieManager
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.constants.InnerTubeCookieKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Keeps DataStore InnerTube cookies and WebView CookieManager in sync.
 * Browse can work with a stale cookie while playback returns "not a bot" —
 * syncing SAPISID from either side reduces that mismatch.
 */
object InnerTubeCookieSync {
    private const val MUSIC_ORIGIN = "https://music.youtube.com"
    private const val YOUTUBE_ORIGIN = "https://www.youtube.com"

    fun hasSapisid(cookie: String?): Boolean =
        !cookie.isNullOrBlank() && "SAPISID" in parseCookieString(cookie)

    suspend fun sync(context: Context) =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val cm =
                runCatching { CookieManager.getInstance() }.getOrNull()
                    ?: return@withContext

            runCatching { cm.setAcceptCookie(true) }

            val webCookie = cm.getCookie(MUSIC_ORIGIN).orEmpty()
            val stored =
                app.dataStore.data
                    .first()[InnerTubeCookieKey]
                    .orEmpty()

            val webOk = hasSapisid(webCookie)
            val storedOk = hasSapisid(stored)

            when {
                webOk && (!storedOk || webCookie != stored) -> {
                    Timber.d("InnerTubeCookieSync: adopting WebView cookie (SAPISID present)")
                    app.safeDataStoreEdit { prefs ->
                        prefs[InnerTubeCookieKey] = webCookie
                    }
                    YouTube.cookie = webCookie
                }
                storedOk -> {
                    if (webCookie != stored) {
                        Timber.d("InnerTubeCookieSync: pushing DataStore cookie into CookieManager")
                        pushToCookieManager(cm, stored)
                    }
                    YouTube.cookie = stored
                }
                else -> {
                    Timber.d("InnerTubeCookieSync: no SAPISID in WebView or DataStore")
                    if (stored.isNotBlank()) {
                        YouTube.cookie = stored
                    }
                }
            }
        }

    private fun pushToCookieManager(
        cm: CookieManager,
        cookieHeader: String,
    ) {
        parseCookieString(cookieHeader).forEach { (name, value) ->
            val pair = "$name=$value"
            cm.setCookie(MUSIC_ORIGIN, pair)
            cm.setCookie(YOUTUBE_ORIGIN, pair)
        }
        cm.flush()
    }
}

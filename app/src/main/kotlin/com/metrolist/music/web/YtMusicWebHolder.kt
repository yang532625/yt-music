package com.metrolist.music.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class YtMusicWebHolder(
    context: Context,
    private val onState: (PlaybackState) -> Unit = {},
    private val embeddedMode: Boolean = false,
) {
    data class PlaybackState(
        val title: String,
        val artist: String,
        val artwork: String,
        val playing: Boolean,
        val duration: Double,
        val position: Double,
        val hasContent: Boolean = false,
    )

    val webView: WebView
    private val injectJs: String
    private val started = AtomicBoolean(false)
    var onUrlChanged: ((String) -> Unit)? = null
    var onProgress: ((Int) -> Unit)? = null
    var onLoadError: (() -> Unit)? = null
    var onPlayerExpanded: ((Boolean) -> Unit)? = null
    var onHasContent: ((Boolean) -> Unit)? = null
    var onTrackSelected: ((String) -> Unit)? = null

    init {
        val appContext = context.applicationContext
        injectJs = appContext.assets.open("ytm_inject.js").bufferedReader().use { it.readText() }
        webView = KeepAliveWebView(appContext).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
            // Hardware layers often composite to a black WebView on emulator GPUs.
            val emulator = Build.FINGERPRINT.contains("generic") ||
                Build.FINGERPRINT.contains("emulator") ||
                Build.MODEL.contains("sdk_gphone", ignoreCase = true)
            setLayerType(
                if (emulator) android.view.View.LAYER_TYPE_NONE
                else android.view.View.LAYER_TYPE_HARDWARE,
                null,
            )
            configure()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.configure() {
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(this, true)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            offscreenPreRaster = true
            useWideViewPort = false
            loadWithOverviewMode = false
            setSupportZoom(false)
            loadsImagesAutomatically = true
            blockNetworkImage = false
            userAgentString = chromeMobileUserAgent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
        }
        isFocusable = true
        isFocusableInTouchMode = true
        addJavascriptInterface(JsBridge(), "AndroidYtm")
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgress?.invoke(newProgress)
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (embeddedMode) {
                    extractVideoId(url)?.let { videoId ->
                        webView.post { onTrackSelected?.invoke(videoId) }
                        return true
                    }
                }
                val host = request.url.host.orEmpty()
                if (host.endsWith("youtube.com") ||
                    host.endsWith("youtu.be") ||
                    host.endsWith("google.com") ||
                    host.endsWith("googleusercontent.com") ||
                    host.endsWith("gstatic.com") ||
                    host.endsWith("ggpht.com") ||
                    host.endsWith("ytimg.com") ||
                    host.endsWith("googlevideo.com") ||
                    host.endsWith("youtube-nocookie.com")
                ) {
                    return false
                }
                return try {
                    view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                } catch (_: Exception) {
                    true
                }
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (YtMusicAdBlock.shouldBlock(url)) {
                    return YtMusicAdBlock.emptyResponse()
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                onProgress?.invoke(5)
                inject(view)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                inject(view)
                if (embeddedMode) {
                    view.evaluateJavascript("window.__ytmSetEmbedded && window.__ytmSetEmbedded(true)", null)
                }
                CookieManager.getInstance().flush()
                onProgress?.invoke(100)
                onUrlChanged?.invoke(url.orEmpty())
                view.evaluateJavascript(PAGE_BROKEN_JS) { broken ->
                    if (broken == "true") onLoadError?.invoke()
                }
                pollContent(view)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    Timber.w("WebView error %s %s", error.errorCode, error.description)
                    onLoadError?.invoke()
                }
            }
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                this,
                injectJs,
                setOf("https://music.youtube.com"),
            )
        }
    }

    private fun inject(view: WebView) {
        view.evaluateJavascript(injectJs, null)
    }

    fun attach(container: ViewGroup) {
        val parent = webView.parent as? ViewGroup
        if (parent === container) return
        parent?.removeView(webView)
        container.addView(webView)
    }

    fun detach() {
        (webView.parent as? ViewGroup)?.removeView(webView)
    }

    fun loadHome() {
        if (started.compareAndSet(false, true)) {
            webView.loadUrl(HOME_URL)
        }
    }

    fun reload() {
        started.set(true)
        val url = currentUrl().ifBlank { HOME_URL }
        webView.loadUrl(url)
    }

    fun navigate(url: String) {
        val now = normalizeUrl(currentUrl())
        val dest = normalizeUrl(url)
        if (now.isNotEmpty() && now == dest) {
            onUrlChanged?.invoke(currentUrl().ifEmpty { url })
            return
        }
        onProgress?.invoke(15)
        if ("/search" in dest || "/explore" in dest) {
            webView.loadUrl(url)
            onUrlChanged?.invoke(url)
            return
        }
        val js = "window.__ytmGo && window.__ytmGo(${org.json.JSONObject.quote(url)})"
        webView.evaluateJavascript(js) { used ->
            if (used != "true") {
                webView.loadUrl(url)
                return@evaluateJavascript
            }
            webView.postDelayed({
                val arrived = normalizeUrl(currentUrl()) == dest
                if (!arrived) webView.loadUrl(url)
                else pollContent()
            }, 5000)
        }
    }

    fun currentUrl(): String = webView.url.orEmpty()

    fun control(action: String) {
        webView.post {
            webView.evaluateJavascript("window.__ytmControl && window.__ytmControl('$action')", null)
        }
    }

    fun setHeaderMode(mode: String, headerPx: Int) {
        webView.post {
            val js = "window.__ytmSetHeader && window.__ytmSetHeader(${JSONObject.quote(mode)}, 0)"
            webView.evaluateJavascript(js, null)
        }
    }

    fun pollContent(view: WebView = webView) {
        view.evaluateJavascript(HAS_CONTENT_JS) { has ->
            onHasContent?.invoke(has == "true")
        }
    }

    fun onResume() {
        webView.onResume()
        webView.resumeTimers()
    }

    fun keepPlayingInBackground() {
        webView.onResume()
        webView.resumeTimers()
        control("keepalive")
    }

    fun destroy() {
        detach()
        webView.stopLoading()
        webView.destroy()
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun onState(json: String) {
            try {
                val obj = JSONObject(json)
                onState(
                    PlaybackState(
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        artwork = obj.optString("artwork"),
                        playing = obj.optBoolean("playing"),
                        duration = obj.optDouble("duration", 0.0),
                        position = obj.optDouble("position", 0.0),
                        hasContent = obj.optBoolean("hasContent"),
                    ),
                )
                if (obj.optBoolean("hasContent")) {
                    webView.post { onHasContent?.invoke(true) }
                }
            } catch (e: Exception) {
                Timber.w(e, "Bad playback state")
            }
        }

        @JavascriptInterface
        fun onPlayerExpanded(expanded: Boolean) {
            webView.post { onPlayerExpanded?.invoke(expanded) }
        }

        @JavascriptInterface
        fun onTrackSelected(videoId: String) {
            if (videoId.isBlank()) return
            webView.post { onTrackSelected?.invoke(videoId) }
        }
    }

    companion object {
        const val HOME_URL = "https://music.youtube.com/"
        private const val PAGE_BROKEN_JS =
            "(function(){var t=(document.body&&document.body.innerText||'').slice(0,500);" +
                "return /something went wrong|go back and try again|check your connection|sin conexión/i.test(t);})()"
        private const val HAS_CONTENT_JS =
            "(function(){return !!(window.__ytmHasContent && window.__ytmHasContent());})()"

        private fun normalizeUrl(url: String): String {
            return url.substringBefore('#').substringBefore('?').trimEnd('/')
                .lowercase()
                .removePrefix("https://")
                .removePrefix("http://")
        }

        private fun extractVideoId(url: String): String? {
            val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
            val host = uri.host.orEmpty()
            if (!host.endsWith("youtube.com") && !host.endsWith("youtu.be")) return null
            uri.getQueryParameter("v")?.takeIf { it.length == 11 }?.let { return it }
            if (host.endsWith("youtu.be")) {
                val id = uri.pathSegments.firstOrNull()?.takeIf { it.length == 11 }
                if (id != null) return id
            }
            val watchPath = uri.pathSegments.getOrNull(1)
            if (uri.pathSegments.firstOrNull() == "watch" && watchPath?.length == 11) {
                return watchPath
            }
            return null
        }

        private fun chromeMobileUserAgent(): String {
            val version = WebView.getCurrentWebViewPackage()?.versionName
                ?.substringBefore(".")
                ?.toIntOrNull()
                ?: 131
            return "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/$version.0.0.0 Mobile Safari/537.36"
        }
    }
}

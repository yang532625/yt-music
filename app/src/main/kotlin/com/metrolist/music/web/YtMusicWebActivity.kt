package com.metrolist.music.web

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class YtMusicWebActivity : ComponentActivity() {
    private var service: YtMusicPlaybackService? = null
    private var container: FrameLayout? = null
    private var progress: ProgressBar? = null
    private var spinner: View? = null
    private var retryPanel: View? = null
    private var bound = false
    private lateinit var navItems: List<NavTab>
    private lateinit var miniPlayer: View
    private lateinit var miniBody: View
    private lateinit var miniArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniCast: ImageView
    private lateinit var miniPlay: ImageView
    private lateinit var miniProgress: ProgressBar
    private lateinit var premiumPanel: View
    private lateinit var bottomNav: View
    private lateinit var toolbar: View
    private lateinit var toolbarLogoRow: View
    private lateinit var toolbarTitle: TextView
    private lateinit var toolbarBell: ImageView
    private lateinit var toolbarHistory: ImageView
    private lateinit var toolbarSearch: ImageView
    private lateinit var toolbarAccount: ImageView
    private var showingPremium = false
    private var playerExpanded = false
    private var nativeHeaderPx = 0
    private var pageHasContent = false
    private var everHadContent = false
    private var lastArtUrl = ""
    private var lastTitle = ""
    private var lastArtist = ""
    private val loadTimeout = Runnable {
        if (spinner?.visibility == View.VISIBLE) {
            spinner?.visibility = View.GONE
            progress?.visibility = View.GONE
            if (!everHadContent) {
                retryPanel?.visibility = View.VISIBLE
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as YtMusicPlaybackService.LocalBinder).service
            attachWebView()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ytm_web)
        container = findViewById(R.id.ytm_web_container)
        progress = findViewById(R.id.ytm_web_progress)
        spinner = findViewById(R.id.ytm_web_spinner)
        retryPanel = findViewById(R.id.ytm_web_retry)
        findViewById<View>(R.id.ytm_web_retry_button).setOnClickListener {
            retryPanel?.visibility = View.GONE
            spinner?.visibility = View.VISIBLE
            service?.holder?.reload()
        }
        bindMiniPlayer()
        bindToolbar()
        bindPremiumPanel()
        bindBottomNav()
        applySystemBars()
        requestNotifications()
        requestUnrestrictedBattery()
        YtMusicPlaybackService.start(this)
        bound = bindService(
            Intent(this, YtMusicPlaybackService::class.java),
            connection,
            BIND_AUTO_CREATE,
        )
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (playerExpanded) {
                        service?.holder?.control("collapse")
                        return
                    }
                    if (showingPremium) {
                        showPremium(false)
                        highlightTab(service?.holder?.currentUrl().orEmpty().ifEmpty { YtMusicWebHolder.HOME_URL })
                        return
                    }
                    val webView = service?.holder?.webView
                    if (webView != null && webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        moveTaskToBack(true)
                        service?.holder?.keepPlayingInBackground()
                    }
                }
            },
        )
        WebView.setWebContentsDebuggingEnabled(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        attachWebView()
    }

    override fun onStart() {
        super.onStart()
        attachWebView()
        service?.holder?.onResume()
    }

    override fun onResume() {
        super.onResume()
        applySystemBars()
        service?.holder?.onResume()
        service?.holder?.keepPlayingInBackground()
    }

    override fun onPause() {
        service?.holder?.keepPlayingInBackground()
        super.onPause()
    }

    override fun onStop() {
        service?.holder?.keepPlayingInBackground()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        service?.holder?.keepPlayingInBackground()
    }

    override fun onDestroy() {
        spinner?.removeCallbacks(loadTimeout)
        service?.holder?.detach()
        if (bound) {
            service?.onUiState = null
            service?.holder?.onPlayerExpanded = null
            service?.holder?.onHasContent = null
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }

    private fun attachWebView() {
        val holder = service?.holder ?: return
        val root = container ?: return
        holder.attach(root)
        holder.onUrlChanged = { url ->
            if (!showingPremium) {
                highlightTab(url)
                updateToolbarForTab(url)
            }
        }
        holder.onProgress = { p ->
            runOnUiThread {
                progress?.progress = p
                progress?.visibility = if (p in 1..94) View.VISIBLE else View.GONE
                spinner?.removeCallbacks(loadTimeout)
                if (p in 1..94) {
                    pageHasContent = false
                    spinner?.visibility = View.VISIBLE
                    retryPanel?.visibility = View.GONE
                    spinner?.postDelayed(loadTimeout, 45_000)
                } else if (p >= 95 && pageHasContent) {
                    spinner?.visibility = View.GONE
                } else if (p >= 95) {
                    spinner?.visibility = View.VISIBLE
                    spinner?.postDelayed(loadTimeout, 45_000)
                    holder.pollContent()
                }
            }
        }
        holder.onHasContent = { ready ->
            runOnUiThread {
                if (ready) {
                    pageHasContent = true
                    everHadContent = true
                    spinner?.removeCallbacks(loadTimeout)
                    spinner?.visibility = View.GONE
                    progress?.visibility = View.GONE
                    retryPanel?.visibility = View.GONE
                }
            }
        }
        holder.onLoadError = {
            runOnUiThread {
                spinner?.visibility = View.GONE
                progress?.visibility = View.GONE
                retryPanel?.visibility = View.VISIBLE
            }
        }
        service?.onUiState = { state -> renderMiniPlayer(state) }
        holder.onPlayerExpanded = { expanded -> runOnUiThread { setPlayerExpanded(expanded) } }
        service?.lastState?.let { renderMiniPlayer(it) }
        holder.onResume()
        if (!showingPremium) {
            highlightTab(holder.currentUrl())
            updateToolbarForTab(holder.currentUrl())
            syncNativeHeader(holder)
        }
        holder.pollContent()
        if (intent.getBooleanExtra("ytm_autoplay", false)) {
            holder.webView.postDelayed({ holder.control("playFirst") }, 400)
        }
    }

    private fun bindPremiumPanel() {
        premiumPanel = findViewById(R.id.ytm_premium_panel)
        bindPremiumRow(R.id.ytm_premium_row_ads, R.string.premium_ads_title, R.string.premium_ads_body)
        bindPremiumRow(R.id.ytm_premium_row_bg, R.string.premium_bg_title, R.string.premium_bg_body)
        bindPremiumRow(R.id.ytm_premium_row_quality, R.string.premium_quality_title, R.string.premium_quality_body)
        bindPremiumRow(R.id.ytm_premium_row_skips, R.string.premium_skips_title, R.string.premium_skips_body)
        bindPremiumRow(R.id.ytm_premium_row_video, R.string.premium_video_title, R.string.premium_video_body)
        bindPremiumRow(R.id.ytm_premium_row_yt, R.string.premium_yt_title, R.string.premium_yt_body)
        bindPremiumRow(R.id.ytm_premium_row_offline, R.string.premium_offline_title, R.string.premium_offline_body)
    }

    private fun bindPremiumRow(rowId: Int, titleId: Int, bodyId: Int) {
        val row = findViewById<View>(rowId)
        row.findViewById<TextView>(R.id.ytm_premium_row_title).setText(titleId)
        row.findViewById<TextView>(R.id.ytm_premium_row_body).setText(bodyId)
    }

    private fun bindMiniPlayer() {
        miniPlayer = findViewById(R.id.ytm_mini_player)
        miniBody = findViewById(R.id.ytm_mini_body)
        miniArt = findViewById(R.id.ytm_mini_art)
        miniTitle = findViewById(R.id.ytm_mini_title)
        miniArtist = findViewById(R.id.ytm_mini_artist)
        miniCast = findViewById(R.id.ytm_mini_cast)
        miniPlay = findViewById(R.id.ytm_mini_play)
        miniProgress = findViewById(R.id.ytm_mini_progress)
        bottomNav = findViewById(R.id.ytm_bottom_nav)
        miniCast.setColorFilter(Color.WHITE)
        miniPlay.setColorFilter(Color.WHITE)
        miniTitle.isSelected = true
        miniPlay.isClickable = true
        miniCast.isClickable = true
        miniPlay.setOnClickListener { service?.holder?.control("toggle") }
        miniCast.setOnClickListener { service?.holder?.control("cast") }
        miniBody.setOnClickListener { service?.holder?.control("expand") }
    }

    private fun bindToolbar() {
        toolbar = findViewById(R.id.ytm_toolbar_include)
        toolbarLogoRow = findViewById(R.id.ytm_toolbar_logo_row)
        toolbarTitle = findViewById(R.id.ytm_toolbar_title)
        toolbarBell = findViewById(R.id.ytm_toolbar_bell)
        toolbarHistory = findViewById(R.id.ytm_toolbar_history)
        toolbarSearch = findViewById(R.id.ytm_toolbar_search)
        toolbarAccount = findViewById(R.id.ytm_toolbar_account)
        toolbarBell.setColorFilter(Color.WHITE)
        toolbarHistory.setColorFilter(Color.WHITE)
        toolbarSearch.setColorFilter(Color.WHITE)
        toolbarAccount.setColorFilter(Color.WHITE)
        toolbarBell.setOnClickListener { service?.holder?.control("openNotifications") }
        toolbarHistory.setOnClickListener { service?.holder?.control("openHistory") }
        toolbarSearch.setOnClickListener { service?.holder?.navigate("https://music.youtube.com/search") }
        toolbarAccount.setOnClickListener { service?.holder?.control("openAccount") }
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, view.paddingBottom)
            view.post {
                nativeHeaderPx = view.height
                service?.holder?.let { syncNativeHeader(it) }
            }
            insets
        }
    }

    private fun syncNativeHeader(holder: YtMusicWebHolder) {
        val url = holder.currentUrl()
        val mode = toolbarModeForUrl(url)
        holder.setHeaderMode(mode, 0)
    }

    private fun toolbarModeForUrl(url: String): String = when {
        "/search" in url -> "search"
        "/library" in url -> "library"
        "/explore" in url || "/moods" in url -> "samples"
        else -> "home"
    }

    private fun updateToolbarForTab(url: String) {
        if (playerExpanded) return
        val mode = toolbarModeForUrl(url)
        when (mode) {
            "search" -> toolbar.visibility = View.GONE
            "library" -> {
                toolbar.visibility = View.VISIBLE
                toolbarLogoRow.visibility = View.GONE
                toolbarTitle.visibility = View.VISIBLE
                toolbarTitle.setText(R.string.filter_library)
                toolbarBell.visibility = View.GONE
                toolbarHistory.visibility = View.VISIBLE
                toolbarSearch.visibility = View.VISIBLE
                toolbarAccount.visibility = View.VISIBLE
            }
            "samples" -> {
                toolbar.visibility = View.VISIBLE
                toolbarLogoRow.visibility = View.VISIBLE
                toolbarTitle.visibility = View.GONE
                toolbarBell.visibility = View.GONE
                toolbarHistory.visibility = View.GONE
                toolbarSearch.visibility = View.GONE
                toolbarAccount.visibility = View.GONE
            }
            else -> {
                toolbar.visibility = View.VISIBLE
                toolbarLogoRow.visibility = View.VISIBLE
                toolbarTitle.visibility = View.GONE
                toolbarBell.visibility = View.VISIBLE
                toolbarHistory.visibility = View.GONE
                toolbarSearch.visibility = View.GONE
                toolbarAccount.visibility = View.VISIBLE
            }
        }
        service?.holder?.let { syncNativeHeader(it) }
    }

    private fun setPlayerExpanded(expanded: Boolean) {
        if (playerExpanded == expanded) return
        playerExpanded = expanded
        if (expanded) {
            toolbar.visibility = View.GONE
            miniPlayer.visibility = View.GONE
            bottomNav.visibility = View.GONE
            window.statusBarColor = Color.TRANSPARENT
        } else {
            bottomNav.visibility = View.VISIBLE
            window.statusBarColor = Color.TRANSPARENT
            updateToolbarForTab(service?.holder?.currentUrl().orEmpty().ifEmpty { YtMusicWebHolder.HOME_URL })
            service?.lastState?.let { renderMiniPlayer(it) }
        }
        service?.holder?.setHeaderMode(
            if (expanded) "search" else toolbarModeForUrl(service?.holder?.currentUrl().orEmpty()),
            0,
        )
    }

    private fun showPremium(show: Boolean) {
        showingPremium = show
        premiumPanel.visibility = if (show) View.VISIBLE else View.GONE
        if (show) highlightTab("upgrade")
    }

    private fun renderMiniPlayer(state: YtMusicWebHolder.PlaybackState) {
        val title = state.title.trim().ifEmpty { lastTitle }
        val artist = state.artist.trim().ifEmpty { lastArtist }
        val blank = title.isEmpty() ||
            title.equals("YT Music", true) ||
            title.equals("YouTube Music", true)
        if (state.playing || state.artwork.isNotBlank() || lastArtUrl.isNotBlank() || lastTitle.isNotEmpty()) {
            if (!playerExpanded) miniPlayer.visibility = View.VISIBLE
        } else if (blank && state.duration <= 0.0) {
            if (!playerExpanded) miniPlayer.visibility = View.GONE
            return
        }
        if (playerExpanded) return
        miniPlayer.visibility = View.VISIBLE
        if (title.isNotEmpty() && !blank) lastTitle = title
        if (artist.isNotEmpty()) lastArtist = artist
        miniTitle.text = when {
            !blank -> title
            state.playing -> lastTitle.ifBlank { getString(R.string.app_name) }
            else -> getString(R.string.app_name)
        }
        miniTitle.isSelected = true
        miniArtist.text = artist
        miniArtist.visibility = if (artist.isEmpty()) View.GONE else View.VISIBLE
        miniPlay.setImageResource(if (state.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
        miniPlay.contentDescription = getString(if (state.playing) R.string.pause else R.string.play)
        val max = state.duration
        miniProgress.progress = if (max > 0) ((state.position / max) * 1000).toInt().coerceIn(0, 1000) else 0
        loadArtwork(state.artwork.ifBlank { lastArtUrl })
    }

    private fun loadArtwork(url: String) {
        val src = normalizeArtworkUrl(url)
        if (src.isBlank() || src == lastArtUrl) return
        val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val headers = NetworkHeaders.Builder()
                        .set("Referer", "https://music.youtube.com/")
                    if (cookie.isNotBlank()) headers.set("Cookie", cookie)
                    val result = imageLoader.execute(
                        ImageRequest.Builder(this@YtMusicWebActivity)
                            .data(src)
                            .allowHardware(false)
                            .httpHeaders(headers.build())
                            .build(),
                    )
                    (result as? SuccessResult)?.image?.toBitmap()
                }.getOrNull()
            }
            if (bmp is Bitmap && bmp.width > 8 && bmp.height > 8) {
                lastArtUrl = src
                miniArt.setImageBitmap(bmp)
            }
        }
    }

    private fun normalizeArtworkUrl(url: String): String {
        var src = url.trim()
        if (src.startsWith("//")) src = "https:$src"
        if (src.startsWith("data:") || src.startsWith("blob:")) return ""
        return src
            .replace(Regex("=w\\d+-h\\d+"), "=w226-h226")
            .replace(Regex("=s\\d+"), "=s226")
    }

    private data class NavTab(
        val id: String,
        val url: String,
        val root: LinearLayout,
        val icon: ImageView,
        val label: TextView,
        val iconOff: Int,
        val iconOn: Int,
        val navigates: Boolean = true,
    )

    private fun bindBottomNav() {
        navItems = listOf(
            NavTab(
                "home",
                YtMusicWebHolder.HOME_URL,
                findViewById(R.id.ytm_nav_home),
                findViewById(R.id.ytm_nav_home_icon),
                findViewById(R.id.ytm_nav_home_label),
                R.drawable.home_outlined,
                R.drawable.home_filled,
            ),
            NavTab(
                "samples",
                "https://music.youtube.com/explore",
                findViewById(R.id.ytm_nav_samples),
                findViewById(R.id.ytm_nav_samples_icon),
                findViewById(R.id.ytm_nav_samples_label),
                R.drawable.samples_outlined,
                R.drawable.samples_filled,
            ),
            NavTab(
                "search",
                "https://music.youtube.com/search",
                findViewById(R.id.ytm_nav_search),
                findViewById(R.id.ytm_nav_search_icon),
                findViewById(R.id.ytm_nav_search_label),
                R.drawable.search,
                R.drawable.search,
            ),
            NavTab(
                "library",
                "https://music.youtube.com/library",
                findViewById(R.id.ytm_nav_library),
                findViewById(R.id.ytm_nav_library_icon),
                findViewById(R.id.ytm_nav_library_label),
                R.drawable.library_bookmark_outlined,
                R.drawable.library_bookmark_filled,
            ),
        )
        navItems.forEach { tab ->
            tab.root.setOnClickListener {
                showPremium(false)
                if (playerExpanded) service?.holder?.control("collapse")
                pageHasContent = false
                spinner?.visibility = View.VISIBLE
                retryPanel?.visibility = View.GONE
                spinner?.removeCallbacks(loadTimeout)
                spinner?.postDelayed(loadTimeout, 45_000)
                highlightTab(tab.url)
                updateToolbarForTab(tab.url)
                if (tab.id == "search") {
                    service?.holder?.control("openSearch")
                } else {
                    service?.holder?.navigate(tab.url)
                }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ytm_bottom_nav)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom)
            insets
        }
        highlightTab(YtMusicWebHolder.HOME_URL)
        updateToolbarForTab(YtMusicWebHolder.HOME_URL)
    }

    private fun highlightTab(url: String) {
        val selected = when {
            "/search" in url -> "search"
            "/library" in url -> "library"
            "/explore" in url || "/moods" in url -> "samples"
            else -> "home"
        }
        val active = Color.WHITE
        val inactive = Color.parseColor("#ABABAB")
        navItems.forEach { tab ->
            val on = tab.id == selected
            tab.icon.setImageResource(if (on) tab.iconOn else tab.iconOff)
            tab.icon.setColorFilter(if (on) active else inactive)
            tab.label.setTextColor(if (on) active else inactive)
        }
    }

    private fun applySystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.parseColor("#0F0F0F")
    }

    private fun requestUnrestrictedBattery() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName")),
            )
        } catch (_: Exception) {
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 71)
            }
        }
    }
}

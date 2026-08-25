package com.metrolist.music.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.web.YtMusicWebHolder
import timber.log.Timber

private const val EXPLORE_URL = "https://music.youtube.com/explore"

@Composable
fun SamplesWebViewScreen() {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    var progress by remember { mutableIntStateOf(0) }
    var hasContent by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var holder by remember { mutableStateOf<YtMusicWebHolder?>(null) }

    DisposableEffect(context) {
        val webHolder =
            YtMusicWebHolder(
                context = context,
                onState = {},
                embeddedMode = true,
            ).apply {
                onProgress = { progress = it }
                onHasContent = { if (it) hasContent = true }
                onLoadError = { loadError = true }
                onTrackSelected = { videoId ->
                    Timber.d("Samples: handoff play %s", videoId)
                    webView.post {
                        webView.evaluateJavascript(
                            "window.__ytmControl && window.__ytmControl('pause')",
                            null,
                        )
                    }
                    playerConnection?.playQueue(
                        YouTubeQueue(WatchEndpoint(videoId = videoId)),
                    )
                }
                setHeaderMode("samples", 0)
            }
        holder = webHolder
        webHolder.navigate(EXPLORE_URL)

        onDispose {
            webHolder.destroy()
            holder = null
        }
    }

    val showSpinner = !hasContent && !loadError && progress < 100

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                }
            },
            update = { container ->
                holder?.attach(container)
            },
        )

        if (showSpinner) {
            CircularProgressIndicator(
                color = Color(0xFFFF0033),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (loadError) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.ytm_web_retry_title),
                        color = Color.White,
                    )
                    TextButton(
                        onClick = {
                            loadError = false
                            hasContent = false
                            progress = 0
                            holder?.reload()
                        },
                    ) {
                        Text(stringResource(R.string.ytm_web_retry_action))
                    }
                }
            }
        }
    }
}

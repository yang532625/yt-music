package com.metrolist.music.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.metrolist.music.R

class YtMusicPlaybackService : Service() {

    inner class LocalBinder : Binder() {
        val service: YtMusicPlaybackService get() = this@YtMusicPlaybackService
    }

    private val binder = LocalBinder()
    private var mediaSession: MediaSessionCompat? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val keepAlive = object : Runnable {
        override fun run() {
            holder?.keepPlayingInBackground()
            handler.postDelayed(this, 4000)
        }
    }
    var holder: YtMusicWebHolder? = null
        private set

    @Volatile
    var lastState: YtMusicWebHolder.PlaybackState? = null
        private set

    var onUiState: ((YtMusicWebHolder.PlaybackState) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureChannel()
        mediaSession = MediaSessionCompat(this, "YtMusicWeb").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = holder?.control("play") ?: Unit
                override fun onPause() = holder?.control("pause") ?: Unit
                override fun onSkipToNext() = holder?.control("next") ?: Unit
                override fun onSkipToPrevious() = holder?.control("previous") ?: Unit
            })
            isActive = true
        }
        holder = YtMusicWebHolder(
            context = this,
            onState = { state ->
                lastState = state
                updateSession(state)
                updateWakeLock(state.playing)
                startInForeground(state)
                handler.post { onUiState?.invoke(state) }
            },
        ).also { it.loadHome() }
        startInForeground(null)
        handler.post(keepAlive)
        requestAudioFocus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> holder?.control("play")
            ACTION_PAUSE -> holder?.control("pause")
            ACTION_NEXT -> holder?.control("next")
            ACTION_PREVIOUS -> holder?.control("previous")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        handler.removeCallbacks(keepAlive)
        releaseAudioFocus()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        holder?.destroy()
        holder = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        holder?.keepPlayingInBackground()
        if (lastState?.playing != true) {
            stopSelf()
        }
    }

    private fun updateWakeLock(playing: Boolean) {
        if (playing) {
            if (wakeLock == null) {
                wakeLock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ytmusic:web")
                    .apply { setReferenceCounted(false) }
            }
            if (wakeLock?.isHeld != true) wakeLock?.acquire(4 * 60 * 60 * 1000L)
        } else if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun releaseAudioFocus() {
        val request = audioFocusRequest ?: return
        getSystemService(AudioManager::class.java).abandonAudioFocusRequest(request)
        audioFocusRequest = null
    }

    private fun updateSession(state: YtMusicWebHolder.PlaybackState) {
        val session = mediaSession ?: return
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (state.duration * 1000).toLong())
                .build(),
        )
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    if (state.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    (state.position * 1000).toLong(),
                    if (state.playing) 1f else 0f,
                )
                .build(),
        )
    }

    private fun startInForeground(state: YtMusicWebHolder.PlaybackState?) {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: YtMusicWebHolder.PlaybackState?): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, YtMusicWebActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val playing = state?.playing == true
        val playPauseAction = if (playing) ACTION_PAUSE else ACTION_PLAY
        val playPauseIcon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(state?.title?.ifBlank { getString(R.string.app_name) } ?: getString(R.string.app_name))
            .setContentText(state?.artist?.ifBlank { getString(R.string.ytm_web_playing) } ?: getString(R.string.ytm_web_playing))
            .setContentIntent(launch)
            .setOngoing(playing)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .addAction(android.R.drawable.ic_media_previous, "Prev", action(ACTION_PREVIOUS))
            .addAction(playPauseIcon, if (playing) "Pause" else "Play", action(playPauseAction))
            .addAction(android.R.drawable.ic_media_next, "Next", action(ACTION_NEXT))
            .build()
    }

    private fun action(name: String): PendingIntent {
        val intent = Intent(this, YtMusicPlaybackService::class.java).setAction(name)
        return PendingIntent.getService(
            this,
            name.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.ytm_web_playback_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val CHANNEL_ID = "ytm_web_playback"
        const val NOTIFICATION_ID = 71
        const val ACTION_PLAY = "com.metrolist.music.web.PLAY"
        const val ACTION_PAUSE = "com.metrolist.music.web.PAUSE"
        const val ACTION_NEXT = "com.metrolist.music.web.NEXT"
        const val ACTION_PREVIOUS = "com.metrolist.music.web.PREVIOUS"

        @Volatile
        var instance: YtMusicPlaybackService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, YtMusicPlaybackService::class.java)
            context.startForegroundService(intent)
        }
    }
}

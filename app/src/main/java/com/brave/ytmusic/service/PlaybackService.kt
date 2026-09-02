package com.brave.ytmusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.brave.ytmusic.R
import com.brave.ytmusic.bridge.PlaybackStateData
import com.brave.ytmusic.bridge.WebInterfaceBridge
import com.brave.ytmusic.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Android 14 (API 34) Foreground Media Playback Service.
 * Coordinates MediaSession, Samsung One UI lock-screen controls, Audio Focus, and WakeLock.
 */
class PlaybackService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private var audioFocusRequest: AudioFocusRequest? = null
    private var bridge: WebInterfaceBridge? = null
    private var stateObservationJob: Job? = null
    private var cachedArtworkBitmap: Bitmap? = null
    private var lastArtUrl: String = ""

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        initWakeLocks()
        initMediaSession()
        createNotificationChannel()
    }

    private fun initWakeLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BraveMusic:PlaybackWakeLock"
        ).apply {
            setReferenceCounted(false)
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        wifiLock = wifiManager?.createWifiLock(
            android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "BraveMusic:PlaybackWifiLock"
        )?.apply {
            setReferenceCounted(false)
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "BraveMusicSession").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    requestAudioFocus()
                    bridge?.play()
                }

                override fun onPause() {
                    bridge?.pause()
                }

                override fun onSkipToNext() {
                    bridge?.next()
                }

                override fun onSkipToPrevious() {
                    bridge?.previous()
                }

                override fun onSeekTo(pos: Long) {
                    bridge?.seekTo(pos / 1000)
                }

                override fun onStop() {
                    bridge?.pause()
                    abandonAudioFocus()
                    stopForegroundPlayback()
                }
            })
        }
    }

    fun setBridge(webBridge: WebInterfaceBridge) {
        this.bridge = webBridge
        stateObservationJob?.cancel()
        stateObservationJob = serviceScope.launch {
            webBridge.playbackState.collect { state ->
                handlePlaybackStateUpdate(state)
            }
        }
    }

    private fun handlePlaybackStateUpdate(state: PlaybackStateData) {
        if (state.title.isEmpty() && !state.isPlaying) {
            return
        }

        // Update MediaSession state
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(
                if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                state.positionSeconds * 1000,
                1.0f
            )
            .build()
        mediaSession.setPlaybackState(playbackState)

        // Asynchronously load artwork if URL changed
        if (state.artUrl != lastArtUrl && state.artUrl.isNotEmpty()) {
            lastArtUrl = state.artUrl
            serviceScope.launch {
                val bitmap = loadBitmapFromUrl(state.artUrl)
                cachedArtworkBitmap = bitmap
                updateMediaMetadata(state, bitmap)
                updateNotification(state, bitmap)
            }
        } else {
            updateMediaMetadata(state, cachedArtworkBitmap)
            updateNotification(state, cachedArtworkBitmap)
        }

        if (state.isPlaying) {
            acquireWakeLock()
            requestAudioFocus()
        } else {
            releaseWakeLock()
        }
    }

    private fun updateMediaMetadata(state: PlaybackStateData, art: Bitmap?) {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.title.ifEmpty { "YouTube Music" })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.artist.ifEmpty { "Streaming" })
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, state.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationSeconds * 1000)

        if (art != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
        }
        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun updateNotification(state: PlaybackStateData, art: Bitmap?) {
        val notification = buildNotification(state, art)
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundType
            )
        } catch (e: Exception) {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: PlaybackStateData, art: Bitmap?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getService(
            this, 2,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this, 3,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 4,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (state.isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(state.title.ifEmpty { getString(R.string.app_name) })
            .setContentText(state.artist.ifEmpty { "Playing" })
            .setSubText(state.album)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(art ?: BitmapFactory.decodeResource(resources, android.R.drawable.ic_media_play))
            .setContentIntent(contentIntent)
            .setDeleteIntent(stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(state.isPlaying)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.action_previous), prevIntent)
            .addAction(playPauseIcon, if (state.isPlaying) getString(R.string.action_pause) else getString(R.string.action_play), playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, getString(R.string.action_next), nextIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(stopIntent)
            )

        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> bridge?.togglePlay()
            ACTION_PREV -> bridge?.previous()
            ACTION_NEXT -> bridge?.next()
            ACTION_STOP -> {
                bridge?.pause()
                stopForegroundPlayback()
            }
        }
        return START_STICKY
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            audioFocusRequest = focusRequest
            return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            return audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                bridge?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                bridge?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                bridge?.setVolume(0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                bridge?.setVolume(1.0f)
                bridge?.play()
            }
        }
    }

    private fun acquireWakeLock() {
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(60 * 60 * 1000L) // 60 min safety timeout
            }
        }
        wifiLock?.let {
            if (!it.isHeld) {
                it.acquire()
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wifiLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    private fun stopForegroundPlayback() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private suspend fun loadBitmapFromUrl(urlString: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(urlString).openConnection()
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.getInputStream().use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseWakeLock()
        mediaSession.release()
        abandonAudioFocus()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "brave_music_playback"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY_PAUSE = "com.brave.ytmusic.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.brave.ytmusic.ACTION_PREV"
        const val ACTION_NEXT = "com.brave.ytmusic.ACTION_NEXT"
        const val ACTION_STOP = "com.brave.ytmusic.ACTION_STOP"
    }
}

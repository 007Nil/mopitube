package com.nil.mopitube

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PlaybackService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var notificationManager: NotificationManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var pollingJob: Job? = null
    private var observerJob: Job? = null
    private var updateJob: Job? = null

    private var lastTrackUri: String? = null
    private var lastPlaybackState: String? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val TAG = "PlaybackService"

        const val ACTION_PLAY = "com.nil.mopitube.PLAY"
        const val ACTION_PAUSE = "com.nil.mopitube.PAUSE"
        const val ACTION_NEXT = "com.nil.mopitube.NEXT"
        const val ACTION_PREVIOUS = "com.nil.mopitube.PREVIOUS"
        const val ACTION_STOP = "com.nil.mopitube.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MopiTube").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    scope.launch(Dispatchers.IO) { App.mopidyClient?.repo?.play() }
                    requestAudioFocus()
                }
                override fun onPause() {
                    scope.launch(Dispatchers.IO) { App.mopidyClient?.repo?.pause() }
                    abandonAudioFocus()
                }
                override fun onSkipToNext() {
                    scope.launch(Dispatchers.IO) { App.mopidyClient?.repo?.next() }
                }
                override fun onSkipToPrevious() {
                    scope.launch(Dispatchers.IO) { App.mopidyClient?.repo?.previous() }
                }
                override fun onStop() {
                    scope.launch(Dispatchers.IO) { App.mopidyClient?.repo?.pause() }
                    abandonAudioFocus()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            // Route hardware media buttons through MediaButtonReceiver
            setMediaButtonReceiver(
                PendingIntent.getBroadcast(
                    this@PlaybackService, 0,
                    Intent(this@PlaybackService, MediaButtonReceiver::class.java)
                        .apply { action = Intent.ACTION_MEDIA_BUTTON },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            isActive = true
        }
    }

    private fun requestAudioFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setWillPauseWhenDucked(false)
            .build()
        audioFocusRequest = req
        audioManager?.requestAudioFocus(req)
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    private fun buildNotification(
        trackName: String = "Loading...",
        artistName: String = "",
        albumName: String? = null,
        playbackState: String? = null,
        artwork: Bitmap? = null
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isPlaying = playbackState == "playing"

        fun actionIntent(action: String, requestCode: Int) = PendingIntent.getService(
            this, requestCode,
            Intent(this, PlaybackService::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Sync MediaSession state
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP
                )
                .setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1.0f
                )
                .build()
        )
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, trackName)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artistName)
                .apply {
                    albumName?.let { putString(MediaMetadataCompat.METADATA_KEY_ALBUM, it) }
                    artwork?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it) }
                }
                .build()
        )

        return NotificationCompat.Builder(this, App.PLAYBACK_CHANNEL_ID)
            .setContentTitle(trackName)
            .setContentText(artistName)
            .setSubText(albumName)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(artwork)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notify_prev, "Previous",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                )
            )
            .addAction(
                NotificationCompat.Action(
                    if (isPlaying) R.drawable.ic_notify_pause else R.drawable.ic_notify_play,
                    if (isPlaying) "Pause" else "Play",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        if (isPlaying) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY
                    )
                )
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notify_next, "Next",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
                )
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setDeleteIntent(actionIntent(ACTION_STOP, 3))
            .build()
    }

    private fun startObserving() {
        val repo = App.mopidyClient?.repo ?: run {
            Log.w(TAG, "repo is null, cannot observe")
            return
        }

        // --- StateFlow observers: react instantly to changes made by PlayerScreen or actions ---
        observerJob?.cancel()
        observerJob = scope.launch {
            launch {
                repo.currentTrack.collectLatest { track ->
                    val state = repo.playbackState.value
                    scheduleNotificationUpdate(track, state)
                }
            }
            launch {
                repo.playbackState.collectLatest { state ->
                    scheduleNotificationUpdate(repo.currentTrack.value, state)
                    // Manage audio focus based on playback state
                    if (state == "playing") requestAudioFocus() else abandonAudioFocus()
                }
            }
        }

        // --- Polling loop: keeps notification accurate when app is backgrounded ---
        // (PlayerScreen stops polling when the app is not in the foreground)
        pollingJob?.cancel()
        pollingJob = scope.launch {
            // Seed initial state without waiting for a StateFlow update
            val initialTrack = withContext(Dispatchers.IO) { repo.getCurrentTrack() }
            val initialState = withContext(Dispatchers.IO) { repo.getPlaybackState() }
            withContext(Dispatchers.Main) {
                repo.updatePlaybackState() // push into StateFlows so observers fire
            }
            // If StateFlows were empty (app just started), update directly
            if (repo.currentTrack.value == null) {
                scheduleNotificationUpdate(initialTrack, initialState)
            }

            while (isActive) {
                delay(2000) // Check every 2s — StateFlows handle the fast path
                try {
                    val track = withContext(Dispatchers.IO) { repo.getCurrentTrack() }
                    val state = withContext(Dispatchers.IO) { repo.getPlaybackState() }
                    val trackUri = track?.get("uri")?.jsonPrimitive?.contentOrNull
                    if (trackUri != lastTrackUri || state != lastPlaybackState) {
                        // Push into StateFlows so observers fire (and audio focus is managed)
                        withContext(Dispatchers.Main) { repo.updatePlaybackState() }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Poll error", e)
                }
            }
        }
    }

    private fun scheduleNotificationUpdate(track: JsonObject?, playbackState: String?) {
        val trackUri = track?.get("uri")?.jsonPrimitive?.contentOrNull
        // Deduplicate: skip if nothing changed
        if (trackUri == lastTrackUri && playbackState == lastPlaybackState) return
        lastTrackUri = trackUri
        lastPlaybackState = playbackState

        updateJob?.cancel()
        updateJob = scope.launch {
            val trackName = track?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Track"
            val artistName = track?.get("artists")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
            val albumName = track?.get("album")?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            val artwork = track?.let { loadArtwork(it) }
            val notification = buildNotification(trackName, artistName, albumName, playbackState, artwork)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun loadArtwork(track: JsonObject): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val url = App.mopidyClient?.repo?.findArtwork(track) ?: return@withContext null
            val loader = ImageLoader(this@PlaybackService)
            val result = loader.execute(
                ImageRequest.Builder(this@PlaybackService)
                    .data(url)
                    .allowHardware(false)
                    .build()
            )
            (result as? SuccessResult)?.drawable?.let { d ->
                val bmp = Bitmap.createBitmap(
                    d.intrinsicWidth.coerceAtLeast(1),
                    d.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                d.setBounds(0, 0, canvas.width, canvas.height)
                d.draw(canvas)
                bmp
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load artwork", e)
            null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")

        // Always call startForeground() first — required within 5s of start
        startForeground(NOTIFICATION_ID, buildNotification())

        when (intent?.action) {
            null -> startObserving()
            ACTION_PLAY -> scope.launch(Dispatchers.IO) {
                App.mopidyClient?.repo?.play()
                requestAudioFocus()
            }
            ACTION_PAUSE -> scope.launch(Dispatchers.IO) {
                App.mopidyClient?.repo?.pause()
                abandonAudioFocus()
            }
            ACTION_NEXT -> scope.launch(Dispatchers.IO) { App.mopidyClient?.repo?.next() }
            ACTION_PREVIOUS -> scope.launch(Dispatchers.IO) { App.mopidyClient?.repo?.previous() }
            ACTION_STOP -> {
                scope.launch(Dispatchers.IO) { App.mopidyClient?.repo?.pause() }
                abandonAudioFocus()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        abandonAudioFocus()
        mediaSession?.release()
        scope.cancel()
    }
}

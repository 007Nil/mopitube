package com.nil.mopitube

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
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
        Log.d(TAG, "Service created")
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun buildBasicNotification(
        trackName: String = "Loading...",
        artistName: String = "",
        albumName: String? = null,
        playbackState: String? = null,
        artwork: Bitmap? = null
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isPlaying = playbackState == "playing"

        val playPauseIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, PlaybackService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val previousIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        return NotificationCompat.Builder(this, App.PLAYBACK_CHANNEL_ID)
            .setContentTitle(trackName)
            .setContentText(artistName)
            .setSubText(albumName)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(artwork)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_media_previous, "Previous", previousIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setDeleteIntent(stopIntent)
            .build()
    }

    private fun startObservingPlayback() {
        scope.launch {
            val repo = App.mopidyClient?.repo
            if (repo == null) {
                Log.d(TAG, "repo is null, cannot observe playback")
                return@launch
            }

            Log.d(TAG, "Starting playback observation")

            // Fetch initial state immediately
            val track = withContext(Dispatchers.IO) { repo.getCurrentTrack() }
            val state = withContext(Dispatchers.IO) { repo.getPlaybackState() }

            Log.d(TAG, "Initial state: track=${track?.get("name")}, state=$state")

            // Update StateFlows
            withContext(Dispatchers.IO) { repo.updatePlaybackState() }

            // Update notification with real data
            updateNotificationAsync(track, state)

            // Observe ongoing changes
            launch {
                repo.currentTrack.collectLatest { t ->
                    Log.d(TAG, "Track changed: ${t?.get("name")}")
                    updateNotificationAsync(t, repo.playbackState.value)
                }
            }

            launch {
                repo.playbackState.collectLatest { s ->
                    Log.d(TAG, "State changed: $s")
                    updateNotificationAsync(repo.currentTrack.value, s)
                }
            }
        }
    }

    private fun updateNotificationAsync(track: JsonObject?, playbackState: String?) {
        scope.launch {
            val trackName = track?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Track"
            val artistName = track?.get("artists")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
            val albumName = track?.get("album")?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull

            val artwork = track?.let { loadArtwork(it) }

            val notification = buildBasicNotification(trackName, artistName, albumName, playbackState, artwork)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun loadArtwork(track: JsonObject): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val artworkUrl = App.mopidyClient?.repo?.findArtwork(track)
                if (artworkUrl != null) {
                    val imageLoader = ImageLoader(this@PlaybackService)
                    val request = ImageRequest.Builder(this@PlaybackService)
                        .data(artworkUrl)
                        .allowHardware(false)
                        .build()
                    val result = imageLoader.execute(request)
                    (result as? SuccessResult)?.drawable?.let { drawable ->
                        val bitmap = Bitmap.createBitmap(
                            drawable.intrinsicWidth,
                            drawable.intrinsicHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(bitmap)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bitmap
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load artwork", e)
                null
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")

        // CRITICAL: Call startForeground() immediately
        val notification = buildBasicNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "startForeground called")

        when (intent?.action) {
            null -> {
                startObservingPlayback()
            }
            ACTION_PLAY -> scope.launch(Dispatchers.IO) { App.mopidyClient?.repo?.play() }
            ACTION_PAUSE -> scope.launch(Dispatchers.IO) { App.mopidyClient?.repo?.pause() }
            ACTION_NEXT -> scope.launch(Dispatchers.IO) { App.mopidyClient?.repo?.next() }
            ACTION_PREVIOUS -> scope.launch(Dispatchers.IO) { App.mopidyClient?.repo?.previous() }
            ACTION_STOP -> {
                scope.launch(Dispatchers.IO) { App.mopidyClient?.repo?.pause() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        scope.cancel()
    }
}

package com.nil.mopitube

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.nil.mopitube.mopidy.MopidyClient

class App : Application(), ImageLoaderFactory {
    companion object {
        // New channel ID — forces fresh creation, bypassing any stale OEM-muted channel
        const val PLAYBACK_CHANNEL_ID = "playback_media_v2"
        private const val LEGACY_CHANNEL_ID = "playback_channel"

        @Volatile
        var mopidyClient: MopidyClient? = null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // Remove stale channel that ColorOS marked as unimportant
            nm.deleteNotificationChannel(LEGACY_CHANNEL_ID)

            val channel = NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows currently playing track"
                setShowBadge(false)
                setSound(null, null) // Explicitly no sound
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                    .build()
            }
            .crossfade(true)
            .build()
    }
}

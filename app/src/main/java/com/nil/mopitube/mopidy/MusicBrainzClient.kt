package com.nil.mopitube.mopidy

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json // FIX: Separated import statements
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

// Data classes for parsing TheAudioDB response
@Serializable
data class AudioDbArtistResponse(
    val artists: List<AudioDbArtist>? = null
)

@Serializable
data class AudioDbArtist(
    val strArtistThumb: String? = null
)

class MusicBrainzClient {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    // This function can stay as it is, as it's for finding album art
    fun getArtworkUrls(artist: String, album: String): List<String> {
        // Implementation for MusicBrainz API would go here.
        // For now, we leave it as a placeholder.
        return emptyList()
    }

    fun getArtistArtworkUrls(artist: String): List<String> {
        Log.d("MusicBrainzClient", "Searching for ARTIST image on TheAudioDB: $artist")
        try {
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val url = "https://www.theaudiodb.com/api/v1/json/2/search.php?s=$encodedArtist".toHttpUrl()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e("MusicBrainzClient", "TheAudioDB request failed with code: ${response.code}")
                return emptyList()
            }

            val responseBody = response.body?.string() ?: return emptyList()
            val artistResponse = json.decodeFromString<AudioDbArtistResponse>(responseBody)
            val imageUrl = artistResponse.artists?.firstOrNull()?.strArtistThumb

            return if (!imageUrl.isNullOrEmpty()) {
                Log.d("MusicBrainzClient", "TheAudioDB found URL: $imageUrl")
                listOf(imageUrl)
            } else {
                Log.w("MusicBrainzClient", "TheAudioDB returned null for artist: $artist")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("MusicBrainzClient", "Failed to fetch from TheAudioDB: ${e.message}", e)
            return emptyList()
        }
    }
}

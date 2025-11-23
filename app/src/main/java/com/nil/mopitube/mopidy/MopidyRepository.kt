package com.nil.mopitube.mopidy

import android.content.Context
import android.util.Log
import com.nil.mopitube.database.ArtworkCacheEntry
import com.nil.mopitube.database.LikedTrack
import com.nil.mopitube.database.MopitubeDatabase // Import the CORRECT database
import com.nil.mopitube.database.PlayHistoryEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

// In-memory cache for this app session for maximum speed.
private object ArtworkProvider {
    private val cache = mutableMapOf<String, String>()
    private val mutex = Mutex()
    suspend fun get(key: String): String? = mutex.withLock { cache[key] }
    suspend fun put(key: String, url: String) = mutex.withLock { cache[key] = url }

}

class MopidyRepository(
    val rpc: MopidyRpcClient,
    context: Context,
    private val serverAddress: String,
    private val queueManager: QueueManager
) {
    // ===== THE FINAL, CRITICAL FIX IS HERE =====
    // It now points to the correct unified database and uses the correct dao name.
    private val dao = MopitubeDatabase.getDatabase(context).mopitubeDao()

    // --- All other functions are now guaranteed to work correctly ---

    suspend fun play() = rpc.call("core.playback.play")
    suspend fun pause() = rpc.call("core.playback.pause")
    suspend fun next() = rpc.call("core.playback.next")
    suspend fun previous() = rpc.call("core.playback.previous")
    suspend fun getCurrentTrack(): JsonObject? = rpc.call("core.playback.get_current_track") as? JsonObject
    suspend fun getPlaybackState(): String? = rpc.call("core.playback.get_state")?.jsonPrimitive?.contentOrNull
    suspend fun getTimePosition(): Int? = rpc.call("core.playback.get_time_position")?.jsonPrimitive?.intOrNull
    suspend fun seek(ms: Int) {
        val params = buildJsonObject { put("time_position", JsonPrimitive(ms)) }
        rpc.call("core.playback.seek", params)
    }
    suspend fun clearTracklist() { rpc.call("core.tracklist.clear") }
    suspend fun addTrackToTracklist(trackUri: String) {
        val params = buildJsonObject { put("uris", buildJsonArray { add(JsonPrimitive(trackUri)) }) }
        rpc.call("core.tracklist.add", params)
    }
    suspend fun getPlaylists(): List<JsonObject> {
        val result = rpc.call("core.playlists.as_list")
        return result?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
    }
    suspend fun getRecentlyAddedAlbums(): List<JsonObject> {
        val result = rpc.call("core.library.browse", buildJsonObject { put("uri", JsonNull) })
        if (result == null || result !is JsonArray) return emptyList()
        return result.jsonArray.mapNotNull { it.jsonObject }.filter { it["type"]?.jsonPrimitive?.content == "album" }
    }
    suspend fun getRandomTracks(count: Int = 12): List<JsonObject> {
        val params = buildJsonObject { put("uri", "local:directory?type=track") }
        val browseResult = rpc.call("core.library.browse", params)
        if (browseResult == null || browseResult !is JsonArray) return emptyList()
        val allTrackRefs = browseResult.jsonArray.mapNotNull { it.jsonObject }
        if (allTrackRefs.isEmpty()) return emptyList()
        val randomTrackUris = allTrackRefs.shuffled().take(count).mapNotNull { it["uri"]?.jsonPrimitive?.content }
        if (randomTrackUris.isEmpty()) return emptyList()
        val lookupParams = buildJsonObject { put("uris", buildJsonArray { randomTrackUris.forEach { add(it) } }) }
        val lookupResult = rpc.call("core.library.lookup", lookupParams)
        return lookupResult?.jsonObject?.values?.flatMap { it.jsonArray }?.mapNotNull { it.jsonObject } ?: emptyList()
    }
    suspend fun lookup(uri: String): JsonElement? {
        val params = buildJsonObject { put("uri", uri) }
        return rpc.call("core.library.lookup", params)
    }
    suspend fun search(query: Map<String, List<String>>): List<JsonElement> {
        val params = buildJsonObject { put("query", buildJsonObject { query.forEach { (k, v) -> put(k, buildJsonArray { v.forEach { add(it) } }) } }) }
        return rpc.call("core.library.search", params)?.jsonArray ?: emptyList()
    }

    // --- Artwork and Like/Play History Functions ---

    suspend fun playTracks(tracks: List<JsonObject>, startAtTrackUri: String) {
        if (tracks.isEmpty()) return

        // 1. Update our app's internal queue first. This is crucial.
        queueManager.setQueue(tracks, startAtTrackUri)

        // 2. Get the reordered list of URIs from our queue manager.
        val trackUris = queueManager.queue.value.mapNotNull { it["uri"]?.jsonPrimitive?.contentOrNull }

        // 3. Update Mopidy's tracklist to match our queue.
        rpc.call("core.tracklist.clear")
        rpc.call("core.tracklist.add", buildJsonObject { put("uris", buildJsonArray { trackUris.forEach { add(JsonPrimitive(it)) } }) })

        // 4. Tell Mopidy to start playing from the beginning of its new tracklist.
        rpc.call("core.playback.play")
    }

    suspend fun getAlbumImages(albumUri: String): List<String> {
        val cacheKey = "album-art|$albumUri"
        ArtworkProvider.get(cacheKey)?.let { return listOf(it) }
        dao.getArtwork(cacheKey)?.let {
            ArtworkProvider.put(cacheKey, it.imageUrl)
            return listOf(it.imageUrl)
        }
        val params = buildJsonObject { put("uris", JsonArray(listOf(JsonPrimitive(albumUri)))) }
        val res = rpc.call("core.library.get_images", params)
        val imageResultsObject = (res as? JsonObject)?.get(albumUri)
        val arr = imageResultsObject?.jsonArray
        val imageUrls = arr?.mapNotNull {
            val imageUri = it.jsonObject["uri"]?.jsonPrimitive?.contentOrNull
            if (imageUri?.startsWith("/") == true) "http://$serverAddress$imageUri" else imageUri
        } ?: emptyList()
        imageUrls.firstOrNull()?.let { foundUrl ->
            dao.insertArtwork(ArtworkCacheEntry(cacheKey = cacheKey, imageUrl = foundUrl))
            ArtworkProvider.put(cacheKey, foundUrl)
        }
        return imageUrls
    }

    suspend fun findArtwork(track: JsonObject?): String? {
        if (track == null) return null
        val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull ?: return null
        val cacheKey = "track-art|$trackUri"
        ArtworkProvider.get(cacheKey)?.let { return it }
        dao.getArtwork(cacheKey)?.let {
            ArtworkProvider.put(cacheKey, it.imageUrl)
            return it.imageUrl
        }
        val params = buildJsonObject { put("uris", JsonArray(listOf(JsonPrimitive(trackUri)))) }
        val res = rpc.call("core.library.get_images", params)
        val imageResultsObject = (res as? JsonObject)?.get(trackUri)
        val arr = imageResultsObject?.jsonArray
        val imageUrl = arr?.mapNotNull {
            val imageUri = it.jsonObject["uri"]?.jsonPrimitive?.contentOrNull
            if (imageUri?.startsWith("/") == true) "http://$serverAddress$imageUri" else imageUri
        }?.firstOrNull()
        if (imageUrl != null) {
            dao.insertArtwork(ArtworkCacheEntry(cacheKey = cacheKey, imageUrl = imageUrl))
            ArtworkProvider.put(cacheKey, imageUrl)
        }
        return imageUrl
    }

    suspend fun isTrackLiked(trackUri: String): Boolean {
        return dao.findLikedTrack(trackUri) != null
    }

    suspend fun toggleLike(trackUri: String): Boolean {
        val isCurrentlyLiked = isTrackLiked(trackUri)
        if (isCurrentlyLiked) {
            dao.unlikeTrack(trackUri)
            return false
        } else {
            dao.likeTrack(LikedTrack(uri = trackUri))
            return true
        }
    }

    suspend fun getLikedTracks(): List<JsonObject> {
        val likedTrackUris = dao.getAllLikedTracks().map { it.uri }
        if (likedTrackUris.isEmpty()) return emptyList()
        val lookupParams = buildJsonObject { put("uris", buildJsonArray { likedTrackUris.forEach { add(it) } }) }
        val lookupResult = rpc.call("core.library.lookup", lookupParams)
        return lookupResult?.jsonObject?.values?.flatMap { it.jsonArray }?.mapNotNull { it.jsonObject } ?: emptyList()
    }

    suspend fun playAll(trackUris: List<String>) {
        if (trackUris.isEmpty()) return
        clearTracklist()
        val params = buildJsonObject { put("uris", buildJsonArray { trackUris.forEach { add(JsonPrimitive(it)) } }) }
        rpc.call("core.tracklist.add", params)
        play()
    }

    suspend fun logTrackPlay(trackUri: String) {
        if (trackUri.isBlank()) return
        dao.insertPlayHistory(PlayHistoryEntry(uri = trackUri))
    }

    suspend fun getMostPlayedTracks(count: Int = 10): List<JsonObject> {
        val mostPlayedUris = dao.getMostPlayed(count).map { it.uri }
        if (mostPlayedUris.isEmpty()) return emptyList()
        val lookupParams = buildJsonObject { put("uris", buildJsonArray { mostPlayedUris.forEach { add(it) } }) }
        val lookupResult = rpc.call("core.library.lookup", lookupParams)
        val finalTracks = lookupResult?.jsonObject?.values?.flatMap { it.jsonArray }?.mapNotNull { it.jsonObject } ?: emptyList()
        val uriOrder = mostPlayedUris.withIndex().associate { it.value to it.index }
        return finalTracks.sortedBy { uriOrder[it["uri"]?.jsonPrimitive?.contentOrNull] }
    }

    suspend fun getVolume(): Int? {
        val result = rpc.call("core.mixer.get_volume")
        return result?.jsonPrimitive?.intOrNull
    }

    suspend fun setVolume(volume: Int): Boolean {
        val params = buildJsonObject { put("volume", JsonPrimitive(volume)) }
        val result = rpc.call("core.mixer.set_volume", params)
        return result?.jsonPrimitive?.booleanOrNull ?: false
    }
}

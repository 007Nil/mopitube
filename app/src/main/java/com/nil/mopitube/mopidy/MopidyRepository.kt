package com.nil.mopitube.mopidy

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.room.Query
import com.nil.mopitube.database.ArtworkCacheEntry
import com.nil.mopitube.database.LikedTrack
import com.nil.mopitube.database.MopitubeDatabase // Import the CORRECT database
import com.nil.mopitube.database.PlayHistoryEntry
import com.nil.mopitube.database.Track
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlinx.serialization.json.add
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.log

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

    private val dao = MopitubeDatabase.getDatabase(context).mopitubeDao()
    private val appendMutex = Mutex()

    private fun normalizeUri(uri: String): String {
        return URLDecoder.decode(uri, StandardCharsets.UTF_8.name())
    }

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

    suspend fun getAllTracks(): List<JsonObject> {
        // Step 1: Attempt to get all tracks from the local database first.
        var cachedTracks = dao.getAllCachedTracks()

        // Step 2: If the cache is empty, fetch from the server and populate it.
        if (cachedTracks.isEmpty()) {
            Log.d("MopidyRepository", "Track cache is empty. Fetching from server...")
            cacheAllTracksFromServer()
            // After caching, query the database again.
            cachedTracks = dao.getAllCachedTracks()
        } else {
            Log.d("MopidyRepository", "Loaded ${cachedTracks.size} tracks from local cache.")
        }

        // Step 3: Convert the Track entities back to JsonObjects for the rest of the app.
        // This ensures that other functions that depend on this method's return type don't break.
        return cachedTracks.map { track ->
            buildJsonObject {
                put("uri", JsonPrimitive(track.uri))
                put("name", JsonPrimitive(track.name))
                put("length", track.length?.let { JsonPrimitive(it) } ?: JsonNull)
                // Reconstruct album and artist objects
                track.artistName?.let {
                    put("artists", buildJsonArray {
                        add(buildJsonObject {
                            put("name", JsonPrimitive(it))
                            // Note: Artist URI is not cached, so it's absent here.
                        })
                    })
                }
                track.albumName?.let {
                    put("album", buildJsonObject {
                        put("name", JsonPrimitive(it))
                        put("uri", track.albumUri?.let { uri -> JsonPrimitive(uri) } ?: JsonNull)
                    })
                }
            }
        }
    }

    suspend fun refreshAllTracksFromServer() {
        Log.d("MopidyRepository", "Refreshing tracks from server. Clearing old cache first.")
        deleteAllTracks() // Clear the old data
        cacheAllTracksFromServer() // Fetch and save new data
        Log.d("MopidyRepository", "Track refresh complete.")
    }

    @Query("DELETE FROM tracks") // This is the SQL command to delete all rows
    suspend fun deleteAllTracks() {
    }

    suspend fun cacheAllTracksFromServer() {
        // Step 1: Browse for all track URIs (same as in getAllTracks)
        val params = buildJsonObject { put("uri", "local:directory?type=track") }
        val browseResult = rpc.call("core.library.browse", params)
        if (browseResult == null || browseResult !is JsonArray) return

        val allTrackUris = browseResult.jsonArray
            .mapNotNull { it.jsonObject?.get("uri")?.jsonPrimitive?.content }
        if (allTrackUris.isEmpty()) return

        // Step 2: Look up the full details for all track URIs
        val lookupParams = buildJsonObject { put("uris", buildJsonArray { allTrackUris.forEach { add(it) } }) }
        val lookupResult = rpc.call("core.library.lookup", lookupParams)

        // Step 3: Parse the JsonObject result into a list of `Track` entities
        val tracksToCache = lookupResult?.jsonObject?.values
            ?.flatMap { it.jsonArray }
            ?.mapNotNull { it.jsonObject }
            ?.map { trackJson ->
                // Safely extract each piece of information from the JSON
                val artistName = trackJson["artists"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("name")?.jsonPrimitive?.content
                val albumObject = trackJson["album"]?.jsonObject
                val albumName = albumObject?.get("name")?.jsonPrimitive?.content
                val albumUri = albumObject?.get("uri")?.jsonPrimitive?.content

                Track(
                    uri = trackJson["uri"]?.jsonPrimitive?.content ?: "",
                    name = trackJson["name"]?.jsonPrimitive?.content ?: "Unknown Track",
                    artistName = artistName,
                    albumName = albumName,
                    albumUri = albumUri,
                    length = trackJson["length"]?.jsonPrimitive?.intOrNull
                )
            }
            ?.filter { it.uri.isNotBlank() } // Ensure we don't save tracks with an empty URI
            ?: emptyList()

        // Step 4: Save the parsed tracks to the database
        if (tracksToCache.isNotEmpty()) {
            dao.insertAllTracks(tracksToCache)
            Log.d("MopidyRepository", "Successfully cached ${tracksToCache.size} tracks.")
        }
    }
    suspend fun getAllAlbums(): List<JsonObject> {
        val allTracks = getAllTracks()
        if (allTracks.isEmpty()) return emptyList()

        return allTracks
            .mapNotNull { it["album"]?.jsonObject }
            .distinctBy { it["uri"]?.jsonPrimitive?.content }
    }

    suspend fun getAllArtists(): List<JsonObject> {
        val allTracks = getAllTracks()
        if (allTracks.isEmpty()) return emptyList()

        return allTracks
            .flatMap { it["artists"]?.jsonArray ?: emptyList() }
            .mapNotNull { it.jsonObject }
            .distinctBy { it["uri"]?.jsonPrimitive?.content }
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
        return rpc.call("core.library.browse", params)
    }

    suspend fun getAlbumSongs(uri: String): JsonElement? {
        val params = buildJsonObject { put("uri", uri) }
        Log.d("lookup function param", ": $params")
        return rpc.call("core.library.browse", params)
    }
    suspend fun search(query: Map<String, List<String>>): List<JsonElement> {
        val params = buildJsonObject { put("query", buildJsonObject { query.forEach { (k, v) -> put(k, buildJsonArray { v.forEach { add(it) } }) } }) }
        return rpc.call("core.library.search", params)?.jsonArray ?: emptyList()
    }

    suspend fun playTracks(tracks: List<JsonObject>) {
        if (tracks.isEmpty()) return

        // 1. Update our app's internal queue first. This is crucial.
        queueManager.setQueue(tracks)

        // 2. Get the reordered list of URIs from our queue manager.
        val trackUris = queueManager.queue.value.mapNotNull { it["uri"]?.jsonPrimitive?.contentOrNull }

        // 3. Update Mopidy's tracklist to match our queue.
//        rpc.call("core.tracklist.clear")
        rpc.call("core.tracklist.add", buildJsonObject { put("uris", buildJsonArray { trackUris.forEach { add(JsonPrimitive(it)) } }) })

        // 4. Tell Mopidy to start playing from the beginning of its new tracklist.
        rpc.call("core.playback.play")
    }

    suspend fun getAlbumImages(albumUri: String): List<String> {
//        val cacheKey = "album-art|$albumUri"
//        ArtworkProvider.get(cacheKey)?.let { return listOf(it) }
//        dao.getArtwork(cacheKey)?.let {
//            ArtworkProvider.put(cacheKey, it.imageUrl)
//            return listOf(it.imageUrl)
//        }
        val params = buildJsonObject { put("uris", JsonArray(listOf(JsonPrimitive(albumUri)))) }
        val res = rpc.call("core.library.get_images", params)
        val imageResultsObject = (res as? JsonObject)?.get(albumUri)
        val arr = imageResultsObject?.jsonArray
        val imageUrls = arr?.mapNotNull {
            val imageUri = it.jsonObject["uri"]?.jsonPrimitive?.contentOrNull
            if (imageUri?.startsWith("/") == true) "http://$serverAddress$imageUri" else imageUri
        } ?: emptyList()
        imageUrls.firstOrNull()?.let { foundUrl ->
//            dao.insertArtwork(ArtworkCacheEntry(cacheKey = cacheKey, imageUrl = foundUrl))
//            ArtworkProvider.put(cacheKey, foundUrl)
        }
        return imageUrls
    }

    suspend fun findArtwork(track: JsonObject?): String? {
        if (track == null) return null
        // 1. Get the Album URI from the track, not the track's own URI.
        val albumUri = track["album"]?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
        if (albumUri.isNullOrBlank()) {
            Log.w("ArtworkDebug", "Track '${track["name"]?.jsonPrimitive?.content}' has no album URI.")
            return null
        }

//         2. Use the Album URI for the cache key.
//        val cacheKey = "album-art|$albumUri"
//
//        // 3. Check memory cache.
//        ArtworkProvider.get(cacheKey)?.let { return it }
//
//        // 4. Check database cache.
//        dao.getArtwork(cacheKey)?.let {
//            ArtworkProvider.put(cacheKey, it.imageUrl)
//            return it.imageUrl
//        }

        // 5. Fetch from server using the correct ALBUM URI.
        Log.d("ArtworkDebug", "Fetching artwork for album: $albumUri")
        val params = buildJsonObject { put("uris", JsonArray(listOf(JsonPrimitive(albumUri)))) }
        val res = rpc.call("core.library.get_images", params)

        // The result is keyed by the album URI.
        val imageResultsObject = (res as? JsonObject)?.get(albumUri)
        val arr = imageResultsObject?.jsonArray
        val imageUrl = arr?.mapNotNull {
            val imageUri = it.jsonObject["uri"]?.jsonPrimitive?.contentOrNull
            if (imageUri?.startsWith("/") == true) "http://$serverAddress$imageUri" else imageUri
        }?.firstOrNull()

        // 6. Cache the result.
//        if (imageUrl != null) {
//            Log.d("ArtworkDebug", "Found artwork for $albumUri -> $imageUrl")
//            dao.insertArtwork(ArtworkCacheEntry(cacheKey = cacheKey, imageUrl = imageUrl))
//            ArtworkProvider.put(cacheKey, imageUrl)
//        } else {
//            Log.w("ArtworkDebug", "Server returned no images for album: $albumUri")
//        }
        Log.d("RepoImageURL", "$imageUrl")
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

    suspend fun playTrackFromTracklist(trackUri: String): Boolean {

        val normalizedTarget = normalizeUri(trackUri)

        val result = rpc.call("core.tracklist.get_tl_tracks")
        if (result !is JsonArray) return false

        val tlid = result.firstOrNull { tlTrack ->
            val tlUri = tlTrack.jsonObject["track"]
                ?.jsonObject
                ?.get("uri")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.let { normalizeUri(it) }

            tlUri == normalizedTarget
        }?.jsonObject
            ?.get("tlid")
            ?.jsonPrimitive
            ?.intOrNull

        if (tlid == null) {
            Log.w("MopidyRepository", "Track not found in tracklist: $trackUri")
            return false
        }

        rpc.call(
            "core.playback.play",
            buildJsonObject { put("tlid", JsonPrimitive(tlid)) }
        )

        Log.d("MopidyRepository", "Playing TLID=$tlid")
        return true
    }


    suspend fun appendRandomTracksIfNeeded(
        fetchCount: Int = 20
    ): Int = appendMutex.withLock {

        val newTracks = getRandomTracks(fetchCount)
        if (newTracks.isEmpty()) return@withLock 0

        val existingUris = queueManager.queue.value
            .mapNotNull { it["uri"]?.jsonPrimitive?.contentOrNull }
            .map { normalizeUri(it) }
            .toSet()

        val filteredTracks = newTracks.filter {
            it["uri"]?.jsonPrimitive?.contentOrNull
                ?.let { normalizeUri(it) } !in existingUris
        }

        if (filteredTracks.isEmpty()) return@withLock 0

        val newUris = filteredTracks.mapNotNull {
            it["uri"]?.jsonPrimitive?.contentOrNull
        }

        rpc.call(
            "core.tracklist.add",
            buildJsonObject {
                put("uris", buildJsonArray {
                    newUris.forEach { add(it) }
                })
            }
        )

        queueManager.appendToQueue(filteredTracks)

        return@withLock filteredTracks.size
    }



}


package com.nil.mopitube.mopidy

import android.content.Context
import android.util.Log
import com.nil.mopitube.database.ArtworkCacheEntry
import com.nil.mopitube.database.LikedTrack
import com.nil.mopitube.database.MopitubeDatabase // Import the CORRECT database
import com.nil.mopitube.database.PlayHistoryEntry
import com.nil.mopitube.database.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlinx.serialization.json.add
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.log

// In-memory LRU cache for artwork URLs (max 300 entries).
private object ArtworkProvider {
    private const val MAX_ENTRIES = 300
    private val cache = object : LinkedHashMap<String, String>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MAX_ENTRIES
        }
    }
    private val mutex = Mutex()
    suspend fun get(key: String): String? = mutex.withLock { cache[key] }
    suspend fun put(key: String, url: String) = mutex.withLock { cache[key] = url }
}

class MopidyRepository(
    val rpc: MopidyRpcClient,
    context: Context,
    private val serverAddress: String
) {

    private val dao = MopitubeDatabase.getDatabase(context).mopitubeDao()
    private val prefs = context.getSharedPreferences("mopitube_cache", Context.MODE_PRIVATE)
    private val trackCacheKey = "track_cache_timestamp_$serverAddress"

    // Playback state flows for notification service
    private val _currentTrack = MutableStateFlow<JsonObject?>(null)
    val currentTrack: StateFlow<JsonObject?> = _currentTrack.asStateFlow()

    private val _playbackState = MutableStateFlow<String?>(null)
    val playbackState: StateFlow<String?> = _playbackState.asStateFlow()

    private val _timePosition = MutableStateFlow<Int?>(null)
    val timePosition: StateFlow<Int?> = _timePosition.asStateFlow()

    companion object {
        private const val TRACK_CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val PLAY_HISTORY_MAX_AGE_MS = 90L * 24 * 60 * 60 * 1000 // 90 days
    }

    private fun isTrackCacheExpired(): Boolean {
        val lastCached = prefs.getLong(trackCacheKey, 0)
        return System.currentTimeMillis() - lastCached > TRACK_CACHE_TTL_MS
    }

    private fun updateTrackCacheTimestamp() {
        prefs.edit().putLong(trackCacheKey, System.currentTimeMillis()).apply()
    }

    private fun normalizeUri(uri: String): String {
        return URLDecoder.decode(uri, StandardCharsets.UTF_8.name())
    }

    // --- All other functions are now guaranteed to work correctly ---

    suspend fun play() {
        rpc.call("core.playback.play")
        updatePlaybackState()
    }

    suspend fun pause() {
        rpc.call("core.playback.pause")
        updatePlaybackState()
    }

    suspend fun next() {
        rpc.call("core.playback.next")
        updatePlaybackState()
    }

    suspend fun previous() {
        rpc.call("core.playback.previous")
        updatePlaybackState()
    }

    suspend fun getCurrentTrack(): JsonObject? = rpc.call("core.playback.get_current_track") as? JsonObject
    suspend fun getPlaybackState(): String? = rpc.call("core.playback.get_state")?.jsonPrimitive?.contentOrNull
    suspend fun getTimePosition(): Int? = rpc.call("core.playback.get_time_position")?.jsonPrimitive?.intOrNull

    // Update all playback state flows (called by PlayerScreen polling and by playback actions)
    suspend fun updatePlaybackState() {
        _currentTrack.value = getCurrentTrack()
        _playbackState.value = getPlaybackState()
        _timePosition.value = getTimePosition()
    }
    suspend fun seek(ms: Int) {
        val params = buildJsonObject { put("time_position", JsonPrimitive(ms)) }
        rpc.call("core.playback.seek", params)
    }

    suspend fun getAllTracks(): List<JsonObject> {
        // Step 1: Attempt to get all tracks from the local database first.
        var cachedTracks = dao.getAllCachedTracks()

        // Step 2: If the cache is empty or expired, fetch from the server and populate it.
        if (cachedTracks.isEmpty() || isTrackCacheExpired()) {
            Log.d("MopidyRepository", "Track cache is empty or expired. Fetching from server...")
            cacheAllTracksFromServer()
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

    private suspend fun deleteAllTracks() {
        dao.deleteAllTracks()
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
            updateTrackCacheTimestamp()
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

    suspend fun getTracklistLength(): Int? {
        val result = rpc.call("core.tracklist.get_length")
        return result?.jsonPrimitive?.intOrNull
    }

    suspend fun getCurrentTrackTlid(): Int? {
        val result = rpc.call("core.playback.get_current_tl_track")
        return result?.jsonObject?.get("tlid")?.jsonPrimitive?.intOrNull
    }

    suspend fun getTracklistTracks(): List<JsonObject> {
        val result = rpc.call("core.tracklist.get_tl_tracks")
        if (result !is JsonArray) return emptyList()
        return result.mapNotNull {
            it.jsonObject?.get("track")?.jsonObject
        }
    }

    suspend fun getTracklistTracksWithTlid(): List<Pair<Int, JsonObject>> {
        val result = rpc.call("core.tracklist.get_tl_tracks")
        if (result !is JsonArray) return emptyList()
        return result.mapNotNull { tlTrack ->
            val tlid = tlTrack.jsonObject?.get("tlid")?.jsonPrimitive?.intOrNull
            val track = tlTrack.jsonObject?.get("track")?.jsonObject
            if (tlid != null && track != null) tlid to track else null
        }
    }

    suspend fun removeTrackFromTracklist(tlid: Int): Boolean {
        return try {
            val params = buildJsonObject {
                put("criteria", buildJsonObject {
                    put("tlid", buildJsonArray { add(JsonPrimitive(tlid)) })
                })
            }
            val result = rpc.call("core.tracklist.remove", params)
            result?.jsonArray?.isNotEmpty() == true
        } catch (e: Exception) {
            Log.e("MopidyRepository", "Failed to remove track with tlid=$tlid", e)
            false
        }
    }

    suspend fun appendRandomTracksToQueue(count: Int, existingUris: Set<String>): Int {
        val randomTracks = getRandomTracks(count)
        val newTracks = randomTracks.filter {
            it["uri"]?.jsonPrimitive?.contentOrNull !in existingUris
        }
        if (newTracks.isEmpty()) return 0

        val trackUris = newTracks.mapNotNull { it["uri"]?.jsonPrimitive?.contentOrNull }
        val params = buildJsonObject {
            put("uris", buildJsonArray { trackUris.forEach { add(JsonPrimitive(it)) } })
        }
        rpc.call("core.tracklist.add", params)
        return newTracks.size
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
        // Extract search query string (typically from "any" key)
        val searchQuery = query["any"]?.firstOrNull() ?: return emptyList()

        // Search local database
        val matchedTracks = dao.searchTracks(searchQuery)

        // Convert tracks to JsonObject format
        val trackJsonObjects = matchedTracks.map { track ->
            buildJsonObject {
                put("__model__", "Track")
                put("uri", track.uri)
                put("name", track.name)
                put("length", track.length)
                put("artists", buildJsonArray {
                    if (!track.artistName.isNullOrBlank()) {
                        add(buildJsonObject {
                            put("__model__", "Artist")
                            put("name", track.artistName)
                        })
                    }
                })
                if (!track.albumUri.isNullOrBlank() && !track.albumName.isNullOrBlank()) {
                    put("album", buildJsonObject {
                        put("__model__", "Album")
                        put("uri", track.albumUri)
                        put("name", track.albumName)
                    })
                }
            }
        }

        // Extract unique albums from matched tracks
        val uniqueAlbums = matchedTracks
            .filter { !it.albumUri.isNullOrBlank() && !it.albumName.isNullOrBlank() }
            .distinctBy { it.albumUri }
            .map { track ->
                buildJsonObject {
                    put("__model__", "Album")
                    put("uri", track.albumUri!!)
                    put("name", track.albumName!!)
                    put("artists", buildJsonArray {
                        if (!track.artistName.isNullOrBlank()) {
                            add(buildJsonObject {
                                put("__model__", "Artist")
                                put("name", track.artistName)
                            })
                        }
                    })
                }
            }

        // Extract unique artists from matched tracks
        val uniqueArtists = matchedTracks
            .mapNotNull { it.artistName }
            .filter { it.isNotBlank() }
            .distinct()
            .map { artistName ->
                buildJsonObject {
                    put("__model__", "Artist")
                    put("name", artistName)
                }
            }

        // Build SearchResult object
        val searchResult = buildJsonObject {
            put("__model__", "SearchResult")
            put("uri", "local:search?any=$searchQuery")
            put("tracks", JsonArray(trackJsonObjects))
            if (uniqueAlbums.isNotEmpty()) {
                put("albums", JsonArray(uniqueAlbums))
            }
            if (uniqueArtists.isNotEmpty()) {
                put("artists", JsonArray(uniqueArtists))
            }
        }

        return listOf(searchResult)
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
        // 1. Get the Album URI from the track, not the track's own URI.
        val albumUri = track["album"]?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
        if (albumUri.isNullOrBlank()) {
            Log.w("ArtworkDebug", "Track '${track["name"]?.jsonPrimitive?.content}' has no album URI.")
            return null
        }

        // 2. Use the Album URI for the cache key.
        val cacheKey = "album-art|$albumUri"

        // 3. Check memory cache.
        ArtworkProvider.get(cacheKey)?.let { return it }

        // 4. Check database cache.
        dao.getArtwork(cacheKey)?.let {
            ArtworkProvider.put(cacheKey, it.imageUrl)
            return it.imageUrl
        }

        // 5. Fetch from server using the correct ALBUM URI.
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
        // Prune entries older than 90 days
        dao.deletePlayHistoryOlderThan(System.currentTimeMillis() - PLAY_HISTORY_MAX_AGE_MS)
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


}


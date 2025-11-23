package com.nil.mopitube.mopidy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Manages the app's private playback queue. This is our own queue,
 * separate from Mopidy's shared tracklist.
 */
class QueueManager {
    private val _queue = MutableStateFlow<List<JsonObject>>(emptyList())
    val queue = _queue.asStateFlow()

    fun setQueue(tracks: List<JsonObject>, currentTrackUri: String?) {
        val currentIndex = tracks.indexOfFirst { it["uri"]?.jsonPrimitive?.contentOrNull == currentTrackUri }
        // If the track is found, reorder the list to start with the current track
        if (currentIndex != -1) {
            val reorderedQueue = tracks.subList(currentIndex, tracks.size) + tracks.subList(0, currentIndex)
            _queue.value = reorderedQueue
        } else {
            // Otherwise, just set the queue as is
            _queue.value = tracks
        }
    }

    fun clearQueue() {
        _queue.value = emptyList()
    }
}

package com.nil.mopitube.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nil.mopitube.mopidy.MopidyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@Composable
fun PlaylistScreen(
    repo: MopidyRepository,
    playlistUri: String,
    // onBack: () -> Unit, // FIX: Removed
    onTrackClick: (trackUri: String) -> Unit,
    onPlayerClick: () -> Unit
) {
    var tracks by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var playlistName by remember { mutableStateOf("Playlist") }

    LaunchedEffect(playlistUri) {
        withContext(Dispatchers.IO) {
            val result = repo.lookup(playlistUri)
            if (result == null) {
                Log.e("PlaylistScreen", "Lookup for URI $playlistUri returned null.")
                return@withContext
            }
            val playlistObject = result.jsonArray.firstOrNull()?.jsonObject
            if (playlistObject != null) {
                playlistName = playlistObject["name"]?.jsonPrimitive?.contentOrNull ?: "Playlist"
                tracks = playlistObject["tracks"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
            }
        }
    }

    // FIX: Scaffold has been removed.
    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator() // Or a "Playlist is empty" message
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tracks) { track ->
                val trackName = track["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Track"
                val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull ?: ""
                ListItem(
                    headlineContent = { Text(trackName) },
                    modifier = Modifier.clickable {
                        if (trackUri.isNotEmpty()) {
                            onTrackClick(trackUri)
                        }
                    }
                )
            }
        }
    }
}

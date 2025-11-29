package com.nil.mopitube.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nil.mopitube.mopidy.MopidyRepository
import com.nil.mopitube.ui.components.TrackListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    repo: MopidyRepository,
    onTrackClick: (String) -> Unit
) {
    var tracks by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun refreshTracks(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            isLoading = true
            // This now calls a new function meant for refreshing from the server
            repo.refreshAllTracksFromServer()
            // After refreshing, we get the updated list from the local database
            tracks = repo.getAllTracks()
            isLoading = false
        }
    }

    // Initial load from the local database for speed
    LaunchedEffect(Unit) {
        isLoading = true
        tracks = repo.getAllTracks()
        isLoading = false

        // Optional: you could also trigger a refresh on first load if you always want fresh data
        // refreshTracks(this)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Songs") },
                actions = {
                    IconButton(onClick = { refreshTracks(scope) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("You have no songs in your library.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = paddingValues
                ) {
                    items(tracks) { track ->
                        TrackListItem(
                            repo = repo,
                            track = track,
                            onClick = {
                                // Correctly extracts the raw string from the JSON object
                                val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull ?: ""
                                onTrackClick(trackUri)
                            })
                    }
                }
            }
        }
    }
}

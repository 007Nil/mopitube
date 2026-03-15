package com.nil.mopitube.ui.screens

import android.widget.Toast
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
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val tracks = remember { mutableListOf<JsonObject>().toMutableStateList() }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun loadTracks(coroutineScope: CoroutineScope, forceRefresh: Boolean = false) {
        coroutineScope.launch {
            isLoading = true
            if (forceRefresh) repo.refreshAllTracksFromServer()
            val loaded = repo.getAllTracks()
            tracks.clear()
            tracks.addAll(loaded)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadTracks(this) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Songs") },
                actions = {
                    IconButton(onClick = { loadTracks(scope, forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("You have no songs in your library.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = paddingValues) {
                items(tracks, key = { it["uri"]?.jsonPrimitive?.contentOrNull ?: "" }) { track ->
                    TrackListItem(
                        repo = repo,
                        track = track,
                        onClick = {
                            val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull ?: ""
                            onTrackClick(trackUri)
                        },
                        onDelete = { trackUri ->
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    repo.deleteTrack(repo.serverHost, trackUri)
                                }
                                if (ok) {
                                    tracks.remove(track)
                                } else {
                                    Toast.makeText(context, "Delete failed — check server", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

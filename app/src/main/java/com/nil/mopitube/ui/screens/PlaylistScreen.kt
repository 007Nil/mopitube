package com.nil.mopitube.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import com.nil.mopitube.mopidy.MopidyRepository
import com.nil.mopitube.ui.components.TrackListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@Composable
fun PlaylistScreen(
    repo: MopidyRepository,
    playlistUri: String,
    onTrackClick: (trackUri: String) -> Unit,
    onPlayerClick: () -> Unit
) {
    val tracks = remember { mutableListOf<JsonObject>().toMutableStateList() }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(playlistUri) {
        withContext(Dispatchers.IO) {
            val playlist = repo.getPlaylist(playlistUri)
            val trackRefs = playlist?.get("tracks")?.jsonArray?.mapNotNull {
                it.jsonObject["uri"]?.jsonPrimitive?.contentOrNull
            } ?: emptyList()

            if (trackRefs.isNotEmpty()) {
                val params = buildJsonObject {
                    put("uris", buildJsonArray { trackRefs.forEach { add(it) } })
                }
                val result = repo.rpc.call("core.library.lookup", params)
                val loaded = trackRefs.mapNotNull { uri ->
                    result?.jsonObject?.get(uri)?.jsonArray?.firstOrNull()?.jsonObject
                }
                tracks.addAll(loaded)
            }
        }
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This playlist is empty.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tracks) { track ->
                val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull ?: ""
                TrackListItem(
                    repo = repo,
                    track = track,
                    onClick = { if (trackUri.isNotEmpty()) onTrackClick(trackUri) },
                    onRemoveFromPlaylist = { uri ->
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                repo.removeTrackFromPlaylist(playlistUri, uri)
                            }
                            if (ok) tracks.removeAll { it["uri"]?.jsonPrimitive?.contentOrNull == uri }
                        }
                    }
                )
            }
        }
    }
}

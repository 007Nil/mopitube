package com.nil.mopitube.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nil.mopitube.mopidy.MopidyRepository
import com.nil.mopitube.ui.components.TrackListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    repo: MopidyRepository,
    onTrackClick: (String) -> Unit
) {
    val tracks = remember { mutableListOf<JsonObject>().toMutableStateList() }
    var isLoading by remember { mutableStateOf(true) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val filteredTracks by remember {
        derivedStateOf {
            if (searchQuery.isBlank()) tracks.toList()
            else tracks.filter { track ->
                val title = track["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val artist = track["artists"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
                title.contains(searchQuery, ignoreCase = true) || artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

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
                    IconButton(onClick = { isSearchActive = !isSearchActive; if (!isSearchActive) searchQuery = "" }) {
                        Icon(if (isSearchActive) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search")
                    }
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
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search songs...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredTracks, key = { it["uri"]?.jsonPrimitive?.contentOrNull ?: "" }) { track ->
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
}

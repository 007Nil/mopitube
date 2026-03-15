package com.nil.mopitube.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nil.mopitube.mopidy.MopidyRepository
import com.nil.mopitube.ui.components.AlbumSearchResult
import com.nil.mopitube.ui.components.ArtistSearchResult
import com.nil.mopitube.ui.components.TrackListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun SearchScreen(
    repo: MopidyRepository,
    onAlbumClick: (String) -> Unit,
    onTrackClick: (String) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<JsonElement>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    var deletedUris by remember { mutableStateOf(setOf<String>()) }

    // Unwrap SearchResult objects and categorize by type
    val tracks = remember(searchResults, deletedUris) {
        searchResults.flatMap { result ->
            result.jsonObject["tracks"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        }.filter { it["uri"]?.jsonPrimitive?.contentOrNull !in deletedUris }
    }

    val albums = remember(searchResults) {
        searchResults.flatMap { result ->
            result.jsonObject["albums"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        }
    }

    val artists = remember(searchResults) {
        searchResults.flatMap { result ->
            result.jsonObject["artists"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
        }
    }

    val hasResults = tracks.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Search field
        OutlinedTextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
                searchJob?.cancel()
                searchJob = scope.launch {
                    delay(300)
                    if (newQuery.isNotBlank()) {
                        val results = repo.search(mapOf("any" to listOf(newQuery)))
                        searchResults = results
                    } else {
                        searchResults = emptyList()
                    }
                }
            },
            label = { Text("Search for songs, albums, or artists") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        // Results
        if (searchResults.isEmpty() && query.isNotBlank() && searchJob?.isCompleted == true) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No results found for \"$query\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (hasResults) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Songs section
                if (tracks.isNotEmpty()) {
                    item {
                        SectionHeader("Songs")
                    }
                    items(tracks) { track ->
                        TrackListItem(
                            repo = repo,
                            track = track,
                            onClick = {
                                onTrackClick(track["uri"]?.jsonPrimitive?.contentOrNull ?: "")
                            },
                            onDelete = { trackUri ->
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        repo.deleteTrack(repo.serverHost, trackUri)
                                    }
                                    if (ok) {
                                        deletedUris = deletedUris + trackUri
                                    } else {
                                        Toast.makeText(context, "Delete failed — check server", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }

                // Albums section
                if (albums.isNotEmpty()) {
                    item {
                        SectionHeader("Albums")
                    }
                    items(albums) { album ->
                        AlbumSearchResult(
                            repo = repo,
                            album = album,
                            onClick = {
                                onAlbumClick(album["uri"]?.jsonPrimitive?.contentOrNull ?: "")
                            }
                        )
                    }
                }

                // Artists section
                if (artists.isNotEmpty()) {
                    item {
                        SectionHeader("Artists")
                    }
                    items(artists) { artist ->
                        ArtistSearchResult(
                            artist = artist,
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "Artist details coming soon",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

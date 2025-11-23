package com.nil.mopitube.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nil.mopitube.mopidy.MopidyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun LikedSongsScreen(
    repo: MopidyRepository,
    onTrackClick: (trackUri: String) -> Unit,
    onPlayerClick: () -> Unit
) {
    var likedTracks by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                likedTracks = repo.getLikedTracks()
            } catch (e: Exception) {
                Log.e("LikedSongsScreen", "Failed to fetch liked tracks", e)
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (likedTracks.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            val trackUris = likedTracks.mapNotNull { it["uri"]?.jsonPrimitive?.contentOrNull }
                            repo.playAll(trackUris)
                            onPlayerClick()
                        }
                    },
                    icon = { Icon(Icons.Filled.PlayArrow, "Play All") },
                    text = { Text(text = "Play All") }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (likedTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("You haven't liked any songs yet.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(likedTracks) { track ->
                    LikedSongItem(
                        track = track,
                        repo = repo,
                        onClick = { trackUri ->
                            if (trackUri.isNotEmpty()) {
                                onTrackClick(trackUri)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LikedSongItem(
    track: JsonObject,
    repo: MopidyRepository,
    onClick: (String) -> Unit
) {
    var artworkUrl by remember { mutableStateOf<String?>(null) }
    val trackName = track["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Track"
    val artistName = track["artists"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
    val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull ?: ""

    LaunchedEffect(track) {
        artworkUrl = withContext(Dispatchers.IO) {
            repo.findArtwork(track)
        }
    }

    ListItem(
        headlineContent = { Text(trackName) },
        supportingContent = { artistName?.let { Text(it) } },
        leadingContent = {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Album art for $trackName",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp) // A slightly larger image for this screen
                    .clip(MaterialTheme.shapes.small)
            )
        },
        modifier = Modifier.clickable { onClick(trackUri) }
    )
}

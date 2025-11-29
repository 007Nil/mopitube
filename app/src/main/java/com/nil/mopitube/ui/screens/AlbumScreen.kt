package com.nil.mopitube.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nil.mopitube.mopidy.MopidyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun AlbumScreen(
    repo: MopidyRepository,
    albumUri: String,
    onTrackClick: (trackUri: String) -> Unit,
    onPlayerClick: () -> Unit
) {
    var tracks by remember { mutableStateOf<List<JsonObject>>(emptyList()) }

    LaunchedEffect(albumUri) {
        withContext(Dispatchers.IO) {
            val result = repo.getAlbumSongs(albumUri)
            if (result == null) {
                Log.e("AlbumScreen", "Lookup for URI $albumUri returned null.")
                return@withContext
            }
            tracks = result.jsonArray.mapNotNull { it.jsonObject }
        }
    }

    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tracks) { track ->
                TrackListItem(
                    repo = repo,
                    track = track,
                    onClick = {
                        val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull ?: ""
                        if (trackUri.isNotEmpty()) {
                            onTrackClick(trackUri)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun TrackListItem(
    repo: MopidyRepository,
    track: JsonObject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trackName = track["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Track"
    var imageUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(track) {
        val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull
        if (!trackUri.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                imageUrl = repo.getAlbumImages(trackUri).firstOrNull()
            }
        }
    }

    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = { Text(trackName) },
        leadingContent = {
            Surface(
                shadowElevation = 2.dp,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = trackName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "No Artwork",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    )
}

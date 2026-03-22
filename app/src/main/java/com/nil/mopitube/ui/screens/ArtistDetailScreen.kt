package com.nil.mopitube.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.nil.mopitube.ui.components.AlbumListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ArtistDetailScreen(
    repo: MopidyRepository,
    artistName: String,
    onAlbumClick: (String) -> Unit,
    onPlayAlbum: (String) -> Unit,
    onTrackClick: (String) -> Unit
) {
    var albums by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var artworkUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(artistName) {
        isLoading = true
        withContext(Dispatchers.IO) {
            artworkUrl = repo.findArtistArtwork(artistName)
            albums = repo.getAlbumsByArtist(artistName)
        }
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (artworkUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(artworkUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = artistName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(artistName, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${albums.size} album${if (albums.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    scope.launch {
                        val tracks = withContext(Dispatchers.IO) { repo.getTracksByArtistName(artistName) }
                        val firstUri = tracks.firstOrNull()?.get("uri")?.jsonPrimitive?.contentOrNull
                        if (firstUri != null) onTrackClick(firstUri)
                    }
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play All")
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
            }
        }

        // Albums list
        if (albums.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No albums found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(albums) { album ->
                val uri = album["uri"]?.jsonPrimitive?.content
                AlbumListItem(
                    repo = repo,
                    album = album,
                    onClick = { uri?.let { onAlbumClick(it) } },
                    onPlayAlbum = { uri?.let { onPlayAlbum(it) } }
                )
            }
        }
    }
}

package com.nil.mopitube.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.nil.mopitube.data.UserPreferencesRepository
import com.nil.mopitube.mopidy.MopidyRepository
import com.nil.mopitube.mopidy.uploadArtworkToServer
import com.nil.mopitube.ui.components.TrackListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val tracks = remember { mutableListOf<JsonObject>().toMutableStateList() }
    var albumArtUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingArtwork by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userPrefs = remember { UserPreferencesRepository(context) }
    val serverHost by userPrefs.serverHost.collectAsState(initial = "")

    LaunchedEffect(albumUri) {
        withContext(Dispatchers.IO) {
            val result = repo.getAlbumSongs(albumUri)
            if (result == null) {
                Log.e("AlbumScreen", "Lookup for URI $albumUri returned null.")
                return@withContext
            }
            val loaded = result.jsonArray.mapNotNull { it.jsonObject }
            tracks.clear()
            tracks.addAll(loaded)
            albumArtUrl = tracks.firstOrNull()?.let { repo.findArtwork(it) }
        }
    }

    val artworkPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (serverHost.isBlank()) return@rememberLauncherForActivityResult
        scope.launch {
            isUploadingArtwork = true
            try {
                val firstTrackUri = tracks.firstOrNull()?.get("uri")?.jsonPrimitive?.contentOrNull ?: albumUri
                val ok = uploadArtworkToServer(serverHost, firstTrackUri, uri, context)
                if (ok) {
                    val oldUrl = albumArtUrl
                    @OptIn(coil.annotation.ExperimentalCoilApi::class)
                    oldUrl?.let { url ->
                        context.imageLoader.memoryCache?.remove(MemoryCache.Key(url))
                        context.imageLoader.diskCache?.remove(url)
                    }
                    val encodedUri = java.net.URLEncoder.encode(firstTrackUri, "UTF-8")
                    val coverUrl = "http://$serverHost:9000/cover?track_uri=$encodedUri"
                    withContext(Dispatchers.IO) {
                        repo.clearArtworkForAlbum(albumUri)
                        repo.setCachedArtwork(firstTrackUri, albumUri, coverUrl)
                    }
                    albumArtUrl = coverUrl
                    Toast.makeText(context, "Artwork updated!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Upload failed — check server", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isUploadingArtwork = false
            }
        }
    }

    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.8f)
                ) {
                    if (albumArtUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(albumArtUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (isUploadingArtwork) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("Uploading artwork…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            if (!isUploadingArtwork) artworkPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        containerColor = if (isUploadingArtwork)
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    ) {
                        if (isUploadingArtwork) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Change artwork")
                        }
                    }
                }
            }

            items(tracks, key = { it["uri"]?.jsonPrimitive?.contentOrNull ?: "" }) { track ->
                TrackListItem(
                    repo = repo,
                    track = track,
                    onClick = {
                        val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull ?: ""
                        if (trackUri.isNotEmpty()) onTrackClick(trackUri)
                    },
                    onDelete = { trackUri ->
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                repo.deleteTrack(serverHost, trackUri)
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

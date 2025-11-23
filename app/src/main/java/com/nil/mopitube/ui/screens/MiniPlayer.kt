package com.nil.mopitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nil.mopitube.mopidy.MopidyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun MiniPlayer(
    repo: MopidyRepository,
    onPlayerClick: () -> Unit,
    modifier: Modifier = Modifier
    // FIX: Removed animation parameters
) {
    var currentTrack by remember { mutableStateOf<JsonObject?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var artworkUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Poll for track and playback state
    LaunchedEffect(Unit) {
        while (true) {
            currentTrack = repo.getCurrentTrack()
            isPlaying = repo.getPlaybackState() == "playing"
            delay(1500)
        }
    }

    // Fetch artwork when the track changes
    LaunchedEffect(currentTrack) { // Or just `track`
        withContext(Dispatchers.IO) {
            artworkUrl = repo.findArtwork(currentTrack) // Use the new unified function
        }
    }

    if (currentTrack != null) {
        val trackName = currentTrack?.get("name")?.jsonPrimitive?.contentOrNull ?: "Now Playing"
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clickable { onPlayerClick() },
            tonalElevation = 4.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Track Info with Album Art
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onPlayerClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artworkUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(artworkUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Album Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "No Artwork"
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = trackName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Playback Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { repo.previous() } }) {
                        Icon(Icons.Filled.SkipPrevious, "Previous")
                    }

                    IconButton(onClick = { scope.launch { if (isPlaying) repo.pause() else repo.play() } }) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(onClick = { scope.launch { repo.next() } }) {
                        Icon(Icons.Filled.SkipNext, "Next")
                    }
                }
            }
        }
    }
}

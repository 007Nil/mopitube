package com.nil.mopitube.ui.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull // <-- ===== THE FIX IS HERE =====

@Composable
fun TrackGridItem(
    repo: MopidyRepository,
    track: JsonObject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = track["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Track"
    var artworkUrl by remember { mutableStateOf<String?>(null) } // Or `var artwork by remember...`

    // When the track item appears, launch an effect to find its artwork
    LaunchedEffect(track) {
        withContext(Dispatchers.IO) {
            artworkUrl = repo.findArtwork(track)
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(end = 16.dp), // Padding for space between items
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork Box
        Surface(
            shadowElevation = 2.dp,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.size(56.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Log.d("ArtworkDebug", "Artwork URL: $artworkUrl")
                if (artworkUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artworkUrl)
                            .crossfade(true)
                            .listener(
                                onError = { _, err ->
                                    Log.e("ArtworkLoad", "Failed: $artworkUrl → ${err.throwable}")
                                },
                                onSuccess = {_, result ->
                                    Log.d("ArtworkLoad", "Success: $artworkUrl → $result")
                                }
                            )
                            .build(),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback icon
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "No Artwork",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Track Name
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

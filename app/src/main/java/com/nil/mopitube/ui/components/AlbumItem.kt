package com.nil.mopitube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun AlbumItem(
    repo: MopidyRepository,
    album: JsonObject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = album["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Album"
    var imageUrl by remember { mutableStateOf<String?>(null) }

    // This logic is now simple, direct, and efficient.
    LaunchedEffect(album) {
        val albumUri = album["uri"]?.jsonPrimitive?.contentOrNull
        if (!albumUri.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                // It calls the correct, caching function from the repository.
                imageUrl = repo.getAlbumImages(albumUri).firstOrNull()
            }
        }
    }

    Column(
        modifier = modifier
            .width(160.dp) // Fixed width for a clean, horizontally-scrollable list
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Surface(
            shadowElevation = 4.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.aspectRatio(1f) // Make the artwork square
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback icon if no artwork is found
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = "No Artwork",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


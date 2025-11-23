package com.nil.mopitube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun PlaylistItem(
    playlist: JsonObject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = playlist["name"]?.jsonPrimitive?.content ?: "Unknown Playlist"

    // ===== THE FIX IS HERE =====
    // The 'images' field contains a list of Image objects. We need to get the first object
    // and then get the 'uri' property from within that object.
    val imageUrl = playlist["images"]
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject // Treat the item as an object
        ?.get("uri") // Get the "uri" key from the object
        ?.jsonPrimitive
        ?.contentOrNull

    Column(
        modifier = modifier
            .width(160.dp) // Give it a fixed width for horizontal scrolling
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        // Artwork Box
        Surface(
            shadowElevation = 4.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.aspectRatio(1f) // Make it square
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)) // Clip the image to the shape
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
                    // Fallback icon if no image is available
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "No Artwork",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Playlist Name
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

package com.nil.mopitube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
fun AlbumSearchResult(
    repo: MopidyRepository,
    album: JsonObject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val albumName = album["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Album"
    val artistNames = album["artists"]?.jsonArray
        ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
        ?.joinToString(", ") ?: "Unknown Artist"

    var imageUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(album) {
        val albumUri = album["uri"]?.jsonPrimitive?.contentOrNull
        if (!albumUri.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                imageUrl = repo.getAlbumImages(albumUri).firstOrNull()
            }
        }
    }

    ListItem(
        modifier = modifier.clickable { onClick() },
        headlineContent = { Text(albumName) },
        supportingContent = { Text(artistNames) },
        leadingContent = {
            Surface(
                shadowElevation = 2.dp,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.size(56.dp)
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
                            contentDescription = albumName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = "No Artwork",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    )
}

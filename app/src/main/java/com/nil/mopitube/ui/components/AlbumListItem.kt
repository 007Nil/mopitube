package com.nil.mopitube.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumListItem(
    repo: MopidyRepository,
    album: JsonObject,
    onClick: () -> Unit,
    onPlayAlbum: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val albumName = album["name"]?.jsonPrimitive?.content ?: "Unknown Album"
    val albumUri = album["uri"]?.jsonPrimitive?.content ?: ""

    LaunchedEffect(albumUri) {
        withContext(Dispatchers.IO) {
            imageUrl = repo.getAlbumImages(albumUri).firstOrNull()
        }
    }

    Box {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (onPlayAlbum != null) { { showMenu = true } } else null
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "$albumName artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onPlayAlbum != null) {
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Play Album") },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onPlayAlbum()
                    }
                )
            }
        }
    }
}

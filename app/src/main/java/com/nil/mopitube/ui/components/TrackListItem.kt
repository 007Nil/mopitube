// app/src/main/java/com/nil/mopitube/ui/components/TrackListItem.kt

package com.nil.mopitube.ui.components

import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nil.mopitube.mopidy.MopidyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import kotlinx.coroutines.Job
import kotlinx.serialization.json.contentOrNull

@Composable
@OptIn(ExperimentalFoundationApi::class) // Add this annotation for combinedClickable
fun TrackListItem(
    repo: MopidyRepository,
    track: JsonObject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToQueue: () -> Job
) {
    val trackName = track["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Track"
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) } // State for the dropdown menu

    LaunchedEffect(track) {
        val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull
        if (!trackUri.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                imageUrl = repo.getAlbumImages(trackUri).firstOrNull()
            }
        }
    }

    ListItem(
        modifier = modifier.combinedClickable(
            onClick = onClick, // Regular click plays the track
            onLongClick = {
                showMenu = true // Long click opens the menu
            }
        ),
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
        },
        // Trailing content for the dropdown menu
        trailingContent = {
            Box {
                // You can have an icon to indicate a menu is available
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }

                // The DropdownMenu
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        onClick = {
                            // TODO: Implement "Add to queue" logic
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.QueueMusic, contentDescription = "Add to queue")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        onClick = {
                            // TODO: Implement "Play next" logic
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.PlaylistPlay, contentDescription = "Play next")
                        }
                    )
                }
            }
        }
    )
}
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun TrackListItem(
    repo: MopidyRepository,
    track: JsonObject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    onAddToQueue: (() -> Job)? = null,
    onDelete: ((trackUri: String) -> Unit)? = null,
    onRemoveFromPlaylist: ((trackUri: String) -> Unit)? = null
) {
    val trackName = track["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Track"
    val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull ?: ""
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var isInListenLater by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(trackUri) {
        if (trackUri.isNotEmpty()) {
            isInListenLater = withContext(Dispatchers.IO) { repo.isInListenLater(trackUri) }
        }
    }

    LaunchedEffect(track) {
        withContext(Dispatchers.IO) {
            imageUrl = repo.findArtwork(track)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete song?") },
            text = {
                Text("\"$trackName\" will be permanently deleted from the server. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete?.invoke(trackUri)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            repo = repo,
            trackUri = trackUri,
            onDismiss = { showPlaylistDialog = false },
            onResult = { message ->
                scope.launch { snackbarHostState?.showSnackbar(message) }
            }
        )
    }

    val hasMenu = true

    ListItem(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { if (hasMenu) showMenu = true }
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
        trailingContent = if (hasMenu) {
            {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (onAddToQueue != null) {
                            DropdownMenuItem(
                                text = { Text("Add to queue") },
                                onClick = {
                                    onAddToQueue()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.QueueMusic, contentDescription = "Add to queue")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Play next") },
                                onClick = { showMenu = false },
                                leadingIcon = {
                                    Icon(Icons.Default.PlaylistPlay, contentDescription = "Play next")
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (isInListenLater) "Remove from Later" else "Save for Later") },
                            onClick = {
                                showMenu = false
                                scope.launch {
                                    if (isInListenLater) {
                                        withContext(Dispatchers.IO) { repo.removeFromListenLater(trackUri) }
                                    } else {
                                        withContext(Dispatchers.IO) { repo.saveForLater(track, 0) }
                                    }
                                    isInListenLater = !isInListenLater
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    if (isInListenLater) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Save for Later",
                                    tint = if (isInListenLater) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            onClick = {
                                showMenu = false
                                showPlaylistDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to playlist")
                            }
                        )
                        if (onRemoveFromPlaylist != null) {
                            DropdownMenuItem(
                                text = { Text("Remove from playlist", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onRemoveFromPlaylist(trackUri)
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PlaylistRemove,
                                        contentDescription = "Remove from playlist",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("Delete song", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete song",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            }
        } else {
            null
        }
    )
}

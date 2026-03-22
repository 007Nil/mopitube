package com.nil.mopitube.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nil.mopitube.mopidy.MopidyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    repo: MopidyRepository,
    onPlaylistClick: (String) -> Unit,
    onPlaylistLongPress: (String) -> Unit = {}
) {
    val playlists = remember { mutableListOf<JsonObject>().toMutableStateList() }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // uri of the playlist whose context menu is open, null = none
    var menuPlaylistUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { repo.getPlaylists() }
        playlists.addAll(loaded)
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No playlists yet. Create one from any song's menu.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(playlists, key = { it["uri"]?.jsonPrimitive?.contentOrNull ?: "" }) { playlist ->
                val name = playlist["name"]?.jsonPrimitive?.contentOrNull ?: "Unnamed Playlist"
                val uri = playlist["uri"]?.jsonPrimitive?.contentOrNull ?: ""

                Box {
                    ListItem(
                        headlineContent = { Text(name) },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = { if (uri.isNotEmpty()) onPlaylistClick(uri) },
                            onLongClick = { if (uri.isNotEmpty()) menuPlaylistUri = uri }
                        )
                    )
                    DropdownMenu(
                        expanded = menuPlaylistUri == uri,
                        onDismissRequest = { menuPlaylistUri = null }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play all") },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            onClick = {
                                menuPlaylistUri = null
                                onPlaylistLongPress(uri)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete playlist", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuPlaylistUri = null
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { repo.deletePlaylist(uri) }
                                    if (ok) playlists.removeAll { it["uri"]?.jsonPrimitive?.contentOrNull == uri }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

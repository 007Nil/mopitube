package com.nil.mopitube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nil.mopitube.mopidy.MopidyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun AddToPlaylistDialog(
    repo: MopidyRepository,
    trackUri: String,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit
) {
    var playlists by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        playlists = withContext(Dispatchers.IO) { repo.getPlaylists() }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text("New Playlist") },
                            leadingContent = {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                            },
                            modifier = Modifier.clickable { showCreateDialog = true }
                        )
                        HorizontalDivider()
                    }
                    if (playlists.isEmpty()) {
                        item { Text("No existing playlists.", modifier = Modifier.padding(16.dp)) }
                    } else {
                        items(playlists) { playlist ->
                            val name = playlist["name"]?.jsonPrimitive?.contentOrNull ?: "Unnamed"
                            val uri = playlist["uri"]?.jsonPrimitive?.contentOrNull ?: return@items
                            ListItem(
                                headlineContent = { Text(name) },
                                modifier = Modifier.clickable {
                                    onDismiss()
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            repo.addTrackToPlaylist(uri, trackUri)
                                        }
                                        onResult(if (ok) "Added to $name" else "Failed to add to $name")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCreating) { showCreateDialog = false; newPlaylistName = "" } },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isBlank()) return@TextButton
                        isCreating = true
                        scope.launch {
                            val created = withContext(Dispatchers.IO) {
                                repo.createPlaylist(newPlaylistName.trim())
                            }
                            val newUri = created?.get("uri")?.jsonPrimitive?.contentOrNull
                            if (newUri != null) {
                                val ok = withContext(Dispatchers.IO) {
                                    repo.addTrackToPlaylist(newUri, trackUri)
                                }
                                onDismiss()
                                onResult(if (ok) "Added to \"${newPlaylistName.trim()}\"" else "Playlist created but failed to add track")
                            } else {
                                onResult("Failed to create playlist")
                            }
                            isCreating = false
                            showCreateDialog = false
                            newPlaylistName = ""
                        }
                    },
                    enabled = newPlaylistName.isNotBlank() && !isCreating
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Create")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false; newPlaylistName = "" },
                    enabled = !isCreating
                ) { Text("Cancel") }
            }
        )
    }
}

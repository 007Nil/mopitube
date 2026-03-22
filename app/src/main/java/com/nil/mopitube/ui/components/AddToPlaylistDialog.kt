package com.nil.mopitube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
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
            } else if (playlists.isEmpty()) {
                Text("No playlists found.")
            } else {
                LazyColumn {
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
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

package com.nil.mopitube.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nil.mopitube.database.ListenLaterEntry
import com.nil.mopitube.mopidy.MopidyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ListenLaterScreen(
    repo: MopidyRepository,
    onResume: (uri: String, positionMs: Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ListenLaterEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) { repo.getListenLaterTracks() }
    }

    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Bookmark, contentDescription = null,
                    modifier = Modifier.size(64.dp), tint = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Text("No saved tracks", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Text("Tap Save for Later while listening to bookmark a track.",
                    color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(entries, key = { it.uri }) { entry ->
            ListenLaterItem(
                entry = entry,
                onResume = { onResume(entry.uri, entry.positionMs) },
                onDelete = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.removeFromListenLater(entry.uri) }
                        entries = withContext(Dispatchers.IO) { repo.getListenLaterTracks() }
                    }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ListenLaterItem(
    entry: ListenLaterEntry,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.MusicNote, contentDescription = null,
            modifier = Modifier.size(40.dp).padding(8.dp), tint = Color.Gray)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge)
            if (!entry.artist.isNullOrBlank()) {
                Text(entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text(
                "Resume from ${formatPosition(entry.positionMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        TextButton(onClick = onResume) { Text("Resume") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Gray)
        }
    }
}

private fun formatPosition(ms: Int): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

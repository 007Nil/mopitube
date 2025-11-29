package com.nil.mopitube.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nil.mopitube.mopidy.MopidyRepository
import com.nil.mopitube.ui.components.AlbumListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.foundation.ExperimentalFoundationApi // For combinedClickable
import androidx.compose.foundation.combinedClickable // For long press
import androidx.compose.material.icons.filled.MoreVert // For the menu icon (optional)
import androidx.compose.material.icons.filled.QueueMusic // For "Add to queue"
import androidx.compose.material.icons.filled.PlaylistPlay // For "Play next"


@Composable
fun AlbumsScreen(
    repo: MopidyRepository,
    onAlbumClick: (String) -> Unit
) {
    var albums by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        withContext(Dispatchers.IO) {
            albums = repo.getAllAlbums()
        }
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        if (albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("You have no albums in your library.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(albums) { album ->
                    val uri = album["uri"]?.jsonPrimitive?.content
                    Log.d("AlbumsScreen", "The uri value is $uri")
                    AlbumListItem(
                        repo = repo,
                        album = album,
                        onClick = { uri?.let { onAlbumClick(it) } }
                    )
                }
            }
        }
    }
}

package com.nil.mopitube.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nil.mopitube.mopidy.MopidyRepository
import com.nil.mopitube.ui.components.AlbumListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    repo: MopidyRepository,
    onAlbumClick: (String) -> Unit,
    onPlayAlbum: (String) -> Unit
) {
    var albums by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun loadAlbums(coroutineScope: CoroutineScope, forceRefresh: Boolean = false) {
        coroutineScope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                if (forceRefresh) repo.refreshAllTracksFromServer()
                albums = repo.getAllAlbums()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadAlbums(scope) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Albums") },
                actions = {
                    IconButton(onClick = { loadAlbums(scope, forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("You have no albums in your library.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues
            ) {
                items(albums) { album ->
                    val uri = album["uri"]?.jsonPrimitive?.content
                    Log.d("AlbumsScreen", "The uri value is $uri")
                    AlbumListItem(
                        repo = repo,
                        album = album,
                        onClick = { uri?.let { onAlbumClick(it) } },
                        onPlayAlbum = { uri?.let { onPlayAlbum(it) } }
                    )
                }
            }
        }
    }
}

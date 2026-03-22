package com.nil.mopitube.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.nil.mopitube.ui.components.ArtistListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    repo: MopidyRepository,
    onArtistClick: (String) -> Unit
) {
    var artists by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun loadArtists(coroutineScope: CoroutineScope, forceRefresh: Boolean = false) {
        coroutineScope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                if (forceRefresh) repo.refreshAllTracksFromServer()
                artists = repo.getAllArtists()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadArtists(scope) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artists") },
                actions = {
                    IconButton(onClick = { loadArtists(scope, forceRefresh = true) }) {
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
        } else if (artists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No artists found in your library.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues
            ) {
                items(artists) { artist ->
                    val name = artist["name"]?.jsonPrimitive?.content ?: return@items
                    ArtistListItem(
                        artist = artist,
                        repo = repo,
                        onClick = { onArtistClick(name) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

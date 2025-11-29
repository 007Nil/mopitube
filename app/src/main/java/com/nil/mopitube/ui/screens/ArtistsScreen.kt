package com.nil.mopitube.ui.screens

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
import com.nil.mopitube.ui.components.ArtistListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ArtistsScreen(
    repo: MopidyRepository,
    onArtistClick: (String) -> Unit
) {
    var artists by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        withContext(Dispatchers.IO) {
            artists = repo.getAllArtists()
        }
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        if (artists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("You have no artists in your library.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(artists) { artist ->
                    val uri = artist["uri"]?.jsonPrimitive?.content
                    ArtistListItem(
                        artist = artist,
                        onClick = { uri?.let { onArtistClick(it) } }
                    )
                }
            }
        }
    }
}

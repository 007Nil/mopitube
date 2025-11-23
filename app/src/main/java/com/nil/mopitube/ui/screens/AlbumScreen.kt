package com.nil.mopitube.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nil.mopitube.mopidy.MopidyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun AlbumScreen(
    repo: MopidyRepository,
    albumUri: String,
    onTrackClick: (trackUri: String) -> Unit,
    onPlayerClick: () -> Unit
) {
    var tracks by remember { mutableStateOf<List<JsonObject>>(emptyList()) }

    LaunchedEffect(albumUri) {
        withContext(Dispatchers.IO) {
            val result = repo.lookup(albumUri)
            if (result == null) {
                Log.e("AlbumScreen", "Lookup for URI $albumUri returned null.")
                return@withContext
            }
            tracks = result.jsonArray.mapNotNull { it.jsonObject }
        }
    }

    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tracks) { track ->
                val trackName = track["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Track"
                val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull ?: ""
                ListItem(
                    headlineContent = { Text(trackName) },
                    modifier = Modifier.clickable {
                        if (trackUri.isNotEmpty()) {
                            onTrackClick(trackUri)
                        }
                    }
                )
            }
        }
    }
}

package com.nil.mopitube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nil.mopitube.mopidy.MopidyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun SearchScreen(
    repo: MopidyRepository,
    onAlbumClick: (String) -> Unit,
    onTrackClick: (String) -> Unit
    // FIX: onPlayerClick parameter removed from the signature.
) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<JsonElement>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                searchJob?.cancel()
                searchJob = scope.launch {
                    delay(300)
                    if (query.isNotBlank()) {
                        searchResults = repo.search(mapOf("any" to listOf(query)))
                    } else {
                        searchResults = emptyList()
                    }
                }
            },
            label = { Text("Search for artists, albums, or tracks") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        if (searchResults.isEmpty() && query.isNotBlank() && searchJob?.isCompleted == true) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No results found for \"$query\"")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(searchResults) { result ->
                    val resultObject = result.jsonObject
                    when (resultObject["__model__"]?.jsonPrimitive?.content) {
                        "Track" -> {
                            val trackName = resultObject["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Track"
                            val trackUri = resultObject["uri"]?.jsonPrimitive?.contentOrNull ?: ""
                            ListItem(
                                headlineContent = { Text(trackName) },
                                modifier = Modifier.clickable { onTrackClick(trackUri) }
                            )
                        }
                        "Album" -> {
                            val albumName = resultObject["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Album"
                            val albumUri = resultObject["uri"]?.jsonPrimitive?.contentOrNull ?: ""
                            ListItem(
                                headlineContent = { Text(albumName) },
                                modifier = Modifier.clickable { onAlbumClick(albumUri) }
                            )
                        }
                    }
                }
            }
        }
    }
}

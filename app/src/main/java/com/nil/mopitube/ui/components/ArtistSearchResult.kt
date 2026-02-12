package com.nil.mopitube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ArtistSearchResult(
    artist: JsonObject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artistName = artist["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"

    ListItem(
        modifier = modifier.clickable { onClick() },
        headlineContent = { Text(artistName) },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Artist"
            )
        }
    )
}

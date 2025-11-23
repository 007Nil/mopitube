package com.nil.mopitube.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun MusicCardLarge(title: String, subtitle: String, artwork: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(280.dp)) {
        AsyncImage(model = artwork, contentDescription = null, modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MusicCardSquare(title: String, subtitle: String, artwork: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(140.dp)) {
        AsyncImage(model = artwork, contentDescription = null, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
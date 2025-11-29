package com.nil.mopitube.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.colorspace.connect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nil.mopitube.mopidy.MopidyRepository
import com.nil.mopitube.ui.components.AlbumItem
import com.nil.mopitube.ui.components.PlaylistItem
import com.nil.mopitube.ui.components.TrackGridItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

// Your Shelf models are correct
sealed class HomeShelf(val title: String) {
    data class QuickPicksShelf(val shelfTitle: String, val items: List<JsonObject>) : HomeShelf(shelfTitle)
    data class MostPlayedShelf(val shelfTitle: String, val items: List<JsonObject>) : HomeShelf(shelfTitle)
    data class PlaylistShelf(val shelfTitle: String, val items: List<JsonObject>) : HomeShelf(shelfTitle)
    data class AlbumShelf(val shelfTitle: String, val items: List<JsonObject>) : HomeShelf(shelfTitle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: MopidyRepository,
    onPlaylistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onTrackClick: (String) -> Unit,
    onPlayerClick: () -> Unit,
    onLikedSongsClick: () -> Unit
) {
    var shelves by remember { mutableStateOf<List<HomeShelf>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val quickPicksJob = async { repo.getRandomTracks(count = 20) }
            val mostPlayedJob = async { repo.getMostPlayedTracks() }
            val playlistsJob = async { repo.getPlaylists() }
            val albumsJob = async { repo.getRecentlyAddedAlbums() }

            val quickPicks = quickPicksJob.await()
            val mostPlayed = mostPlayedJob.await()
            val userPlaylists = playlistsJob.await()
            val recentAlbums = albumsJob.await()

            Log.d("HomeScreen", "Quick Picks: $quickPicks")

            val homeShelves = mutableListOf<HomeShelf>()
            if (quickPicks.isNotEmpty()) { homeShelves.add(HomeShelf.QuickPicksShelf("Quick Picks", quickPicks)) }
            if (mostPlayed.isNotEmpty()) { homeShelves.add(HomeShelf.MostPlayedShelf("Most Played", mostPlayed)) }
            if (recentAlbums.isNotEmpty()) { homeShelves.add(HomeShelf.AlbumShelf("Your Albums", recentAlbums)) }
            if (userPlaylists.isNotEmpty()) { homeShelves.add(HomeShelf.PlaylistShelf("Your Playlists", userPlaylists)) }
            shelves = homeShelves
        }
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Log.d("HomeScreen", "Loading...")
            CircularProgressIndicator()
        }
    } else {
        Column {
            Divider()
            if (shelves.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Your library is empty. Add some music!")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                            item {
                                AssistChip(
                                    onClick = { onLikedSongsClick() },
                                    label = { Text("Liked Songs") },
                                    leadingIcon = { Icon(Icons.Filled.Favorite, null) }
                                )
                            }
                        }
                    }
                    items(shelves) { shelf ->
                        when (shelf) {
                            is HomeShelf.QuickPicksShelf -> QuickPicksShelfView(repo, shelf.title, shelf.items, onTrackClick)
                            is HomeShelf.MostPlayedShelf -> TrackShelfView(repo, shelf.title, shelf.items, onTrackClick)
                            is HomeShelf.PlaylistShelf -> PlaylistShelfView(shelf.title, shelf.items, onPlaylistClick)
                            is HomeShelf.AlbumShelf -> AlbumShelfView(repo, shelf.title, shelf.items, onAlbumClick)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun QuickPicksShelfView(repo: MopidyRepository, title: String, tracks: List<JsonObject>, onTrackClick: (String) -> Unit) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        LazyHorizontalGrid(
            rows = GridCells.Fixed(4),
            modifier = Modifier.height(256.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tracks) { track ->
                val uri = track["uri"]?.jsonPrimitive?.content
                TrackGridItem(repo = repo, track = track, onClick = { uri?.let { onTrackClick(it) } }, modifier = Modifier.width(250.dp))
            }
        }
    }
}

// ===== THE FINAL FIX IS HERE =====
@Composable
fun TrackShelfView(
    repo: MopidyRepository,
    title: String,
    tracks: List<JsonObject>,
    onTrackClick: (String) -> Unit
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
            items(tracks) { track ->
                val uri = track["uri"]?.jsonPrimitive?.content
                // FIX: Use AlbumItem to render the track, as it's designed to show artwork.
                // We pass the 'track' object to the 'album' parameter. This works because
                // AlbumItem just needs a 'name' and 'uri' to fetch artwork, which the track object has.
                AlbumItem(repo = repo, album = track, onClick = { uri?.let { onTrackClick(it) } })
            }
        }
    }
}


@Composable
fun PlaylistShelfView(title: String, playlists: List<JsonObject>, onPlaylistClick: (String) -> Unit) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
            items(playlists) { playlist ->
                val uri = playlist["uri"]?.jsonPrimitive?.content
                PlaylistItem(playlist = playlist, onClick = { uri?.let { onPlaylistClick(it) } })
            }
        }
    }
}

@Composable
fun AlbumShelfView(repo: MopidyRepository, title: String, albums: List<JsonObject>, onAlbumClick: (String) -> Unit) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
            items(albums) { album ->
                val uri = album["uri"]?.jsonPrimitive?.content
                AlbumItem(repo = repo, album = album, onClick = { uri?.let { onAlbumClick(it) } })
            }
        }
    }
}

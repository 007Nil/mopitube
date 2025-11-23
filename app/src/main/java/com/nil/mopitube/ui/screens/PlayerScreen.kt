package com.nil.mopitube.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nil.mopitube.R
import com.nil.mopitube.mopidy.MopidyClient
import com.nil.mopitube.mopidy.MopidyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    client: MopidyClient,
    onBack: () -> Unit
) {
    val repo = client.repo
    val queueManager = client.queueManager
    val scope = rememberCoroutineScope()

    var currentTrack by remember { mutableStateOf<JsonObject?>(null) }
    var artworkUrl by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0) }
    var positionMs: Int? by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var pendingSeekPosition by remember { mutableStateOf(0f) }
    var isLiked by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(100) }
    var isVolumeSliderVisible by remember { mutableStateOf(false) }

    // State for the bottom sheet content (Up Next vs. Lyrics)
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Up Next", "Lyrics")

    // State for controlling the ModalBottomSheet
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    val queue by queueManager.queue.collectAsState()

    // This polling loop fetches track state and time
    LaunchedEffect(Unit) {
        // Fetch initial volume
        withContext(Dispatchers.IO) {
            repo.getVolume()?.let {
                volume = it
            }
        }
        delay(250)
        while (true) {
            val newTrack = repo.getCurrentTrack()
            if (isLoading && newTrack != null) {
                isLoading = false
            }
            if (newTrack?.get("uri")?.jsonPrimitive?.content != currentTrack?.get("uri")?.jsonPrimitive?.content) {
                currentTrack = newTrack
            }
            isPlaying = repo.getPlaybackState() == "playing"
            if (!isSeeking) {
                positionMs = repo.getTimePosition()
            }
            durationMs = currentTrack?.get("length")?.jsonPrimitive?.intOrNull ?: 0
            delay(250)
        }
    }

    // This effect fetches artwork and like status when the track changes
    LaunchedEffect(currentTrack) {
        artworkUrl = null
        isLiked = false
        val track = currentTrack ?: return@LaunchedEffect
        val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull
        if (!trackUri.isNullOrEmpty()) {
            isLiked = withContext(Dispatchers.IO) { repo.isTrackLiked(trackUri) }
            withContext(Dispatchers.IO) {
                artworkUrl = repo.findArtwork(track)
            }
        }
    }

    // Refreshes the queue when playback starts or the track changes
    LaunchedEffect(isPlaying, currentTrack) {
        if (isPlaying) {
            val mopidyTracklist = withContext(Dispatchers.IO) {
                val result = repo.rpc.call("core.tracklist.get_tl_tracks")
                result?.jsonArray?.mapNotNull { it.jsonObject["track"]?.jsonObject } ?: emptyList()
            }
            queueManager.setQueue(mopidyTracklist, currentTrack?.get("uri")?.jsonPrimitive?.contentOrNull)
        }
    }

    // The main player UI
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Floating back arrow
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Filled.KeyboardArrowDown, "Back", modifier = Modifier.size(32.dp))
            }
        }

        // Album Art and the rest of the player UI
        val track = currentTrack
        if (isLoading || track == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isLoading) CircularProgressIndicator() else Text("Nothing is playing")
            }
        } else {
            val title = track["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
            val artist = track["artists"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
            val albumName = track["album"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
            val artUrl = artworkUrl

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            when {
                                // Swiping right to left (next song)
                                dragAmount < -50 -> {
                                    scope.launch { repo.next() }
                                }
                                // Swiping left to right (previous song)
                                dragAmount > 50 -> {
                                    scope.launch { repo.previous() }
                                }
                            }
                        }
                    }
            ) {
                key(artUrl) {
                    if (artUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(artUrl).crossfade(true).build(),
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, "No Image", modifier = Modifier.size(64.dp), tint = Color.Gray)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            Text(artist, color = Color.Gray, textAlign = TextAlign.Center)
            if (albumName.isNotEmpty()) {
                Text(albumName, color = Color.Gray, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(30.dp))
            val sliderValue = if (isSeeking) pendingSeekPosition else (positionMs ?: 0).toFloat()
            Slider(
                value = sliderValue,
                onValueChange = {
                    isSeeking = true
                    pendingSeekPosition = it
                },
                onValueChangeFinished = {
                    isSeeking = false
                    scope.launch { repo.seek(pendingSeekPosition.toInt()) }
                },
                valueRange = 0f..durationMs.toFloat(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(sliderValue.toInt()))
                Text(formatMs(durationMs))
            }
            Spacer(Modifier.height(30.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Dislike action */ }) { Icon(Icons.Outlined.ThumbDown, "Dislike") }
                IconButton(onClick = { scope.launch { repo.previous() } }) { Icon(Icons.Filled.SkipPrevious, "Previous", modifier = Modifier.size(36.dp)) }
                IconButton(onClick = { scope.launch { if (isPlaying) repo.pause() else repo.play() } }) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", modifier = Modifier.size(48.dp)) }
                IconButton(onClick = { scope.launch { repo.next() } }) { Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(36.dp)) }
                IconButton(onClick = {
                    scope.launch {
                        val trackUri = currentTrack?.get("uri")?.jsonPrimitive?.contentOrNull
                        if (!trackUri.isNullOrEmpty()) {
                            isLiked = repo.toggleLike(trackUri)
                        }
                    }
                }) { Icon(imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Like", tint = if (isLiked) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
            }

            Spacer(Modifier.height(20.dp))

            // Volume Control UI
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { isVolumeSliderVisible = !isVolumeSliderVisible }) {
                    Icon(
                        when {
                            volume > 50 -> Icons.Filled.VolumeUp
                            volume > 0 -> Icons.Filled.VolumeDown
                            else -> Icons.Filled.VolumeMute
                        },
                        contentDescription = "Volume"
                    )
                }
                if (isVolumeSliderVisible) {
                    Slider(
                        value = volume.toFloat(),
                        onValueChange = { newVolume -> volume = newVolume.toInt() },
                        onValueChangeFinished = {
                            scope.launch {
                                repo.setVolume(volume)
                            }
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Button to trigger the bottom sheet
            Button(onClick = { showBottomSheet = true }) {
                Icon(Icons.Outlined.PlaylistPlay, contentDescription = "Up Next", modifier = Modifier.padding(end = 8.dp))
                Text("Up Next")
            }

            Spacer(Modifier.height(20.dp)) // Add some space at the very bottom
        }
    }

    // The Modal Bottom Sheet for "Up Next" and "Lyrics"
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(text = title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
                // Content based on selected tab
                when (selectedTab) {
                    0 -> UpNextQueue(
                        queue = queue,
                        currentTrack = currentTrack,
                        onTrackClick = { clickedUri ->
                            scope.launch {
                                repo.playTracks(queue, clickedUri)
                                // Optionally hide the sheet after a selection
                                // sheetState.hide()
                                // showBottomSheet = false
                            }
                        },
                        repo = repo
                    )
                    1 -> LyricsScreen(currentTrack = currentTrack, repo = repo)
                }
            }
        }
    }
}

@Composable
fun UpNextQueueItem(
    trackInQueue: JsonObject,
    isCurrent: Boolean,
    repo: MopidyRepository,
    onClick: () -> Unit
) {
    var artworkUrl by remember { mutableStateOf<String?>(null) }
    val trackName = trackInQueue["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
    val artistName = trackInQueue["artists"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"

    // Fetch artwork for each track individually
    LaunchedEffect(trackInQueue) {
        artworkUrl = withContext(Dispatchers.IO) {
            repo.findArtwork(trackInQueue)
        }
    }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = trackName,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
        },
        supportingContent = { Text(artistName) },
        leadingContent = {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUrl)
                    .crossfade(true)
//                    .placeholder(R.drawable.ic_album) // Optional: Add a placeholder drawable
//                    .error(R.drawable.ic_album)       // Optional: Add an error drawable
                    .build(),
                contentDescription = "Album art for $trackName",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
            )
        }
    )
}

@Composable
fun UpNextQueue(
    queue: List<JsonObject>,
    currentTrack: JsonObject?,
    repo: MopidyRepository,
    onTrackClick: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
        items(queue) { trackInQueue ->
            val isPlayingNow = trackInQueue["uri"]?.jsonPrimitive?.content == currentTrack?.get("uri")?.jsonPrimitive?.content
            val clickedUri = trackInQueue["uri"]!!.jsonPrimitive.content

            UpNextQueueItem(
                trackInQueue = trackInQueue,
                isCurrent = isPlayingNow,
                repo = repo,
                onClick = {
                    onTrackClick(clickedUri)
                    Log.d("PlayerScreen", "Clicked on track: $clickedUri")
                }
            )
        }
    }
}

@Composable
fun LyricsScreen(
    currentTrack: JsonObject?,
    repo: MopidyRepository
) {
    var lyrics by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(currentTrack) {
        val trackUri = currentTrack?.get("uri")?.jsonPrimitive?.contentOrNull
        if (trackUri != null) {
            isLoading = true
//            lyrics = try {
//                repo.getLyrics(trackUri)
//            } finally {
//                isLoading = false
//            }
        } else {
            lyrics = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> CircularProgressIndicator()
            !lyrics.isNullOrEmpty() -> {
                Text(
                    text = lyrics!!,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                )
            }
            else -> Text("No lyrics found for this track.")
        }
    }
}

fun formatMs(ms: Int): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

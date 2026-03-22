package com.nil.mopitube.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.nil.mopitube.data.UserPreferencesRepository
import com.nil.mopitube.mopidy.MopidyClient
import com.nil.mopitube.mopidy.MopidyRepository
import com.nil.mopitube.mopidy.TrackRepeatMode
import com.nil.mopitube.mopidy.uploadArtworkToServer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    client: MopidyClient,
    pendingSeekMs: Int = 0,
    onSeekConsumed: () -> Unit = {},
    onBack: () -> Unit
) {

    val repo = client.repo
    if (repo == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Error: Not connected")
        }
        // Early return to prevent the rest of the composable from running with a null repo.
        return
    }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val userPrefs = remember { UserPreferencesRepository(context) }
    val serverHost by userPrefs.serverHost.collectAsState(initial = "")

    var queue by remember { mutableStateOf<List<Pair<Int, JsonObject>>>(emptyList()) }
    var tracklistLength by remember { mutableStateOf(0) }
    var currentPosition by remember { mutableStateOf(-1) }
    var artworkCache by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var isRemovalInProgress by remember { mutableStateOf(false) }
    var isAppendingTracks by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTrack by remember { mutableStateOf<JsonObject?>(null) }
    var artworkUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingArtwork by remember { mutableStateOf(false) }

    // Photo picker for artwork upload — declared after currentTrack/artworkUrl so the lambda can capture them
    val artworkPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        Log.d("ArtworkUpload", "Picker returned uri=$uri currentTrack=$currentTrack serverHost=$serverHost")
        if (uri == null) { Log.w("ArtworkUpload", "uri is null — user cancelled"); return@rememberLauncherForActivityResult }
        val trackUri = currentTrack?.get("uri")?.jsonPrimitive?.contentOrNull
        if (trackUri.isNullOrEmpty()) { Log.e("ArtworkUpload", "trackUri is null/empty"); return@rememberLauncherForActivityResult }
        if (serverHost.isBlank()) { Log.e("ArtworkUpload", "serverHost is blank"); return@rememberLauncherForActivityResult }
        scope.launch {
            Log.d("ArtworkUpload", "Uploading artwork for trackUri=$trackUri")
            isUploadingArtwork = true
            try {
                val ok = uploadArtworkToServer(serverHost, trackUri, uri, context)
                if (ok) {
                    val albumUri = currentTrack?.get("album")?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
                        ?: withContext(Dispatchers.IO) { repo.getAlbumUriForTrack(trackUri) }
                    // Bypass Mopidy re-indexing: serve the image directly from the scan server.
                    // mopidy-local can't find folder artwork for tracks with no album tag via get_images.
                    val encodedUri = java.net.URLEncoder.encode(trackUri, "UTF-8")
                    val coverUrl = "http://$serverHost:9000/cover?track_uri=$encodedUri"
                    val oldUrl = artworkUrl
                    @OptIn(coil.annotation.ExperimentalCoilApi::class)
                    oldUrl?.let { url ->
                        context.imageLoader.memoryCache?.remove(MemoryCache.Key(url))
                        context.imageLoader.diskCache?.remove(url)
                    }
                    withContext(Dispatchers.IO) { repo.setCachedArtwork(trackUri, albumUri, coverUrl) }
                    artworkUrl = coverUrl
                    snackbarHostState.showSnackbar("Artwork updated!")
                } else {
                    snackbarHostState.showSnackbar("Upload failed — check server")
                }
            } finally {
                isUploadingArtwork = false
            }
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0) }
    var positionMs: Int? by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var pendingSeekPosition by remember { mutableStateOf(0f) }
    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }
    var isInListenLater by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(100) }
    var isVolumeSliderVisible by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(TrackRepeatMode.OFF) }

    // State for the bottom sheet content (Up Next vs. Lyrics)
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Up Next", "Lyrics")

    // State for controlling the ModalBottomSheet
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    // This polling loop fetches track state and time
    LaunchedEffect(repo) {
        // Fetch initial volume and repeat mode (one-time, before lifecycle-aware loop)
        withContext(Dispatchers.IO) {
            repo.getVolume()?.let { volume = it }
            repeatMode = repo.getRepeatMode()
        }
        delay(250)
        // Only poll when the app is in the foreground
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
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

                // Update playback state flows for PlaybackService notification
                withContext(Dispatchers.IO) {
                    repo.updatePlaybackState()
                }

                delay(250)
            }
        }
    }

    // Queue monitoring and auto-append — only when foregrounded
    LaunchedEffect(repo) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                withContext(Dispatchers.IO) {
                    try {
                        // Fetch current tracklist from Mopidy with tlid
                        val tracklistTracksWithTlid = repo.getTracklistTracksWithTlid()
                        queue = tracklistTracksWithTlid
                        tracklistLength = tracklistTracksWithTlid.size

                        // Batch prefetch artwork for all unique albums
                        val uniqueAlbumUris = tracklistTracksWithTlid
                            .map { it.second } // Extract track objects
                            .mapNotNull { it["album"]?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull }
                            .distinct()

                        val newArtworkCache = mutableMapOf<String, String?>()
                        uniqueAlbumUris.forEach { albumUri ->
                            // Reuse existing cache or fetch new
                            newArtworkCache[albumUri] = artworkCache[albumUri]
                                ?: repo.findArtwork(tracklistTracksWithTlid.first {
                                    it.second["album"]?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull == albumUri
                                }.second)
                        }
                        artworkCache = newArtworkCache

                        // Find current track position by URI matching
                        val currentUri = currentTrack?.get("uri")?.jsonPrimitive?.contentOrNull
                        if (currentUri != null) {
                            currentPosition = tracklistTracksWithTlid.indexOfFirst {
                                it.second["uri"]?.jsonPrimitive?.contentOrNull == currentUri
                            }

                            // Auto-append when reaching last 3 tracks (with queue cap at 100 items)
                            if (currentPosition != -1 && tracklistLength > 0 && !isAppendingTracks) {
                                val remaining = tracklistLength - currentPosition - 1
                                // Only append if queue is low AND below the 100-item cap
                                if (remaining <= 3 && tracklistLength < 100) {
                                    isAppendingTracks = true
                                    try {
                                        Log.d("PlayerScreen", "Queue low ($remaining remaining, $tracklistLength total). Appending more tracks")

                                        // Get existing URIs to filter duplicates
                                        val existingUris = tracklistTracksWithTlid
                                            .map { it.second["uri"]?.jsonPrimitive?.contentOrNull }
                                            .filterNotNull()
                                            .toSet()

                                        // Calculate how many tracks to append (don't exceed cap)
                                        val maxToAppend = (100 - tracklistLength).coerceAtMost(20)
                                        val appendedCount = repo.appendSimilarTracksToQueue(maxToAppend, existingUris, currentTrack)
                                        Log.d("PlayerScreen", "Appended $appendedCount new tracks (queue now at ${tracklistLength + appendedCount})")
                                    } finally {
                                        isAppendingTracks = false
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerScreen", "Queue monitoring error", e)
                    }
                }
                delay(1000) // Poll every 1 second
            }
        }
    }

    // This effect fetches artwork, like, and dislike status when the track changes
    LaunchedEffect(currentTrack, repo) {
        artworkUrl = null
        isLiked = false
        isDisliked = false
        isInListenLater = false
        val track = currentTrack ?: return@LaunchedEffect
        val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull
        if (!trackUri.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                isLiked = repo.isTrackLiked(trackUri)
                isDisliked = repo.isTrackDisliked(trackUri)
                isInListenLater = repo.isInListenLater(trackUri)
                artworkUrl = repo.findArtwork(track)
            }
        }
    }

    // Seek to resume position after playback starts
    LaunchedEffect(isPlaying, pendingSeekMs) {
        if (isPlaying && pendingSeekMs > 0) {
            withContext(Dispatchers.IO) { repo.seek(pendingSeekMs) }
            onSeekConsumed()
        }
    }

    // Handle queue item removal
    val handleRemoveTrack: (Int) -> Unit = { tlid ->
        scope.launch {
            if (isRemovalInProgress) return@launch
            isRemovalInProgress = true

            try {
                withContext(Dispatchers.IO) {
                    // Remove track from Mopidy tracklist
                    val success = repo.removeTrackFromTracklist(tlid)

                    if (success) {
                        // Auto-refill with 10 new tracks
                        val existingUris = queue
                            .map { it.second["uri"]?.jsonPrimitive?.contentOrNull }
                            .filterNotNull()
                            .toSet()

                        val appendedCount = repo.appendRandomTracksToQueue(10, existingUris)
                        Log.d("PlayerScreen", "Removed track $tlid, appended $appendedCount tracks")
                    } else {
                        withContext(Dispatchers.Main) {
                            snackbarHostState.showSnackbar("Failed to remove track")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerScreen", "Error removing track", e)
                snackbarHostState.showSnackbar("Failed to remove track")
            } finally {
                isRemovalInProgress = false
            }
        }
    }

    // The main player UI
    Box {
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

            @OptIn(ExperimentalFoundationApi::class)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            artworkPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            when {
                                dragAmount < -50 -> scope.launch { repo.next() }
                                dragAmount > 50 -> scope.launch { repo.previous() }
                            }
                        }
                    }
            ) {
                key(artUrl) {
                    if (artUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(artUrl).crossfade(true).build(),
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
                // Upload progress overlay
                if (isUploadingArtwork) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Uploading artwork…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                // Edit hint overlay — always visible as a subtle cue
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Change artwork",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
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
                IconButton(onClick = {
                    scope.launch {
                        val next = when (repeatMode) {
                            TrackRepeatMode.OFF -> TrackRepeatMode.ALL
                            TrackRepeatMode.ALL -> TrackRepeatMode.ONE
                            TrackRepeatMode.ONE -> TrackRepeatMode.OFF
                        }
                        repeatMode = next
                        withContext(Dispatchers.IO) { repo.setRepeatMode(next) }
                    }
                }) {
                    Icon(
                        imageVector = if (repeatMode == TrackRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode == TrackRepeatMode.OFF) LocalContentColor.current.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { scope.launch { repo.previous() } }) { Icon(Icons.Filled.SkipPrevious, "Previous", modifier = Modifier.size(36.dp)) }
                IconButton(onClick = {
                    scope.launch {
                        if (isPlaying) {
                            repo.pause()
                            // Auto-update Listen Later position on every pause
                            val track = currentTrack
                            val pos = positionMs ?: 0
                            if (isInListenLater && track != null) {
                                withContext(Dispatchers.IO) { repo.saveForLater(track, pos) }
                            }
                        } else {
                            repo.play()
                        }
                    }
                }) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", modifier = Modifier.size(48.dp)) }
                IconButton(onClick = { scope.launch { repo.next() } }) { Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(36.dp)) }
                IconButton(onClick = {
                    scope.launch {
                        val trackUri = currentTrack?.get("uri")?.jsonPrimitive?.contentOrNull
                        if (!trackUri.isNullOrEmpty()) {
                            isLiked = withContext(Dispatchers.IO) { repo.toggleLike(trackUri) }
                            if (isLiked) isDisliked = false
                        }
                    }
                }) { Icon(imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Like", tint = if (isLiked) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                IconButton(onClick = {
                    scope.launch {
                        val trackUri = currentTrack?.get("uri")?.jsonPrimitive?.contentOrNull
                        if (!trackUri.isNullOrEmpty()) {
                            isDisliked = withContext(Dispatchers.IO) { repo.toggleDislike(trackUri) }
                            if (isDisliked) {
                                isLiked = false
                                repo.next()
                            }
                        }
                    }
                }) {
                    Icon(
                        imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Dislike",
                        tint = if (isDisliked) MaterialTheme.colorScheme.error else LocalContentColor.current
                    )
                }
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { showBottomSheet = true }) {
                    Icon(Icons.Outlined.PlaylistPlay, contentDescription = "Up Next", modifier = Modifier.padding(end = 8.dp))
                    Text("Up Next")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        val track = currentTrack ?: return@launch
                        val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull ?: return@launch
                        val pos = positionMs ?: 0
                        withContext(Dispatchers.IO) {
                            if (isInListenLater) {
                                repo.removeFromListenLater(trackUri)
                            } else {
                                repo.saveForLater(track, pos)
                                repo.pause()
                            }
                            isInListenLater = !isInListenLater
                        }
                        val msg = if (isInListenLater) "Saved for later at ${formatMs(pos)}" else "Removed from Listen Later"
                        snackbarHostState.showSnackbar(msg)
                    }
                }) {
                    Icon(
                        imageVector = if (isInListenLater) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
                        contentDescription = "Save for Later",
                        tint = if (isInListenLater) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(if (isInListenLater) "Saved" else "Later")
                }
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
                        artworkCache = artworkCache,
                        repo = repo,
                        isRemovalInProgress = isRemovalInProgress,
                        onRemove = handleRemoveTrack,
                        onTrackClick = { clickedUri ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    repo.playTrackFromTracklist(clickedUri)
                                }
                                // Optionally hide the sheet after a selection
//                                 sheetState.hide()
//                                 showBottomSheet = false
                            }
                        }
                    )
                    1 -> LyricsScreen(currentTrack = currentTrack, repo = repo)
                }
            }
        }
    }

        // SnackbarHost for error messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun UpNextQueueItem(
    tlid: Int,
    track: JsonObject,
    isCurrent: Boolean,
    artworkUrl: String?,
    onRemove: (Int) -> Unit,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 150.dp.toPx() }

    var offsetX by remember { mutableStateOf(0f) }
    var isSwipeTriggered by remember { mutableStateOf(false) }

    val trackName = track["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
    val artistName = track["artists"]?.jsonArray?.firstOrNull()
        ?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"

    // Pulse animation for play icon (only when current track is playing)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Spring-back animation when swipe cancelled
    LaunchedEffect(isSwipeTriggered) {
        if (!isSwipeTriggered && offsetX != 0f) {
            animate(
                initialValue = offsetX,
                targetValue = 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) { value, _ -> offsetX = value }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (abs(offsetX) > swipeThresholdPx / 2) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                } else {
                    Color.Transparent
                }
            )
    ) {
        // Delete icon background
        if (abs(offsetX) > 50f) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(if (offsetX > 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 32.dp)
                    .alpha((abs(offsetX) / swipeThresholdPx).coerceIn(0f, 1f))
            )
        }

        ListItem(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(offsetX) > swipeThresholdPx) {
                                isSwipeTriggered = true
                                onRemove(tlid)
                            } else {
                                isSwipeTriggered = false
                            }
                        },
                        onDragCancel = {
                            isSwipeTriggered = false
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX += dragAmount
                        }
                    )
                }
                .clickable(onClick = onClick),
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Now Playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    // Subtle pulse animation using rememberInfiniteTransition
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = trackName,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified
                    )
                }
            },
            supportingContent = { Text(artistName) },
            leadingContent = {
                Box {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artworkUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.small)
                    )
                    // Visual indicator border for current track
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun UpNextQueue(
    queue: List<Pair<Int, JsonObject>>,
    currentTrack: JsonObject?,
    artworkCache: Map<String, String?>,
    repo: MopidyRepository,
    isRemovalInProgress: Boolean,
    onRemove: (Int) -> Unit,
    onTrackClick: (String) -> Unit
) {
    if (queue.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Queue is empty",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val listState = rememberLazyListState()
        val currentUri = currentTrack?.get("uri")?.jsonPrimitive?.contentOrNull

        // Auto-scroll to currently playing track only when the current track changes
        // Do NOT depend on queue to avoid interrupting manual scrolling
        LaunchedEffect(currentUri) {
            if (currentUri != null) {
                val currentIndex = queue.indexOfFirst { (_, track) ->
                    track["uri"]?.jsonPrimitive?.contentOrNull == currentUri
                }
                if (currentIndex >= 0) {
                    // Scroll to position with some offset to center it
                    listState.animateScrollToItem(
                        index = currentIndex,
                        scrollOffset = -100 // Small offset to show item near top but not at edge
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            items(
                items = queue,
                key = { it.first } // Use tlid as key for stable identity
            ) { (tlid, track) ->
                val trackUri = track["uri"]?.jsonPrimitive?.contentOrNull
                val currentUri = currentTrack?.get("uri")?.jsonPrimitive?.contentOrNull
                val isPlayingNow = trackUri == currentUri

                // Get artwork from cache
                val albumUri = track["album"]?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
                val artwork = albumUri?.let { artworkCache[it] }

                UpNextQueueItem(
                    tlid = tlid,
                    track = track,
                    isCurrent = isPlayingNow,
                    artworkUrl = artwork,
                    onRemove = if (isRemovalInProgress) { _ -> } else onRemove,
                    onClick = {
                        trackUri?.let {
                            onTrackClick(it)
                            Log.d("PlayerScreen", "Clicked on track: $it")
                        }
                    }
                )
            }
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


// Helper function remains the same
fun formatMs(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

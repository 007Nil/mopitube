package com.nil.mopitube.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.nil.mopitube.mopidy.ConnectionState
import com.nil.mopitube.mopidy.MopidyClient
import com.nil.mopitube.ui.components.AppDrawer
import com.nil.mopitube.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// --- Data class for our Bottom Navigation items ---
data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav() {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var client by remember { mutableStateOf<MopidyClient?>(null) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // --- START: LIFECYCLE-AWARE CONNECTION MANAGEMENT ---
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (client == null) {
                        Log.d("AppNavLifecycle", "ON_START: Creating and connecting new MopidyClient.")
                        client = MopidyClient(context)
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    Log.d("AppNavLifecycle", "ON_STOP: Shutting down MopidyClient.")
                    client?.shutdown() // Ensure shutdown() exists on MopidyClient
                    client = null
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            Log.d("AppNavLifecycle", "ON_DISPOSE: Final shutdown.")
            client?.shutdown()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    // --- END: LIFECYCLE-AWARE CONNECTION MANAGEMENT ---

    // Define the items for the Bottom Navigation Bar
    val bottomNavItems = listOf(
        BottomNavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem("search", "Search", Icons.Filled.Search, Icons.Outlined.Search),
        BottomNavItem("liked_songs", "Library", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder)
    )

    // This effect handles the initial navigation after connection
    LaunchedEffect(client) {
        client?.let { currentClient ->
            currentClient.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    scope.launch {
                        if (navController.currentDestination?.route == "startup") {
                            navController.navigate("home") {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== MAJOR FIX AREA =====
    // If the client is null, we show a loading indicator and prevent the rest of the UI from composing.
    // This is the main fix for all the nullability errors.
    if (client == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        // From this point on, we can guarantee that 'client' is not null.
        val nonNullClient = client!!

        // The root of our UI is the ModalNavigationDrawer, enabling the left swipe
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawer(
                    currentRoute = currentRoute ?: "home",
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    closeDrawer = { scope.launch { drawerState.close() } }
                )
            }
        ) {
            Scaffold(
                topBar = {
                    val isTopLevelDestination = bottomNavItems.any { it.route == currentRoute }
                    if (currentRoute != "startup" && currentRoute != "player") {
                        CenterAlignedTopAppBar(
                            title = {
                                val title = when {
                                    currentRoute == "home" -> "Home"
                                    currentRoute == "search" -> "Search"
                                    currentRoute == "liked_songs" -> "Liked Songs"
                                    currentRoute == "settings" -> "Settings"
                                    currentRoute == "songs" -> "Songs"
                                    currentRoute == "albums" -> "Albums"
                                    currentRoute == "artists" -> "Artists"
                                    currentRoute?.startsWith("playlist") == true -> "Playlist"
                                    currentRoute?.startsWith("album") == true -> "Album"
                                    else -> ""
                                }
                                Text(title)
                            },
                            navigationIcon = {
                                if (!isTopLevelDestination) {
                                    IconButton(onClick = { navController.navigateUp() }) {
                                        Icon(Icons.Filled.ArrowBack, "Back")
                                    }
                                } else {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, "Open Menu")
                                    }
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    if (currentRoute != "startup" && currentRoute != "player") {
                        Column {
                            MiniPlayer(repo = nonNullClient.repo, onPlayerClick = { navController.navigate("player") })

                            val isTopLevelDestination = bottomNavItems.any { it.route == currentRoute }
                            if (isTopLevelDestination) {
                                NavigationBar {
                                    bottomNavItems.forEach { item ->
                                        NavigationBarItem(
                                            selected = currentRoute == item.route,
                                            onClick = {
                                                navController.navigate(item.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            icon = { Icon(if (currentRoute == item.route) item.selectedIcon else item.unselectedIcon, item.label) },
                                            label = { Text(item.label) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "startup",
                    modifier = Modifier.padding(innerPadding)
                ) {
                    val onTrackClick: (String) -> Unit = { trackUri ->
                        scope.launch {
                            try {
                                nonNullClient.repo.clearTracklist()
                                nonNullClient.repo.addTrackToTracklist(trackUri)
                                nonNullClient.repo.play()
                                withContext(Dispatchers.IO) { nonNullClient.repo.logTrackPlay(trackUri) }
                                navController.navigate("player")
                            } catch (e: Exception) {
                                Log.e("AppNav", "Failed to play track", e)
                            }
                        }
                    }
                    val onPlayerClick: () -> Unit = { navController.navigate("player") }

                    composable("startup") { StartupScreen(nonNullClient) }

                    composable("home") {
                        HomeScreen(
                            repo = nonNullClient.repo,
                            onPlaylistClick = { uri -> navController.navigate("playlist/${URLEncoder.encode(uri, "UTF-8")}") },
                            onAlbumClick = { uri -> navController.navigate("album/${URLEncoder.encode(uri, "UTF-8")}") },
                            onTrackClick = onTrackClick,
                            onPlayerClick = onPlayerClick,
                            onLikedSongsClick = { navController.navigate("liked_songs") },
                            onSongsClick = { navController.navigate("songs") },
                            onAlbumsClick = { navController.navigate("albums") },
                            onArtistsClick = { navController.navigate("artists") }
                        )
                    }

                    composable("search") {
                        SearchScreen(
                            repo = nonNullClient.repo,
                            onAlbumClick = { uri -> navController.navigate("album/${URLEncoder.encode(uri, "UTF-8")}") },
                            onTrackClick = onTrackClick,
                        )
                    }

                    composable("liked_songs") {
                        LikedSongsScreen(
                            repo = nonNullClient.repo,
                            onTrackClick = onTrackClick,
                            onPlayerClick = onPlayerClick
                        )
                    }

                    composable("settings") { SettingsScreen() }

                    composable("songs") {
                        SongsScreen(
                            repo = nonNullClient.repo,
                            onTrackClick = onTrackClick
                        )
                    }

                    composable("albums") {
                        AlbumsScreen(
                            repo = nonNullClient.repo,
                            onAlbumClick = { uri -> navController.navigate("album/${URLEncoder.encode(uri, "UTF-8")}") }
                        )
                    }

                    composable("artists") {
                        ArtistsScreen(
                            repo = nonNullClient.repo,
                            onArtistClick = { uri -> Log.d("AppNav", "Artist clicked: $uri. Navigation not implemented yet.") }
                        )
                    }

                    composable("playlist/{playlistUri}", arguments = listOf(navArgument("playlistUri") { type = NavType.StringType })) {
                        val uri = it.arguments?.getString("playlistUri")?.let { URLDecoder.decode(it, "UTF-8") }
                        if (uri != null) {
                            PlaylistScreen(
                                repo = nonNullClient.repo,
                                playlistUri = uri,
                                onTrackClick = onTrackClick,
                                onPlayerClick = onPlayerClick
                            )
                        }
                    }

                    composable("album/{albumUri}", arguments = listOf(navArgument("albumUri") { type = NavType.StringType })) {
                        val uri = it.arguments?.getString("albumUri")?.let { URLDecoder.decode(it, "UTF-8") }
                        if (uri != null) {
                            AlbumScreen(
                                repo = nonNullClient.repo,
                                albumUri = uri,
                                onTrackClick = onTrackClick,
                                onPlayerClick = onPlayerClick
                            )
                        }
                    }

                    composable("player") {
                        PlayerScreen(
                            client = nonNullClient,
                            onBack = { navController.navigateUp() }
                        )
                    }
                }
            }
        }
    }
}

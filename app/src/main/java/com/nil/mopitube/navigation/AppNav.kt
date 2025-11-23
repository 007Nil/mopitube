package com.nil.mopitube.navigation

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
    val client = remember { MopidyClient(context) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Define the items for the Bottom Navigation Bar
    val bottomNavItems = listOf(
        BottomNavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem("search", "Search", Icons.Filled.Search, Icons.Outlined.Search),
        BottomNavItem("liked_songs", "Library", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder)
    )

    // This effect handles the initial navigation after connection
    LaunchedEffect(Unit) {
        client.connectionState.collect { state ->
            if (state is ConnectionState.Connected) {
                scope.launch {
                    navController.navigate("home") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            }
        }
    }

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
        // Inside the drawer, we place the Scaffold
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
                                currentRoute?.startsWith("playlist") == true -> "Playlist"
                                currentRoute?.startsWith("album") == true -> "Album"
                                else -> ""
                            }
                            Text(title)
                        },
                        navigationIcon = {
                            if (!isTopLevelDestination) {
                                // Show back arrow on detail screens
                                IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(Icons.Filled.ArrowBack, "Back")
                                }
                            } else {
                                // Show menu icon on top-level screens to open the drawer
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, "Open Menu")
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                // Do not show any bottom bar on the startup or full player screens
                if (currentRoute != "startup" && currentRoute != "player") {
                    Column {
                        // The MiniPlayer is ALWAYS shown on every other screen
                        MiniPlayer(repo = client.repo, onPlayerClick = { navController.navigate("player") })

                        // The main NavigationBar is ONLY shown on the top-level destinations
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
                // Define single, reusable callbacks
                val onTrackClick: (String) -> Unit = { trackUri ->
                    scope.launch {
                        try {
                            client.repo.clearTracklist()
                            client.repo.addTrackToTracklist(trackUri)
                            client.repo.play()
                            withContext(Dispatchers.IO) { client.repo.logTrackPlay(trackUri) }
                            navController.navigate("player")
                        } catch (e: Exception) {
                            Log.e("AppNav", "Failed to play track", e)
                        }
                    }
                }
                val onPlayerClick: () -> Unit = { navController.navigate("player") }

                composable("startup") { StartupScreen(client) }

                composable("home") {
                    HomeScreen(
                        repo = client.repo,
                        onPlaylistClick = { uri -> navController.navigate("playlist/${URLEncoder.encode(uri, "UTF-8")}") },
                        onAlbumClick = { uri -> navController.navigate("album/${URLEncoder.encode(uri, "UTF-8")}") },
                        onTrackClick = onTrackClick,
                        onPlayerClick = onPlayerClick,
                        onLikedSongsClick = { navController.navigate("liked_songs") }
                    )
                }

                composable("search") {
                    SearchScreen(
                        repo = client.repo,
                        onAlbumClick = { uri -> navController.navigate("album/${URLEncoder.encode(uri, "UTF-8")}") },
                        onTrackClick = onTrackClick,
//                        onPlayerClick = onPlayerClick
                    )
                }

                composable("liked_songs") {
                    LikedSongsScreen(
                        repo = client.repo,
                        onTrackClick = onTrackClick,
                        onPlayerClick = onPlayerClick
                    )
                }

                composable("settings") { SettingsScreen() }

                composable("playlist/{playlistUri}", arguments = listOf(navArgument("playlistUri") { type = NavType.StringType })) {
                    val uri = it.arguments?.getString("playlistUri")?.let { URLDecoder.decode(it, "UTF-8") }
                    if (uri != null) {
                        PlaylistScreen(
                            repo = client.repo,
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
                            repo = client.repo,
                            albumUri = uri,
                            onTrackClick = onTrackClick,
                            onPlayerClick = onPlayerClick
                        )
                    }
                }

                composable("player") {
                    PlayerScreen(
                        client = client, // Pass the whole client for repo and queueManager
                        onBack = { navController.navigateUp() }
                    )
                }
            }
        }
    }
}

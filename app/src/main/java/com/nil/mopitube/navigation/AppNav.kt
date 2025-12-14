package com.nil.mopitube.navigation

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nil.mopitube.ui.components.AppDrawer
import com.nil.mopitube.ui.screens.*
import com.nil.mopitube.ui.screens.settings.ClientSettingsScreen
import com.nil.mopitube.ui.screens.settings.ServerSettingsScreen
import com.nil.mopitube.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(
    appNavViewModel: AppNavViewModel = viewModel()
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val client = appNavViewModel.client

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        BottomNavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem("search", "Search", Icons.Filled.Search, Icons.Outlined.Search),
        BottomNavItem("liked_songs", "Library", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder)
    )

    // --- DEFINITIVE FIX FOR NAVIGATION ---
    // This logic now correctly waits for the repository to be ready and uses a
    // flag in the ViewModel to ensure navigation happens exactly once.
    LaunchedEffect(client.repo) {
        if (client.repo != null && !appNavViewModel.hasNavigatedFromStartup) {
            // ...it is now SAFE to navigate because the "home" route always exists in the graph below.
            navController.navigate("home") {
                // Pop up to the start destination to clear the startup screen from the back stack.
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
            }
            // CRITICAL: Mark that we have performed the initial navigation.
            appNavViewModel.hasNavigatedFromStartup = true
        }
    }

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
                                currentRoute == "server_settings" -> "Server Settings"
                                currentRoute == "client_settings" -> "Client Settings"
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
                            val isSettingsScreen = currentRoute == "settings" ||
                                    currentRoute == "server_settings" ||
                                    currentRoute == "client_settings"
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
                // Only show the bottom bar if the repo is available (client is connected)
                if (currentRoute != "startup" && currentRoute != "player" && client.repo != null) {
                    Column {
                        // The '!!' is safe here because of the outer if-check.
                        MiniPlayer(
                            repo = client.repo!!,
                            onPlayerClick = { navController.navigate("player") })

                        val isTopLevelDestination = bottomNavItems.any { it.route == currentRoute }
//                        if (isTopLevelDestination) {
                            NavigationBar {
                                bottomNavItems.forEach { item ->
                                    NavigationBarItem(
                                        selected = currentRoute == item.route,
                                        onClick = {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                if (currentRoute == item.route) item.selectedIcon else item.unselectedIcon,
                                                item.label
                                            )
                                        },
                                        label = { Text(item.label) }
                                    )
                                }
                            }
                        }
                    }
//                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "startup",
                modifier = Modifier.padding(innerPadding)
            ) {
                // --- Part 1: Routes that DO NOT need a connection ---
                composable("startup") {
                    StartupScreen(
                        client = client,
                        onNavigateToSettings = { navController.navigate("settings") }
                    )
                }
                composable("server_settings") {
                    ServerSettingsScreen(onNavigateBack = { navController.navigateUp() })
                }
                composable("client_settings") {
                    ClientSettingsScreen(onNavigateBack = { navController.navigateUp() })
                }
                composable("settings") {
                    SettingsScreen(
                        onNavigateToServerSettings = { navController.navigate("server_settings") },
                        onNavigateToClientSettings = { navController.navigate("client_settings") }
                    )
                }
                composable("player") {
                    PlayerScreen(
                        client = client,
                        onBack = { navController.navigateUp() })
                }


                // --- Part 2: Routes that REQUIRE a connection ---
                // These routes are now defined unconditionally, which solves the race condition.
                // We safely access the repo inside each one.
                val onTrackClick: (String) -> Unit = { trackUri ->
                    scope.launch {
                        client.repo?.let { repo ->
                            try {
                                repo.clearTracklist()
                                repo.addTrackToTracklist(trackUri)
                                repo.play()
                                withContext(Dispatchers.IO) { repo.logTrackPlay(trackUri) }
                                navController.navigate("player")
                            } catch (e: Exception) {
                                Log.e("AppNav", "Failed to play track", e)
                            }
                        }
                    }
                }
                val onPlayerClick: () -> Unit = { navController.navigate("player") }

                composable("home") {
                    // Access repo safely. The '!!' is safe because this screen is only
                    // reachable after the LaunchedEffect navigates here.
                    client.repo?.let { repo ->
                        HomeScreen(
                            repo = repo,
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
                }

                composable("search") {
                    client.repo?.let { repo ->
                        SearchScreen(
                            repo = repo,
                            onAlbumClick = { uri -> navController.navigate("album/${URLEncoder.encode(uri, "UTF-8")}") },
                            onTrackClick = onTrackClick
                        )
                    }
                }

                composable("liked_songs") {
                    client.repo?.let { repo ->
                        LikedSongsScreen(
                            repo = repo,
                            onTrackClick = onTrackClick,
                            onPlayerClick = onPlayerClick
                        )
                    }
                }

                composable("songs") {
                    client.repo?.let { repo ->
                        SongsScreen(repo = repo, onTrackClick = onTrackClick)
                    }
                }

                composable("albums") {
                    client.repo?.let { repo ->
                        AlbumsScreen(
                            repo = repo,
                            onAlbumClick = { uri -> navController.navigate("album/${URLEncoder.encode(uri, "UTF-8")}") }
                        )
                    }
                }

                composable("artists") {
                    client.repo?.let { repo ->
                        ArtistsScreen(
                            repo = repo,
                            onArtistClick = { uri -> Log.d("AppNav", "Artist clicked: $uri. Navigation not implemented yet.") }
                        )
                    }
                }

                composable(
                    "playlist/{playlistUri}",
                    arguments = listOf(navArgument("playlistUri") { type = NavType.StringType })
                ) { backStackEntry ->
                    client.repo?.let { repo ->
                        val uri = backStackEntry.arguments?.getString("playlistUri")?.let { URLDecoder.decode(it, "UTF-8") }
                        if (uri != null) {
                            PlaylistScreen(
                                repo = repo,
                                playlistUri = uri,
                                onTrackClick = onTrackClick,
                                onPlayerClick = onPlayerClick
                            )
                        }
                    }
                }

                composable(
                    "album/{albumUri}",
                    arguments = listOf(navArgument("albumUri") { type = NavType.StringType })
                ) { backStackEntry ->
                    client.repo?.let { repo ->
                        val uri = backStackEntry.arguments?.getString("albumUri")?.let { URLDecoder.decode(it, "UTF-8") }
                        if (uri != null) {
                            AlbumScreen(
                                repo = repo,
                                albumUri = uri,
                                onTrackClick = onTrackClick,
                                onPlayerClick = onPlayerClick
                            )
                        }
                    }
                }
            }
        }
    }
}

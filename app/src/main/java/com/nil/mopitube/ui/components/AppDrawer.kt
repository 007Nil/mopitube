package com.nil.mopitube.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class NavDrawerItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawer(
    currentRoute: String,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onNavigate: (String) -> Unit,
    closeDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavDrawerItem("home", "Home", Icons.Default.Home),
        NavDrawerItem("liked_songs", "Liked Songs", Icons.Default.Favorite),
        NavDrawerItem("disliked_songs", "Disliked Songs", Icons.Default.ThumbDown),
        NavDrawerItem("listen_later", "Listen Later", Icons.Default.Bookmark),
        NavDrawerItem("playlists", "Playlists", Icons.AutoMirrored.Filled.QueueMusic),
        NavDrawerItem("settings", "Settings", Icons.Default.Settings)
    )

    ModalDrawerSheet(modifier) {
        Spacer(Modifier.height(12.dp))
        items.forEach { item ->
            NavigationDrawerItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute == item.route,
                onClick = {
                    onNavigate(item.route)
                    closeDrawer()
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }

        Spacer(Modifier.weight(1f))
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = null
            )
            Spacer(Modifier.width(16.dp))
            Text("Dark mode", modifier = Modifier.weight(1f))
            Switch(checked = isDarkMode, onCheckedChange = { onToggleDarkMode() })
        }
    }
}

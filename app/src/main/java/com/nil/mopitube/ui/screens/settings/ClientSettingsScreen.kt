package com.nil.mopitube.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Client Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues)
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Refresh Artwork") },
                    supportingContent = { Text("Force a refresh of all local album artwork.") },
                    modifier = Modifier.clickable {
                        // TODO: Implement artwork refresh logic here
                        Toast.makeText(context, "Artwork refresh triggered!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

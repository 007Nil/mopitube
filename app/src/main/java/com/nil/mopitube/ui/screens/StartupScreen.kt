package com.nil.mopitube.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.Columnimport
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nil.mopitube.data.UserPreferencesRepository
import com.nil.mopitube.mopidy.MopidyClient
import com.nil.mopitube.mopidy.ConnectionState // <-- FIX: ADD THIS IMPORT
import kotlinx.coroutines.flow.first

@Composable
fun StartupScreen(
    client: MopidyClient,
    onNavigateToSettings: () -> Unit // Navigation callback
) {
    val context = LocalContext.current
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }

    // Observe the detailed connection state from the client
    val state by client.connectionState.collectAsState()

    // This LaunchedEffect will run once to check settings and decide the course of action.
    LaunchedEffect(Unit) {
        // Read the server settings from DataStore.
        // .first() gets the initial value and completes the flow for this one-time check.
        val host = userPreferencesRepository.serverHost.first()
        val port = userPreferencesRepository.serverPort.first()

        if (host.isBlank() || port.isBlank()) {
            // If settings are missing, navigate to the Settings screen.
            onNavigateToSettings()
        } else {
            // If settings are present, update the client and start the connection.
            client.updateServerConfig(host, port)
            client.start()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Update the UI based on the current connection state
        when (val currentState = state) {
            // With the import added, these lines will now resolve correctly
            is ConnectionState.Connecting -> {
                // State: Still trying to connect
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Connecting to Mopidy...")
                }
            }
            is ConnectionState.Connected -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Connected. Loading...")
                }
            }
            is ConnectionState.Disconnected -> {
                // State: Failure or disconnected
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Connection Failed", color = Color.Red)
                    Spacer(Modifier.height(8.dp))
                    // Provide the reason for the failure
                    Text(currentState.reason ?: "Unknown error")
                    Spacer(Modifier.height(16.dp))
                    // Add a button to allow the user to retry
                    Button(onClick = { client.retryConnection() }) {
                        Text("Retry")
                    }
                    Spacer(Modifier.height(8.dp))
                    // Add a button to navigate to settings
                    Button(onClick = onNavigateToSettings) {
                        Text("Go to Settings")
                    }
                }
            }
            is ConnectionState.Idle -> {
                // A default state while we check settings
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Checking server settings...")
                }
            }
        }
    }
}

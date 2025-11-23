package com.nil.mopitube.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nil.mopitube.mopidy.ConnectionState
import com.nil.mopitube.mopidy.MopidyClient

@Composable
fun StartupScreen(client: MopidyClient) {
    // Start the connection attempt when the screen is first shown
    LaunchedEffect(Unit) {
        client.start()
    }

    // Observe the detailed connection state from the client
    val state by client.connectionState.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Update the UI based on the current connection state
        when (val currentState = state) {
            is ConnectionState.Connecting -> {
                // State: Still trying to connect
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Connecting to Mopidy...")
                }
            }
            is ConnectionState.Connected -> {
                // State: Success!
                // AppNav will handle navigating away automatically.
                // We can show a temporary success message.
                Text("Connected!")
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
                }
            }
        }
    }
}

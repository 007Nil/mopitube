package com.nil.mopitube.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nil.mopitube.data.UserPreferencesRepository
import com.nil.mopitube.mopidy.ConnectionState
import com.nil.mopitube.mopidy.MopidyClient
import kotlinx.coroutines.flow.first

@Composable
fun StartupScreen(
    client: MopidyClient,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val state by client.connectionState.collectAsState()

    // When Idle, read settings and kick off the connection. Re-runs on each Idle (e.g. after Retry).
    LaunchedEffect(state) {
        if (state is ConnectionState.Idle) {
            val host = userPreferencesRepository.serverHost.first()
            val port = userPreferencesRepository.serverPort.first()
            if (host.isBlank() || port.isBlank()) {
                onNavigateToSettings()
            } else {
                client.updateServerConfig(host, port)
                client.start()
            }
        }
    }

    // Delegate all UI to the modern ReconnectScreen — no duplicate error UI.
    ReconnectScreen(
        client = client,
        connectionState = state,
        onNavigateToServerSettings = onNavigateToSettings
    )
}

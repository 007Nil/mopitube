package com.nil.mopitube.navigation

import android.app.Application
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.nil.mopitube.mopidy.MopidyClient
import androidx.compose.runtime.getValue // <-- Added
import androidx.compose.runtime.mutableStateOf // <-- Added

/**
 * A ViewModel to manage the lifecycle of the MopidyClient.
 *
 * By creating the client in a ViewModel, its lifecycle is tied to the NavHost or Activity/Fragment,
 * surviving configuration changes and remaining active as long as the navigation graph is in scope.
 * This is the correct place to hold a long-lived, session-level object.
 */
class AppNavViewModel(application: Application) : AndroidViewModel(application) {

    // The MopidyClient is now created once and managed by the ViewModel.
    // It's exposed as a non-nullable property, simplifying the UI code.
    val client: MopidyClient = MopidyClient(application.applicationContext)
    var hasNavigatedFromStartup by mutableStateOf(false)
    // The ViewModel will automatically be cleared when it's no longer needed (e.g., app is closed),
    // and its onCleared() method is the perfect place to shut down the client connection.
    override fun onCleared() {
        super.onCleared()
        client.shutdown()
    }
}

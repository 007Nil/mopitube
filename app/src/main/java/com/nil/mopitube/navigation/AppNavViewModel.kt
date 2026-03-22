package com.nil.mopitube.navigation

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nil.mopitube.App
import com.nil.mopitube.data.UserPreferencesRepository
import com.nil.mopitube.mopidy.MopidyClient
import kotlinx.coroutines.launch

/**
 * A ViewModel to manage the lifecycle of the MopidyClient.
 *
 * By creating the client in a ViewModel, its lifecycle is tied to the NavHost or Activity/Fragment,
 * surviving configuration changes and remaining active as long as the navigation graph is in scope.
 * This is the correct place to hold a long-lived, session-level object.
 */
class AppNavViewModel(application: Application) : AndroidViewModel(application) {

    val client: MopidyClient = MopidyClient(application.applicationContext).also {
        App.mopidyClient = it
    }
    var hasNavigatedFromStartup by mutableStateOf(false)
    var pendingSeekMs by mutableStateOf(0)

    var isDarkMode by mutableStateOf(false)
        private set

    private val prefsRepo = UserPreferencesRepository(application.applicationContext)

    init {
        viewModelScope.launch {
            prefsRepo.darkMode.collect { isDarkMode = it }
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch { prefsRepo.saveDarkMode(!isDarkMode) }
    }
    // The ViewModel will automatically be cleared when it's no longer needed (e.g., app is closed),
    // and its onCleared() method is the perfect place to shut down the client connection.
    override fun onCleared() {
        super.onCleared()
        App.mopidyClient = null
        client.shutdown()
    }
}

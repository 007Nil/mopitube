package com.nil.mopitube.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Create a singleton instance of DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {

    // Define keys for the values you want to store
    private object PreferencesKeys {
        val SERVER_HOST = stringPreferencesKey("server_host")
        val SERVER_PORT = stringPreferencesKey("server_port")
    }

    // Function to save the server settings
    suspend fun saveServerSettings(host: String, port: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVER_HOST] = host
            preferences[PreferencesKeys.SERVER_PORT] = port
        }
    }

    // Flow to read the server host
    val serverHost: Flow<String> = context.dataStore.data
        .map { preferences ->
            // Return the saved host or a default value if it's not set
            preferences[PreferencesKeys.SERVER_HOST] ?: ""
        }

    // Flow to read the server port
    val serverPort: Flow<String> = context.dataStore.data
        .map { preferences ->
            // Return the saved port or a default value if it's not set
            preferences[PreferencesKeys.SERVER_PORT] ?: ""
        }
}

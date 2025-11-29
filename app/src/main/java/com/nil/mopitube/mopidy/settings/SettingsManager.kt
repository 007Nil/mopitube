package com.nil.mopitube.data // Corrected package name

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Create the DataStore instance as a top-level extension property on Context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Manages saving and retrieving user settings using Jetpack DataStore.
 * This class provides a clean API for other parts of the app to interact with settings
 * without needing to know the implementation details of DataStore.
 *
 * @param context The application context, used to initialize DataStore.
 */
class SettingsManager(context: Context) {

    // Use the application context to avoid memory leaks
    private val appContext = context.applicationContext

    // Companion object to hold the keys for DataStore.
    // This makes them accessible without an instance of SettingsManager.
    companion object {
        val HOST_KEY = stringPreferencesKey("mopidy_host")
        val PORT_KEY = stringPreferencesKey("mopidy_port")
    }

    /**
     * A Flow that emits the saved Mopidy host value whenever it changes.
     * Returns null if no value has been set.
     */
    val hostFlow: Flow<String?>
        get() = appContext.dataStore.data.map { preferences ->
            preferences[HOST_KEY]
        }

    /**
     * A Flow that emits the saved Mopidy port value whenever it changes.
     * Returns null if no value has been set.
     */
    val portFlow: Flow<String?>
        get() = appContext.dataStore.data.map { preferences ->
            preferences[PORT_KEY]
        }

    /**
     * Saves the Mopidy host and port to DataStore.
     * This is a suspend function and must be called from a coroutine.
     */
    suspend fun saveSettings(host: String, port: String) {
        appContext.dataStore.edit { settings ->
            settings[HOST_KEY] = host
            settings[PORT_KEY] = port
        }
    }
}

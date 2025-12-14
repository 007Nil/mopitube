package com.nil.mopitube.mopidy

// This will be the single source of truth for all connection states in the app.
sealed interface ConnectionState {
    object Idle : ConnectionState
    object Connecting : ConnectionState
    object Connected : ConnectionState
    data class Disconnected(val reason: String?) : ConnectionState
}

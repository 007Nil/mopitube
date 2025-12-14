package com.nil.mopitube.mopidy

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// BUG FIX: Removed the duplicate 'sealed interface ConnectionState' definition from this file.
// It will now use the one from ConnectionState.kt.

class MopidyClient(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var host: String = ""
    private var port: Int = 0

    private lateinit var ws: MopidyWebSocket
//    private lateinit var rpc: MopidyRpcClient
//    lateinit var repo: MopidyRepository

    val queueManager = QueueManager()

    // This is the single source of truth that the UI will observe.
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    // --- FIX 1: MAKE COMPONENTS NULLABLE ---
    // Initialize components as null. They will be created in start()
    // and destroyed in shutdown(). This makes their lifecycle explicit.
    var rpc by mutableStateOf<MopidyRpcClient?>(null)
        private set
    var repo by mutableStateOf<MopidyRepository?>(null)
        private set
    private var webSocket: MopidyWebSocket? = null
    fun updateServerConfig(host: String, port: String) {
        this.host = host
        this.port = port.toIntOrNull() ?: 0
        Log.d("MopidyClient", "Server config updated: host=$host, port=${this.port}")
    }

    fun start() {
        // Prevent re-initialization if already running
        if (webSocket != null) {
            retryConnection()
            return
        }

        // --- FIX 2: INITIALIZE COMPONENTS HERE ---
        // Now we create the components when the client is explicitly started.
        val ws = MopidyWebSocket("ws://$host:$port/mopidy/ws", scope, _connectionState)
        webSocket = ws
        rpc = MopidyRpcClient(ws, scope)
        repo = MopidyRepository(rpc!!, context, "$host:$port", queueManager) // We can use '!!' here because we just created it.

        ws.connect()
    }

    fun retryConnection() {
        scope.launch {
            _connectionState.emit(ConnectionState.Connecting)
            webSocket?.connect()
        }
    }

    fun shutdown() {
        webSocket?.disconnect()
        rpc = null
        repo = null
        webSocket = null
        scope.launch {
            _connectionState.emit(ConnectionState.Idle)
        }
    }
}

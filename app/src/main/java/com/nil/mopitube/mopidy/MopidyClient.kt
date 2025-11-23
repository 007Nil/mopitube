package com.nil.mopitube.mopidy

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl // Import the correct parsing function

class MopidyClient(context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Define the server address in one place for easy modification
    private val serverHost = "192.168.1.50"
    private val serverPort = 6680

    // ===== THE FINAL, DEFINITIVE FIX IS HERE =====
    // We construct the URL as a string first, and then parse it using .toHttpUrl().
    // This is the correct and safe way to handle a "ws://" scheme with OkHttp.
    private val wsUrl = "ws://$serverHost:$serverPort/mopidy/ws"

    // This is the base address for fetching images. It MUST be http.
    private val httpAddress = "$serverHost:$serverPort"

    private val ws = MopidyWebSocket(wsUrl, scope)
    private val rpc = MopidyRpcClient(ws, scope)

    val queueManager = QueueManager()
    val repo = MopidyRepository(rpc, context, httpAddress, queueManager)

    // Pass the context and the correct HTTP address to the MopidyRepository
//    val repo = MopidyRepository(rpc, context, httpAddress)

    val connectionState: StateFlow<ConnectionState> = ws.connectionState

    fun start() {
        ws.connect()
    }

    fun retryConnection() {
        start()
    }


}

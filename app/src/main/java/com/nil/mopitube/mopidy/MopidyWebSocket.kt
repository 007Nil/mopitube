package com.nil.mopitube.mopidy

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import okhttp3.*
import okio.ByteString
import java.time.Duration

// This sealed class is correct and does not need to change.
sealed class ConnectionState {
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Disconnected(val reason: String?) : ConnectionState()
}

class MopidyWebSocket(
    // ===== THE FINAL FIX IS HERE =====
    // The constructor now correctly accepts an HttpUrl object.
    private val url: String,
    private val scope: CoroutineScope
) : WebSocketListener() {

    private val _messages = MutableSharedFlow<String>(replay = 1)
    val messages: MutableSharedFlow<String> = _messages

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected("Not started"))
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var ws: WebSocket? = null
    private var reconnectJob: Job? = null

    // Configure client with a ping interval so servers know the client is alive
    private val client = OkHttpClient.Builder()
        .pingInterval(Duration.ofSeconds(15))
        .build()

    fun connect() {
        if (_connectionState.value is ConnectionState.Connecting || _connectionState.value is ConnectionState.Connected) {
            Log.w("MopidyWS", "Already connected or connecting.")
            return
        }
        Log.i("MopidyWS", "Attempting to connect to $url...")
        _connectionState.value = ConnectionState.Connecting
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, this)
    }

    // This function allows other parts of the app to know the URL, which is good practice.
    fun getUrl() = url

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.i("MopidyWS", "Connection OPENED successfully.")
        _connectionState.value = ConnectionState.Connected
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        scope.launch {
            _messages.emit(text)
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        onMessage(webSocket, bytes.utf8())
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.w("MopidyWS", "Connection CLOSING: $code - $reason")
        webSocket.close(1000, null)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e("MopidyWS", "Connection FAILURE: ${t.message}", t)
        _connectionState.value = ConnectionState.Disconnected("Failure: ${t.message}")
        ws = null // Reset for reconnection attempts
        scheduleReconnect()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.w("MopidyWS", "Connection CLOSED: $code - $reason")
        _connectionState.value = ConnectionState.Disconnected("Closed: $reason")
        ws = null // Reset for reconnection attempts
        scheduleReconnect()
    }

    fun send(message: String) {
        scope.launch {
            if (_connectionState.value is ConnectionState.Connected) {
                ws?.send(message)
            } else {
                Log.e("MopidyWS", "Cannot send message, not connected.")
            }
        }
    }

    fun disconnect() {
        Log.d("MopidyWS", "Intentional disconnect requested. Stopping reconnect attempts and closing connection.")

        // 1. Cancel any pending or active reconnection jobs.
        // This is crucial to prevent the app from trying to reconnect after we've shut down.
        reconnectJob?.cancel()
        reconnectJob = null

        // 2. Close the WebSocket connection if it exists.
        ws?.close(1000, "Client initiated shutdown")
        ws = null

        // 3. Completely shut down the OkHttpClient to release all network resources.
        // This is the key step for proper lifecycle cleanup when the app goes to the background.
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()

        // 4. Update the state to reflect the intentional disconnection.
        _connectionState.value = ConnectionState.Disconnected("Client shut down")
    }

    private fun scheduleReconnect() {
        // If a reconnect loop is already running, don't start another.
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            var delayMs = 1000L
            val maxDelay = 30_000L
            while (isActive && _connectionState.value !is ConnectionState.Connected) {
                try {
                    Log.i("MopidyWS", "Reconnect attempt in ${delayMs}ms")
                    delay(delayMs)
                    // Try to connect again
                    connect()
                } catch (t: Throwable) {
                    Log.w("MopidyWS", "Reconnect attempt failed: ${t.message}")
                }
                delayMs = min(delayMs * 2, maxDelay)
            }
        }
    }

    /**
     * Close the websocket and stop any reconnect attempts.
     * Call this when the client is explicitly shutting down.
     */
    fun close() {
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            ws?.close(1000, "Client closed")
        } catch (t: Throwable) {
            Log.w("MopidyWS", "Error while closing websocket: ${t.message}")
        }
        ws = null
        _connectionState.value = ConnectionState.Disconnected("Client closed")
        // It's OK to keep the OkHttpClient for reuse; shutting it down would require new client creation.
    }
}

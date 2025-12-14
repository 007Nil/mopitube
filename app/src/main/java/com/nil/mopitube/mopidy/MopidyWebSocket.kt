package com.nil.mopitube.mopidy

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import java.time.Duration
import kotlin.math.min

class MopidyWebSocket(
    private val url: String,
    private val scope: CoroutineScope,
    // This remains the internal mutable state flow passed from MopidyClient.
    private val _connectionState: MutableStateFlow<ConnectionState>
) : WebSocketListener() {

    // --- FIX: EXPOSE A PUBLIC, READ-ONLY VERSION OF THE STATE ---
    // This allows other classes like MopidyRpcClient to observe the state
    // without being able to change it.
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _messages = MutableSharedFlow<String>(replay = 1)
    val messages: MutableSharedFlow<String> = _messages

    private var ws: WebSocket? = null
    private var reconnectJob: Job? = null

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
        ws = null
        scheduleReconnect()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.w("MopidyWS", "Connection CLOSED: $code - $reason")
        _connectionState.value = ConnectionState.Disconnected("Closed: $reason")
        ws = null
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
        Log.d("MopidyWS", "Intentional disconnect requested.")
        reconnectJob?.cancel()
        reconnectJob = null
        ws?.close(1000, "Client initiated shutdown")
        ws = null
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        _connectionState.value = ConnectionState.Disconnected("Client shut down")
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            var delayMs = 1000L
            val maxDelay = 30_000L
            while (isActive && _connectionState.value !is ConnectionState.Connected) {
                try {
                    Log.i("MopidyWS", "Reconnect attempt in ${delayMs}ms")
                    delay(delayMs)
                    connect()
                } catch (t: Throwable) {
                    Log.w("MopidyWS", "Reconnect attempt failed: ${t.message}")
                }
                delayMs = min(delayMs * 2, maxDelay)
            }
        }
    }
}

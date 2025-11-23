package com.nil.mopitube.mopidy

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString

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
    private val client = OkHttpClient()

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
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.w("MopidyWS", "Connection CLOSED: $code - $reason")
        _connectionState.value = ConnectionState.Disconnected("Closed: $reason")
        ws = null // Reset for reconnection attempts
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
}

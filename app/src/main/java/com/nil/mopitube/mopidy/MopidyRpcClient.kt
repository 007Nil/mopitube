package com.nil.mopitube.mopidy

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MopidyRpcClient(
    private val ws: MopidyWebSocket,
    private val scope: CoroutineScope
) {
    private val idCounter = AtomicInteger(0)
    // thread-safe map for pending requests
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonElement?>>()

    init {
        scope.launch {
            ws.messages.collect { message ->
                try {
                    val json = Json.parseToJsonElement(message).jsonObject
                    val id = json["id"]?.jsonPrimitive?.intOrNull
                    if (id != null) {
                        val deferred = pendingRequests.remove(id)
                        deferred?.complete(json["result"])
                    }
                } catch (e: Exception) {
                    Log.e("MopidyRpcClient", "Failed to parse message: $message", e)
                }
            }
        }
    }

    suspend fun call(method: String, params: JsonObject? = null): JsonElement? {
        val id = idCounter.incrementAndGet()
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            if (params != null) {
                put("params", params)
            }
        }

        // Wait for the websocket to be connected, with a 5-second timeout.
        val isConnected = withTimeoutOrNull(5000L) {
            ws.connectionState.first { it is ConnectionState.Connected }
        } != null

        // If not connected after the timeout, abort the request.
        if (!isConnected) {
            Log.w("MopidyRpcClient", "Request '$method' (id=$id) aborted: WebSocket not connected within timeout.")
            return null
        }


        val deferred = CompletableDeferred<JsonElement?>()
        pendingRequests[id] = deferred

        ws.send(request.toString())

        val result = withTimeoutOrNull(5000L) {
            deferred.await()
        }

        if (result == null) {
            Log.w("MopidyRpcClient", "Request '$method' (id=$id) timed out.")
            pendingRequests.remove(id)
        }

        return result
    }
}

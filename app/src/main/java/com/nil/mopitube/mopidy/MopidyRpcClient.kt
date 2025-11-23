package com.nil.mopitube.mopidy

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull // FIX: Separated import statements and removed duplicates
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicInteger

class MopidyRpcClient(
    private val ws: MopidyWebSocket,
    private val scope: CoroutineScope
) {
    private val idCounter = AtomicInteger(0)
    private val pendingRequests = mutableMapOf<Int, (JsonElement?) -> Unit>()

    init {
        scope.launch {
            ws.messages.collect { message ->
                try {
                    val json = Json.parseToJsonElement(message).jsonObject
                    val id = json["id"]?.jsonPrimitive?.intOrNull
                    if (id != null) {
                        val callback = pendingRequests.remove(id)
                        callback?.invoke(json["result"])
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

        val response = withTimeoutOrNull(5000L) { // 5 second timeout
            val responseFlow = MutableSharedFlow<JsonElement?>()
            pendingRequests[id] = { result ->
                scope.launch {
                    responseFlow.emit(result)
                }
            }
            ws.send(request.toString())
            responseFlow.first()
        }

        if (response == null) {
            Log.w("MopidyRpcClient", "Request '$method' (id=$id) timed out.")
            pendingRequests.remove(id)
        }

        return response
    }
}

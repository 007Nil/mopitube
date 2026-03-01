package com.nil.mopitube.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nil.mopitube.data.UserPreferencesRepository
import com.nil.mopitube.mopidy.MopidyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun ServerSettingsScreen(repo: MopidyRepository? = null) {
    val context = LocalContext.current
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var scanStatusText by remember { mutableStateOf<String?>(null) }

    val savedHost by userPreferencesRepository.serverHost.collectAsState(initial = "")
    val savedPort by userPreferencesRepository.serverPort.collectAsState(initial = "")

    LaunchedEffect(savedHost, savedPort) {
        host = savedHost
        port = savedPort
        // Fetch last known scan status when the host is available
        if (savedHost.isNotBlank()) {
            val status = getScanStatus(savedHost)
            if (status != null && !status.running) {
                scanStatusText = if (status.lastStatus == "success") "Last scan: complete" else "Last scan: ${status.lastStatus}"
            } else if (status?.running == true) {
                // A scan is already in progress on the server — reflect that in the UI
                isScanning = true
                scanStatusText = "Scanning..."
                while (true) {
                    kotlinx.coroutines.delay(2000)
                    val updated = getScanStatus(savedHost) ?: break
                    if (!updated.running) {
                        if (updated.lastStatus == "success") {
                            scanStatusText = "Scan complete — syncing client..."
                            repo?.refreshAllTracksFromServer()
                            scanStatusText = "Scan complete"
                        } else {
                            scanStatusText = "Scan failed: ${updated.lastStatus}"
                        }
                        isScanning = false
                        break
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // --- Connection Section ---
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connection",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host / IP Address") },
                    placeholder = { Text("192.168.1.50") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    placeholder = { Text("6680") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            userPreferencesRepository.saveServerSettings(host, port)
                            Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Save")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // --- Server Actions Section ---
        item {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Server Actions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Scan Local Library") },
                supportingContent = {
                    Text(
                        text = when {
                            isScanning -> scanStatusText ?: "Starting scan..."
                            scanStatusText != null -> scanStatusText!!
                            else -> "Trigger a local media scan on the server (port 9000)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            scanStatusText == "Scan complete" -> MaterialTheme.colorScheme.primary
                            scanStatusText?.startsWith("Scan failed") == true -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Scanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                },
                modifier = Modifier.clickable(enabled = !isScanning && host.isNotBlank()) {
                    isScanning = true
                    scanStatusText = null
                    scope.launch {
                        try {
                            val started = triggerLibraryScan(host)
                            if (!started) {
                                scanStatusText = "Scan failed: could not reach scan server"
                                return@launch
                            }
                            // Poll scan-status until running == false
                            scanStatusText = "Scanning..."
                            while (true) {
                                kotlinx.coroutines.delay(2000)
                                val status = getScanStatus(host) ?: break
                                if (!status.running) {
                                    if (status.lastStatus == "success") {
                                        scanStatusText = "Scan complete — syncing client..."
                                        repo?.refreshAllTracksFromServer()
                                        scanStatusText = "Scan complete"
                                    } else {
                                        scanStatusText = "Scan failed: ${status.lastStatus}"
                                    }
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            scanStatusText = "Scan failed: ${e.message}"
                        } finally {
                            isScanning = false
                        }
                    }
                }
            )
        }
    }
}

private data class ScanStatus(val running: Boolean, val lastStatus: String)

private suspend fun triggerLibraryScan(host: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val url = URL("http://$host:9000/rescan")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        conn.doOutput = true
        conn.outputStream.close()
        val code = conn.responseCode
        conn.disconnect()
        code in 200..299
    } catch (e: Exception) {
        false
    }
}

private suspend fun getScanStatus(host: String): ScanStatus? = withContext(Dispatchers.IO) {
    try {
        val url = URL("http://$host:9000/scan-status")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val json = JSONObject(body)
        ScanStatus(
            running = json.optBoolean("running", false),
            lastStatus = json.optString("last_status", "unknown")
        )
    } catch (e: Exception) {
        null
    }
}

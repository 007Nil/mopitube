package com.nil.mopitube.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nil.mopitube.data.BackupRepository
import com.nil.mopitube.mopidy.MopidyRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClientSettingsScreen(
    repo: MopidyRepository?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    val backupRepo = remember { BackupRepository(context) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isBackingUp = true
        scope.launch {
            backupRepo.exportBackup(uri)
                .onSuccess { Toast.makeText(context, "Backup saved successfully!", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Backup failed: ${it.message}", Toast.LENGTH_SHORT).show() }
            isBackingUp = false
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isRestoring = true
        scope.launch {
            backupRepo.importBackup(uri)
                .onSuccess { Toast.makeText(context, "Restore completed successfully!", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Restore failed: ${it.message}", Toast.LENGTH_SHORT).show() }
            isRestoring = false
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Library",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Resync All Songs") },
                supportingContent = {
                    Text(
                        text = if (isSyncing) "Syncing..." else "Re-download the full track library from the server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingContent = {
                    if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                },
                modifier = Modifier.clickable(enabled = !isSyncing && repo != null) {
                    isSyncing = true
                    scope.launch {
                        try {
                            repo?.refreshAllTracksFromServer()
                            Toast.makeText(context, "Library synced successfully!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSyncing = false
                        }
                    }
                }
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cache",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Refresh Artwork") },
                supportingContent = {
                    Text(
                        text = "Force a refresh of all local album artwork.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(imageVector = Icons.Default.Image, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.clickable {
                    Toast.makeText(context, "Artwork refresh triggered!", Toast.LENGTH_SHORT).show()
                }
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Data",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Backup Data") },
                supportingContent = {
                    Text(
                        text = if (isBackingUp) "Saving..." else "Export liked songs, history & listen later to a file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    if (isBackingUp) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                modifier = Modifier.clickable(enabled = !isBackingUp) {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    backupLauncher.launch("mopitube_backup_$timestamp.json")
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Restore Data") },
                supportingContent = {
                    Text(
                        text = if (isRestoring) "Restoring..." else "Import a previously exported backup file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    if (isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                modifier = Modifier.clickable(enabled = !isRestoring) {
                    restoreLauncher.launch(arrayOf("application/json"))
                }
            )
        }
    }
}

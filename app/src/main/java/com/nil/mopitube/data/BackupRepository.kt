package com.nil.mopitube.data

import android.content.Context
import android.net.Uri
import com.nil.mopitube.database.DislikedTrack
import com.nil.mopitube.database.LikedTrack
import com.nil.mopitube.database.ListenLaterEntry
import com.nil.mopitube.database.MopitubeDatabase
import com.nil.mopitube.database.PlayHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val likedTracks: List<String> = emptyList(),
    val dislikedTracks: List<String> = emptyList(),
    val playHistory: List<PlayHistoryRecord> = emptyList(),
    val listenLater: List<ListenLaterRecord> = emptyList()
)

@Serializable
private data class PlayHistoryRecord(val uri: String, val timestamp: Long)

@Serializable
private data class ListenLaterRecord(
    val uri: String,
    val title: String,
    val artist: String?,
    val albumUri: String?,
    val positionMs: Int,
    val savedAt: Long
)

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

class BackupRepository(private val context: Context) {

    private val dao = MopitubeDatabase.getDatabase(context).mopitubeDao()

    suspend fun exportBackup(destinationUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val liked = dao.getAllLikedTracks().map { it.uri }
            val disliked = dao.getAllDislikedTracks().map { it.uri }
            val history = dao.getAllPlayHistory().map { PlayHistoryRecord(it.uri, it.timestamp) }
            val later = dao.getAllListenLater().map {
                ListenLaterRecord(it.uri, it.title, it.artist, it.albumUri, it.positionMs, it.savedAt)
            }
            val backup = BackupData(
                likedTracks = liked,
                dislikedTracks = disliked,
                playHistory = history,
                listenLater = later
            )
            context.contentResolver.openOutputStream(destinationUri)?.use { stream ->
                stream.write(json.encodeToString(BackupData.serializer(), backup).toByteArray())
            } ?: return@withContext Result.failure(Exception("Could not open output stream"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackup(sourceUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return@withContext Result.failure(Exception("Could not open input stream"))

            val backup = json.decodeFromString(BackupData.serializer(), content)

            backup.likedTracks.forEach { dao.likeTrack(LikedTrack(it)) }
            backup.dislikedTracks.forEach { dao.dislikeTrack(DislikedTrack(it)) }
            backup.playHistory.forEach { dao.insertPlayHistory(PlayHistoryEntry(uri = it.uri, timestamp = it.timestamp)) }
            backup.listenLater.forEach {
                dao.saveListenLater(
                    ListenLaterEntry(
                        uri = it.uri,
                        title = it.title,
                        artist = it.artist,
                        albumUri = it.albumUri,
                        positionMs = it.positionMs,
                        savedAt = it.savedAt
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

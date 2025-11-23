package com.nil.mopitube.database

import android.content.Context
import androidx.room.*

// --- Table 1: Artwork Cache (Unchanged) ---
@Entity(tableName = "artwork_cache")
data class ArtworkCacheEntry(
    @PrimaryKey val cacheKey: String,
    val imageUrl: String
)

// --- Table 2: Liked Tracks (Unchanged) ---
@Entity(tableName = "liked_tracks")
data class LikedTrack(
    @PrimaryKey val uri: String
)

// ===== NEW: Table 3: Play History =====
@Entity(tableName = "play_history")
data class PlayHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uri: String,
    val timestamp: Long = System.currentTimeMillis()
)

// A simple data class to hold the result of our count query
data class TrackPlayCount(
    val uri: String,
    val playCount: Int
)

// --- DAO (Data Access Object) - Updated ---
@Dao
interface MopitubeDao {
    // --- Artwork Methods (Unchanged) ---
    @Query("SELECT * FROM artwork_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getArtwork(key: String): ArtworkCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtwork(entry: ArtworkCacheEntry)

    // --- Liked Track Methods (Unchanged) ---
    @Query("SELECT * FROM liked_tracks WHERE uri = :trackUri LIMIT 1")
    suspend fun findLikedTrack(trackUri: String): LikedTrack?

    @Query("SELECT * FROM liked_tracks")
    suspend fun getAllLikedTracks(): List<LikedTrack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun likeTrack(track: LikedTrack)

    @Query("DELETE FROM liked_tracks WHERE uri = :trackUri")
    suspend fun unlikeTrack(trackUri: String)

    // ===== NEW: Play History Methods =====
    @Insert
    suspend fun insertPlayHistory(entry: PlayHistoryEntry)

    @Query("""
        SELECT uri, COUNT(uri) as playCount 
        FROM play_history 
        GROUP BY uri 
        ORDER BY playCount DESC 
        LIMIT :limit
    """)
    suspend fun getMostPlayed(limit: Int): List<TrackPlayCount>
}

// --- Database Definition (Updated) ---
// Add PlayHistoryEntry to the entities list and increment the version number to 3
@Database(entities = [ArtworkCacheEntry::class, LikedTrack::class, PlayHistoryEntry::class], version = 3)
abstract class MopitubeDatabase : RoomDatabase() {
    abstract fun mopitubeDao(): MopitubeDao

    companion object {
        @Volatile
        private var INSTANCE: MopitubeDatabase? = null

        fun getDatabase(context: Context): MopitubeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MopitubeDatabase::class.java,
                    "mopitube_app.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

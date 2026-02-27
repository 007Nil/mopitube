package com.nil.mopitube.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

// --- Table: Disliked Tracks ---
@Entity(tableName = "disliked_tracks")
data class DislikedTrack(
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
    // ===== NEW: Method to cache tracks =====
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTracks(tracks: List<Track>)

    // ===== NEW: Method to retrieve all cached tracks =====
    @Query("SELECT * FROM tracks")
    suspend fun getAllCachedTracks(): List<Track>

    // ===== NEW: Method to search cached tracks =====
    @Query("""
        SELECT * FROM tracks
        WHERE name LIKE '%' || :query || '%'
        OR artistName LIKE '%' || :query || '%'
        OR albumName LIKE '%' || :query || '%'
        ORDER BY
            CASE
                WHEN name LIKE :query || '%' THEN 1
                WHEN artistName LIKE :query || '%' THEN 2
                WHEN albumName LIKE :query || '%' THEN 3
                ELSE 4
            END,
            name ASC
    """)
    suspend fun searchTracks(query: String): List<Track>

    // Smart queue: random tracks with the same genre, excluding already-queued URIs
    @Query("SELECT * FROM tracks WHERE genre = :genre AND uri NOT IN (:excludeUris) ORDER BY RANDOM() LIMIT :limit")
    suspend fun getTracksByGenre(genre: String, excludeUris: List<String>, limit: Int): List<Track>

    // Smart queue: random tracks by the same artist, excluding already-queued URIs
    @Query("SELECT * FROM tracks WHERE artistName = :artistName AND uri NOT IN (:excludeUris) ORDER BY RANDOM() LIMIT :limit")
    suspend fun getTracksByArtist(artistName: String, excludeUris: List<String>, limit: Int): List<Track>

    // --- Liked Track Methods (Unchanged) ---
    @Query("SELECT * FROM liked_tracks WHERE uri = :trackUri LIMIT 1")
    suspend fun findLikedTrack(trackUri: String): LikedTrack?

    @Query("SELECT * FROM liked_tracks")
    suspend fun getAllLikedTracks(): List<LikedTrack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun likeTrack(track: LikedTrack)

    @Query("DELETE FROM liked_tracks WHERE uri = :trackUri")
    suspend fun unlikeTrack(trackUri: String)

    // --- Disliked Track Methods ---
    @Query("SELECT * FROM disliked_tracks WHERE uri = :trackUri LIMIT 1")
    suspend fun findDislikedTrack(trackUri: String): DislikedTrack?

    @Query("SELECT * FROM disliked_tracks")
    suspend fun getAllDislikedTracks(): List<DislikedTrack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun dislikeTrack(track: DislikedTrack)

    @Query("DELETE FROM disliked_tracks WHERE uri = :trackUri")
    suspend fun undislikeTrack(trackUri: String)

    // ===== Track Cache Methods =====
    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()

    // ===== Play History Methods =====
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

    @Query("DELETE FROM play_history WHERE timestamp < :cutoffTimestamp")
    suspend fun deletePlayHistoryOlderThan(cutoffTimestamp: Long)
}

// --- Database Definition (Updated) ---
// Add PlayHistoryEntry to the entities list and increment the version number to 3
@Database(entities =
    [
        ArtworkCacheEntry::class,
        LikedTrack::class,
        DislikedTrack::class,
        PlayHistoryEntry::class,
        Track::class
    ], version = 6)
abstract class MopitubeDatabase : RoomDatabase() {
    abstract fun mopitubeDao(): MopitubeDao

    companion object {
        @Volatile
        private var INSTANCE: MopitubeDatabase? = null

        // Migration from version 1 to 2 (if version 1 existed with just artwork_cache and liked_tracks)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 1->2: No schema changes, just version bump
                // (Version 1 likely had artwork_cache and liked_tracks)
            }
        }

        // Migration from version 2 to 3: Add play_history table
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS play_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        uri TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """)
            }
        }

        // Migration from version 4 to 5: Add genre column to tracks table
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN genre TEXT")
            }
        }

        // Migration from version 5 to 6: Add disliked_tracks table
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS disliked_tracks (uri TEXT NOT NULL, PRIMARY KEY(uri))")
            }
        }

        // Migration from version 3 to 4: Add tracks table
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tracks (
                        uri TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        artistName TEXT,
                        albumName TEXT,
                        albumUri TEXT,
                        length INTEGER
                    )
                """)
            }
        }

        fun getDatabase(context: Context): MopitubeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MopitubeDatabase::class.java,
                    "mopitube_app.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration() // Only as last resort for unknown versions
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

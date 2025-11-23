package com.nil.mopitube.mopidy

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

// 1. Define the table structure for Room
@Entity(tableName = "artwork_cache")
data class ArtworkCacheEntry(
    @ColumnInfo(name = "album_id")
    @androidx.room.PrimaryKey val albumId: String,

    @ColumnInfo(name = "image_url")
    val imageUrl: String
)

// 2. Define the Data Access Object (DAO) with methods to interact with the table
@Dao
interface ArtworkDao {
    @Query("SELECT * FROM artwork_cache WHERE album_id = :albumId LIMIT 1")
    suspend fun getArtwork(albumId: String): ArtworkCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtwork(entry: ArtworkCacheEntry)
}

// 3. Define the Database class itself
@Database(entities = [ArtworkCacheEntry::class], version = 1)
abstract class ArtworkDatabase : RoomDatabase() {
    abstract fun artworkDao(): ArtworkDao

    companion object {
        @Volatile
        private var INSTANCE: ArtworkDatabase? = null

        fun getDatabase(context: Context): ArtworkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ArtworkDatabase::class.java,
                    "artwork_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

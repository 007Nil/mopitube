package com.nil.mopitube.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val uri: String,
    val name: String,
    val artistName: String?,
    val albumName: String?,
    val albumUri: String?,
    val length: Int?
)

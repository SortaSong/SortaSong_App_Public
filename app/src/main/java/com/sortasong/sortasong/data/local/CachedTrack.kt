package com.sortasong.sortasong.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [Index(value = ["song", "artist"], unique = true)]
)
data class CachedTrack(
    @PrimaryKey
    val id: Int,
    val song: String,
    val artist: String,
    val releaseDate: String,
    val releaseYear: Int?,
    val fileAvailability: String = "NOT_VERIFIED"
)

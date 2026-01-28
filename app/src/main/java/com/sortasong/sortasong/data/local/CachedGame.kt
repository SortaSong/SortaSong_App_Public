package com.sortasong.sortasong.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "games",
    indices = [Index(value = ["folderName"], unique = true)]
)
data class CachedGame(
    @PrimaryKey
    val id: Int,
    val game: String,
    val folderName: String,
    val linkIdentifier: String
)

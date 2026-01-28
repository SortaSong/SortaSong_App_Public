package com.sortasong.sortasong.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "game_tracks",
    indices = [
        Index(value = ["gameId"]),
        Index(value = ["trackId"]),
        Index(value = ["gameId", "trackId"], unique = true)
    ]
)
data class CachedGameTrack(
    @PrimaryKey
    val id: Int,
    val gameId: Int,
    val trackId: Int,
    val trackNr: String
)

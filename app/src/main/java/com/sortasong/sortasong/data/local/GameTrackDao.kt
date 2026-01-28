package com.sortasong.sortasong.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class TrackWithGameInfo(
    val trackId: Int,
    val trackNr: String,
    val song: String,
    val artist: String,
    val releaseDate: String,
    val releaseYear: Int?,
    val folderName: String,
    val fileAvailability: String
)

@Dao
interface GameTrackDao {
    @Query("SELECT * FROM game_tracks")
    suspend fun getAllGameTracks(): List<CachedGameTrack>

    @Query("""
        SELECT
            t.id as trackId,
            gt.trackNr,
            t.song,
            t.artist,
            t.releaseDate,
            t.releaseYear,
            g.folderName,
            t.fileAvailability
        FROM tracks t
        JOIN game_tracks gt ON t.id = gt.trackId
        JOIN games g ON gt.gameId = g.id
        WHERE g.folderName = :folderName
    """)
    suspend fun getTracksForFolder(folderName: String): List<TrackWithGameInfo>

    @Query("""
        SELECT
            t.id as trackId,
            gt.trackNr,
            t.song,
            t.artist,
            t.releaseDate,
            t.releaseYear,
            g.folderName,
            t.fileAvailability
        FROM tracks t
        JOIN game_tracks gt ON t.id = gt.trackId
        JOIN games g ON gt.gameId = g.id
    """)
    suspend fun getAllTracksWithGameInfo(): List<TrackWithGameInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(gameTracks: List<CachedGameTrack>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllSync(gameTracks: List<CachedGameTrack>)

    @Query("DELETE FROM game_tracks")
    suspend fun deleteAll()

    @Query("DELETE FROM game_tracks")
    fun deleteAllSync()
}

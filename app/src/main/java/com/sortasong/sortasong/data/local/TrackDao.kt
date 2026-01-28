package com.sortasong.sortasong.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks")
    suspend fun getAllTracks(): List<CachedTrack>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: Int): CachedTrack?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<CachedTrack>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllSync(tracks: List<CachedTrack>)

    @Query("DELETE FROM tracks")
    suspend fun deleteAll()

    @Query("DELETE FROM tracks")
    fun deleteAllSync()

    @Query("UPDATE tracks SET fileAvailability = :availability WHERE id = :trackId")
    suspend fun updateFileAvailability(trackId: Int, availability: String)

    @Query("UPDATE tracks SET fileAvailability = 'NOT_VERIFIED'")
    suspend fun resetAllToNotVerified()
}

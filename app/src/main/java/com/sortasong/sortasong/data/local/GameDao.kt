package com.sortasong.sortasong.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GameDao {
    @Query("SELECT * FROM games")
    suspend fun getAllGames(): List<CachedGame>

    @Query("SELECT * FROM games WHERE folderName = :folderName")
    suspend fun getGameByFolderName(folderName: String): CachedGame?

    @Query("SELECT * FROM games WHERE linkIdentifier = :linkIdentifier")
    suspend fun getGameByLinkIdentifier(linkIdentifier: String): CachedGame?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<CachedGame>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllSync(games: List<CachedGame>)

    @Query("DELETE FROM games")
    suspend fun deleteAll()

    @Query("DELETE FROM games")
    fun deleteAllSync()
}

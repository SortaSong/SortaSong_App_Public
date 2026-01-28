package com.sortasong.sortasong.data

import android.util.Log
import com.sortasong.sortasong.data.local.CachedGame
import com.sortasong.sortasong.data.local.CachedGameTrack
import com.sortasong.sortasong.data.local.CachedTrack
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseGame(
    val id: Int,
    val game: String,
    @SerialName("folder_name") val folderName: String,
    @SerialName("link_identifier") val linkIdentifier: String
)

@Serializable
data class SupabaseTrack(
    val id: Int,
    val song: String,
    val artist: String,
    @SerialName("release_date") val releaseDate: String,
    @SerialName("release_year") val releaseYear: Int?
)

@Serializable
data class SupabaseGameTrack(
    val id: Int,
    @SerialName("game_id") val gameId: Int,
    @SerialName("track_id") val trackId: Int,
    @SerialName("track_nr") val trackNr: String
)

data class SupabaseDataResult(
    val games: List<CachedGame>,
    val tracks: List<CachedTrack>,
    val gameTracks: List<CachedGameTrack>
)

object SupabaseDataService {
    private const val TAG = "SupabaseDataService"

    suspend fun fetchAllData(): Result<SupabaseDataResult> {
        return try {
            if (!SupabaseClient.isConfigured()) {
                return Result.failure(Exception("Supabase not configured"))
            }

            val games = fetchGames()
            val tracks = fetchTracks()
            val gameTracks = fetchGameTracks()

            Result.success(
                SupabaseDataResult(
                    games = games.map { it.toCached() },
                    tracks = tracks.map { it.toCached() },
                    gameTracks = gameTracks.map { it.toCached() }
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching data from Supabase", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchGames(): List<SupabaseGame> {
        return SupabaseClient.client.from("games")
            .select()
            .decodeList<SupabaseGame>()
    }

    private suspend fun fetchTracks(): List<SupabaseTrack> {
        val allTracks = mutableListOf<SupabaseTrack>()
        var from = 0L
        val pageSize = 1000L

        while (true) {
            val to = from + pageSize - 1
            val batch = SupabaseClient.client.from("tracks")
                .select {
                    range(from, to)
                }
                .decodeList<SupabaseTrack>()

            allTracks.addAll(batch)
            Log.d(TAG, "Fetched ${batch.size} tracks (total: ${allTracks.size})")

            if (batch.size < pageSize.toInt()) break
            from += pageSize
        }

        return allTracks
    }

    private suspend fun fetchGameTracks(): List<SupabaseGameTrack> {
        val allGameTracks = mutableListOf<SupabaseGameTrack>()
        var from = 0L
        val pageSize = 1000L

        while (true) {
            val to = from + pageSize - 1
            val batch = SupabaseClient.client.from("game_tracks")
                .select {
                    range(from, to)
                }
                .decodeList<SupabaseGameTrack>()

            allGameTracks.addAll(batch)
            Log.d(TAG, "Fetched ${batch.size} game_tracks (total: ${allGameTracks.size})")

            if (batch.size < pageSize.toInt()) break
            from += pageSize
        }

        return allGameTracks
    }

    private fun SupabaseGame.toCached() = CachedGame(
        id = id,
        game = game,
        folderName = folderName,
        linkIdentifier = linkIdentifier
    )

    private fun SupabaseTrack.toCached() = CachedTrack(
        id = id,
        song = song,
        artist = artist,
        releaseDate = releaseDate,
        releaseYear = releaseYear,
        fileAvailability = "NOT_VERIFIED"
    )

    private fun SupabaseGameTrack.toCached() = CachedGameTrack(
        id = id,
        gameId = gameId,
        trackId = trackId,
        trackNr = trackNr
    )
}

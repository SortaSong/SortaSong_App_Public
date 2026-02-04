package com.sortasong.sortasong.data

import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Represents a community game submission from Supabase
 */
@Serializable
data class CommunityGame(
    val id: String,
    val description: String? = null,
    @SerialName("game_data")
    val gameData: CustomGameInfo,
    val status: String,
    @SerialName("submitted_by_name")
    val submittedByName: String? = null,
    @SerialName("vote_count")
    val voteCount: Int = 0,
    @SerialName("download_count")
    val downloadCount: Int = 0,
    @SerialName("created_at")
    val createdAt: String? = null
) {
    val gameName: String get() = gameData.game
    val folderName: String get() = gameData.folderName
    val trackCount: Int get() = gameData.tracks.size
}

enum class SortOrder {
    NEWEST, MOST_VOTES, MOST_DOWNLOADS
}

sealed class CommunityGamesResult {
    data class Success(val games: List<CommunityGame>) : CommunityGamesResult()
    data class Error(val message: String) : CommunityGamesResult()
}

sealed class VoteResult {
    data object Success : VoteResult()
    data object AlreadyVoted : VoteResult()
    data class Error(val message: String) : VoteResult()
}

@Serializable
data class VoteParams(
    @SerialName("p_submission_id")
    val submissionId: String
)

@Serializable
data class IncrementDownloadParams(
    @SerialName("p_submission_id")
    val submissionId: String
)

object CommunityGameService {
    private const val TAG = "CommunityGameService"
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    /**
     * Fetch approved/published community games
     */
    suspend fun fetchCommunityGames(sortOrder: SortOrder = SortOrder.NEWEST): CommunityGamesResult {
        return try {
            if (!SupabaseClient.isConfigured()) {
                return CommunityGamesResult.Error("Supabase not configured")
            }
            
            val games = SupabaseClient.client.postgrest
                .from("community_submissions")
                .select {
                    filter {
                        or {
                            eq("status", "approved")
                            eq("status", "published")
                        }
                    }
                }
                .decodeList<CommunityGame>()
            
            // Sort
            val sorted = when (sortOrder) {
                SortOrder.NEWEST -> games.sortedByDescending { it.createdAt }
                SortOrder.MOST_VOTES -> games.sortedByDescending { it.voteCount }
                SortOrder.MOST_DOWNLOADS -> games.sortedByDescending { it.downloadCount }
            }
            
            CommunityGamesResult.Success(sorted)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching community games", e)
            CommunityGamesResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Vote for a community game
     */
    suspend fun voteForGame(gameId: String): VoteResult {
        return try {
            if (!SupabaseClient.isConfigured()) {
                return VoteResult.Error("Supabase not configured")
            }
            
            // Call RPC function to increment vote
            SupabaseClient.client.postgrest
                .rpc("vote_submission", VoteParams(gameId))
            
            VoteResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Error voting for game", e)
            VoteResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Increment download count for a game
     */
    suspend fun incrementDownload(gameId: String) {
        try {
            if (!SupabaseClient.isConfigured()) return
            
            SupabaseClient.client.postgrest
                .rpc("increment_download", IncrementDownloadParams(gameId))
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing download count", e)
        }
    }
}

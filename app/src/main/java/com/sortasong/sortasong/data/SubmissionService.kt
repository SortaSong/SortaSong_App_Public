package com.sortasong.sortasong.data

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/**
 * Stored credentials for a community submission
 */
@Serializable
data class SubmissionCredential(
    val folderName: String,
    val gameName: String,
    val submissionId: String,
    val passwordHash: String,
    val cachedStatus: String = "pending",
    val cachedVoteCount: Int = 0,
    val cachedDownloadCount: Int = 0,
    val rejectionReason: String? = null,
    val submittedAt: Long = System.currentTimeMillis()
)

/**
 * Response from check_submission_status RPC
 */
@Serializable
data class SubmissionStatusResponse(
    val success: Boolean,
    val status: String? = null,
    @SerialName("vote_count")
    val voteCount: Int? = null,
    @SerialName("download_count")
    val downloadCount: Int? = null,
    @SerialName("rejection_reason")
    val rejectionReason: String? = null,
    @SerialName("game_name")
    val gameName: String? = null,
    val error: String? = null
)

/**
 * Parameters for submitting a game
 */
@Serializable
data class SubmitGameParams(
    @SerialName("p_game_data")
    val gameData: CustomGameInfo,  // Send as object, not JSON string
    @SerialName("p_description")
    val description: String = "",
    @SerialName("p_submitted_by_name")
    val submittedByName: String = "",
    @SerialName("p_submission_password_hash")
    val passwordHash: String
)

/**
 * Parameters for checking submission status
 */
@Serializable
data class CheckStatusParams(
    @SerialName("p_submission_id")
    val submissionId: String,
    @SerialName("p_password_hash")
    val passwordHash: String
)

/**
 * Parameters for updating a submission
 */
@Serializable
data class UpdateSubmissionParams(
    @SerialName("p_submission_id")
    val submissionId: String,
    @SerialName("p_password_hash")
    val passwordHash: String,
    @SerialName("p_game_data")
    val gameData: CustomGameInfo,  // Send as object, not JSON string
    @SerialName("p_description")
    val description: String = ""
)

sealed class SubmissionResult {
    data class Success(val submissionId: String) : SubmissionResult()
    data class Error(val message: String) : SubmissionResult()
}

sealed class StatusResult {
    data class Success(val response: SubmissionStatusResponse) : StatusResult()
    data class Error(val message: String) : StatusResult()
}

object SubmissionService {
    private const val TAG = "SubmissionService"
    private const val PREFS_NAME = "submission_credentials"
    private const val KEY_CREDENTIALS = "credentials_list"
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    /**
     * Hash a password using SHA-256 (matches web editor)
     */
    fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Generate a random password
     */
    fun generatePassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..12).map { chars.random() }.joinToString("")
    }
    
    /**
     * Submit a game to the community
     */
    suspend fun submitGame(
        gameInfo: CustomGameInfo,
        description: String = "",
        submitterName: String = "",
        password: String
    ): SubmissionResult {
        return try {
            if (!SupabaseClient.isConfigured()) {
                return SubmissionResult.Error("Supabase not configured")
            }
            
            val passwordHash = hashPassword(password)
            
            val params = SubmitGameParams(
                gameData = gameInfo,
                description = description,
                submittedByName = submitterName,
                passwordHash = passwordHash
            )
            
            val response = SupabaseClient.client.postgrest
                .rpc("submit_community_game", params)
                .data
            
            val result = json.decodeFromString<JsonObject>(response)
            val success = result["success"]?.jsonPrimitive?.boolean ?: false
            
            if (success) {
                val submissionId = result["submission_id"]?.jsonPrimitive?.content
                if (submissionId != null) {
                    Log.d(TAG, "Game submitted successfully: $submissionId")
                    SubmissionResult.Success(submissionId)
                } else {
                    SubmissionResult.Error("No submission ID returned")
                }
            } else {
                val error = result["error"]?.jsonPrimitive?.content ?: "Unknown error"
                SubmissionResult.Error(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting game", e)
            SubmissionResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Check the status of a submission
     */
    suspend fun checkStatus(submissionId: String, passwordHash: String): StatusResult {
        return try {
            if (!SupabaseClient.isConfigured()) {
                return StatusResult.Error("Supabase not configured")
            }
            
            val params = CheckStatusParams(submissionId, passwordHash)
            
            val response = SupabaseClient.client.postgrest
                .rpc("check_submission_status", params)
                .data
            
            val result = json.decodeFromString<SubmissionStatusResponse>(response)
            
            if (result.success) {
                StatusResult.Success(result)
            } else {
                StatusResult.Error(result.error ?: "Unknown error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking status", e)
            StatusResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Update an existing submission (resets to pending)
     */
    suspend fun updateSubmission(
        submissionId: String,
        passwordHash: String,
        gameInfo: CustomGameInfo,
        description: String = ""
    ): SubmissionResult {
        return try {
            if (!SupabaseClient.isConfigured()) {
                return SubmissionResult.Error("Supabase not configured")
            }
            
            val params = UpdateSubmissionParams(
                submissionId = submissionId,
                passwordHash = passwordHash,
                gameData = gameInfo,
                description = description
            )
            
            val response = SupabaseClient.client.postgrest
                .rpc("update_submission", params)
                .data
            
            val result = json.decodeFromString<JsonObject>(response)
            val success = result["success"]?.jsonPrimitive?.boolean ?: false
            
            if (success) {
                Log.d(TAG, "Submission updated: $submissionId")
                SubmissionResult.Success(submissionId)
            } else {
                val error = result["error"]?.jsonPrimitive?.content ?: "Unknown error"
                SubmissionResult.Error(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating submission", e)
            SubmissionResult.Error(e.message ?: "Unknown error")
        }
    }
    
    // ========== Local Credential Storage ==========
    
    /**
     * Save a submission credential locally
     */
    fun saveCredential(context: Context, credential: SubmissionCredential) {
        val credentials = getStoredCredentials(context).toMutableList()
        
        // Remove existing credential for same folder if exists
        credentials.removeAll { it.folderName == credential.folderName }
        credentials.add(credential)
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CREDENTIALS, json.encodeToString(credentials)).apply()
        
        Log.d(TAG, "Saved credential for: ${credential.folderName}")
    }
    
    /**
     * Get all stored credentials
     */
    fun getStoredCredentials(context: Context): List<SubmissionCredential> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_CREDENTIALS, null) ?: return emptyList()
        
        return try {
            json.decodeFromString<List<SubmissionCredential>>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading credentials", e)
            emptyList()
        }
    }
    
    /**
     * Get credential for a specific folder
     */
    fun getCredentialForFolder(context: Context, folderName: String): SubmissionCredential? {
        return getStoredCredentials(context).find { it.folderName == folderName }
    }
    
    /**
     * Update cached status for a credential
     */
    fun updateCachedStatus(context: Context, folderName: String, status: String, 
                           voteCount: Int = 0, downloadCount: Int = 0, rejectionReason: String? = null) {
        val credentials = getStoredCredentials(context).toMutableList()
        val index = credentials.indexOfFirst { it.folderName == folderName }
        
        if (index >= 0) {
            val updated = credentials[index].copy(
                cachedStatus = status,
                cachedVoteCount = voteCount,
                cachedDownloadCount = downloadCount,
                rejectionReason = rejectionReason
            )
            credentials[index] = updated
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CREDENTIALS, json.encodeToString(credentials)).apply()
        }
    }
    
    /**
     * Remove a credential
     */
    fun removeCredential(context: Context, folderName: String) {
        val credentials = getStoredCredentials(context).toMutableList()
        credentials.removeAll { it.folderName == folderName }
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CREDENTIALS, json.encodeToString(credentials)).apply()
    }
}

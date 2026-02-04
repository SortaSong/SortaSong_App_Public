package com.sortasong.sortasong.data

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

@Serializable
data class TrackReportParams(
    @SerialName("p_track_id")
    val trackId: Int,
    @SerialName("p_suggested_song")
    val suggestedSong: String,
    @SerialName("p_suggested_artist")
    val suggestedArtist: String,
    @SerialName("p_suggested_release_date")
    val suggestedReleaseDate: String,
    @SerialName("p_suggested_release_year")
    val suggestedReleaseYear: Int? = null,
    @SerialName("p_user_comment")
    val userComment: String,
    @SerialName("p_reporter_id")
    val reporterId: String
)

sealed class ReportResult {
    data object Success : ReportResult()
    data object Duplicate : ReportResult()
    data class Error(val message: String) : ReportResult()
}

object TrackReportService {
    private const val TAG = "TrackReportService"
    private const val PREFS_NAME = "track_report_prefs"
    private const val KEY_REPORTER_ID = "reporter_id"

    /**
     * Get or create a unique anonymous reporter ID for this device.
     */
    fun getReporterId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var reporterId = prefs.getString(KEY_REPORTER_ID, null)
        
        if (reporterId == null) {
            // Generate a new UUID for this device
            reporterId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_REPORTER_ID, reporterId).apply()
        }
        
        return reporterId
    }

    suspend fun submitReport(params: TrackReportParams): ReportResult {
        return try {
            if (!SupabaseClient.isConfigured()) {
                return ReportResult.Error("Supabase not configured")
            }

            val response = SupabaseClient.client.postgrest
                .rpc("submit_track_report", params)
                .data  // Get raw JSON string
            
            val result = kotlinx.serialization.json.Json.decodeFromString<JsonObject>(response)

            val success = result["success"]?.jsonPrimitive?.boolean ?: false
            val reason = result["reason"]?.jsonPrimitive?.content
            
            if (success) {
                Log.d(TAG, "Track report submitted successfully")
                ReportResult.Success
            } else if (reason == "duplicate") {
                Log.d(TAG, "Duplicate report detected")
                ReportResult.Duplicate
            } else {
                ReportResult.Error("Unknown error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting track report", e)
            ReportResult.Error(e.message ?: "Unknown error")
        }
    }
}

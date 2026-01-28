package com.sortasong.sortasong.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sortasong.sortasong.GameRepository
import kotlinx.coroutines.delay

class TrackVerificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TrackVerificationWorker"
        const val WORK_NAME = "track_verification"
        const val KEY_FOLDER_URI = "folder_uri"

        // Progress keys
        const val PROGRESS_CURRENT = "current"
        const val PROGRESS_TOTAL = "total"
        const val PROGRESS_FOLDER = "folder"

        // SharedPreferences keys
        private const val PREFS_NAME = "track_verification"
        private const val KEY_LAST_TRACK_ID = "last_track_id"
        private const val KEY_IS_RUNNING = "is_running"
    }

    /**
     * Check which game folders exist in the selected directory.
     */
    private suspend fun getExistingGameFolders(folderUri: Uri): Set<String> {
        val rootFolder = DocumentFile.fromTreeUri(applicationContext, folderUri)
        if (rootFolder == null || !rootFolder.isDirectory) {
            Log.e(TAG, "Invalid root folder")
            return emptySet()
        }

        // Get all games from database
        val db = GameRepository.getDatabase(applicationContext)
        val allGames = db.gameDao().getAllGames()

        // Check which folders exist
        val existingFolders = mutableSetOf<String>()
        val subFolders = rootFolder.listFiles()

        for (game in allGames) {
            val exists = subFolders.any {
                it.isDirectory && it.name == game.folderName
            }
            if (exists) {
                existingFolders.add(game.folderName)
            }
        }

        Log.d(TAG, "Found ${existingFolders.size} existing game folders")
        return existingFolders
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "========================================")
        Log.d(TAG, "Worker started - ID: ${id}")
        Log.d(TAG, "========================================")

        // Set up foreground service
        try {
            setForeground(getForegroundInfo())
            Log.d(TAG, "Foreground service notification shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set foreground", e)
        }

        val folderUriString = inputData.getString(KEY_FOLDER_URI)
        if (folderUriString == null) {
            Log.e(TAG, "No folder URI provided")
            return Result.failure()
        }

        Log.d(TAG, "Folder URI: $folderUriString")

        val folderUri = folderUriString.toUri()
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Mark as running
        prefs.edit().putBoolean(KEY_IS_RUNNING, true).apply()
        Log.d(TAG, "Marked as running in SharedPreferences")

        try {
            // Load data if not already loaded
            if (GameRepository.games.isEmpty()) {
                Log.d(TAG, "Loading data from cache...")
                GameRepository.loadFromCacheOnly(applicationContext)

                // Filter to only enabled games (those with existing folders)
                val existingFolders = getExistingGameFolders(folderUri)
                if (existingFolders.isNotEmpty()) {
                    GameRepository.filterGamesByAvailableFolders(
                        applicationContext,
                        existingFolders
                    )
                    Log.d(TAG, "Filtered to ${existingFolders.size} existing folders")
                } else {
                    Log.e(TAG, "No game folders found")
                    return Result.failure()
                }
            }

            // Get all tracks that need verification (only from enabled games)
            val tracksToVerify = GameRepository.getTracksNeedingVerification()
            val totalTracks = GameRepository.getTotalTrackCount()
            val alreadyVerified = GameRepository.getVerifiedTrackCount()

            if (tracksToVerify.isEmpty()) {
                Log.d(TAG, "No tracks need verification (all $totalTracks already verified as AVAILABLE)")
                prefs.edit()
                    .remove(KEY_LAST_TRACK_ID)
                    .putBoolean(KEY_IS_RUNNING, false)
                    .apply()
                return Result.success()
            }

            Log.d(TAG, "Starting verification: $alreadyVerified verified as AVAILABLE, ${tracksToVerify.size} to verify (NOT_VERIFIED or UNAVAILABLE), $totalTracks total")

            // Resume from last position if interrupted
            val lastTrackId = prefs.getInt(KEY_LAST_TRACK_ID, -1)
            val startIndex = if (lastTrackId != -1) {
                tracksToVerify.indexOfFirst { it.trackId == lastTrackId } + 1
            } else {
                0
            }

            Log.d(TAG, "Resuming from index $startIndex")

            // Verify each track
            for (i in startIndex until tracksToVerify.size) {
                if (isStopped) {
                    Log.d(TAG, "Worker stopped by system")
                    return Result.retry()
                }

                val track = tracksToVerify[i]

                // Current progress = already verified + tracks verified so far + 1
                val currentProgress = alreadyVerified + i + 1

                // Update progress
                setProgress(workDataOf(
                    PROGRESS_CURRENT to currentProgress,
                    PROGRESS_TOTAL to totalTracks,
                    PROGRESS_FOLDER to track.folderName
                ))

                // Verify the track file
                try {
                    val availability = GameRepository.verifyTrackFile(
                        applicationContext,
                        track,
                        folderUri
                    )
                    Log.d(TAG, "Track $currentProgress/$totalTracks: ${track.artist} - ${track.song} -> $availability")
                } catch (e: Exception) {
                    Log.e(TAG, "Error verifying track: ${track.artist} - ${track.song}", e)
                }

                // Save progress
                prefs.edit().putInt(KEY_LAST_TRACK_ID, track.trackId).apply()

                // Small delay to avoid overloading (100ms per track)
                delay(100)
            }

            Log.d(TAG, "Verification complete")

            // Clear progress
            prefs.edit()
                .remove(KEY_LAST_TRACK_ID)
                .putBoolean(KEY_IS_RUNNING, false)
                .apply()

            return Result.success(workDataOf(
                PROGRESS_CURRENT to totalTracks,
                PROGRESS_TOTAL to totalTracks
            ))

        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            prefs.edit().putBoolean(KEY_IS_RUNNING, false).apply()
            return Result.failure()
        }
    }

    override suspend fun getForegroundInfo() =
        TrackVerificationForegroundInfo.createForegroundInfo(applicationContext)
}

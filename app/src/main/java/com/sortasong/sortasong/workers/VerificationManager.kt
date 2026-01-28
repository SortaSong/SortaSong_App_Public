package com.sortasong.sortasong.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object VerificationManager {
    private const val TAG = "VerificationManager"
    private const val PREFS_NAME = "track_verification"
    private const val KEY_IS_RUNNING = "is_running"

    /**
     * Start track verification in the background.
     * If already running, does nothing.
     */
    fun startVerification(context: Context, folderUri: Uri) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "Starting verification for folder: $folderUri")
        Log.d(TAG, "========================================")

        val inputData = workDataOf(
            TrackVerificationWorker.KEY_FOLDER_URI to folderUri.toString()
        )

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<TrackVerificationWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        Log.d(TAG, "Work request created: ${workRequest.id}")

        WorkManager.getInstance(context).enqueueUniqueWork(
            TrackVerificationWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE, // Replace any existing work
            workRequest
        )

        Log.d(TAG, "Work enqueued with REPLACE policy")
    }

    /**
     * Cancel running verification.
     */
    fun cancelVerification(context: Context) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(TrackVerificationWorker.WORK_NAME)

        // Clear running flag
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_RUNNING, false)
            .apply()
    }

    /**
     * Check if verification is currently running.
     */
    fun isVerificationRunning(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_RUNNING, false)
    }

    /**
     * Get live progress updates as a Flow.
     * Returns (current, total, folderName)
     */
    fun getVerificationProgress(context: Context): Flow<VerificationProgress> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(TrackVerificationWorker.WORK_NAME)
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull()
                when {
                    workInfo == null -> VerificationProgress.NotRunning
                    workInfo.state == WorkInfo.State.RUNNING -> {
                        val current = workInfo.progress.getInt(
                            TrackVerificationWorker.PROGRESS_CURRENT,
                            0
                        )
                        val total = workInfo.progress.getInt(
                            TrackVerificationWorker.PROGRESS_TOTAL,
                            0
                        )
                        val folder = workInfo.progress.getString(
                            TrackVerificationWorker.PROGRESS_FOLDER
                        ) ?: ""
                        VerificationProgress.Running(current, total, folder)
                    }
                    workInfo.state == WorkInfo.State.SUCCEEDED -> {
                        VerificationProgress.Completed
                    }
                    workInfo.state == WorkInfo.State.FAILED -> {
                        VerificationProgress.Failed
                    }
                    else -> VerificationProgress.NotRunning
                }
            }
    }
}

sealed class VerificationProgress {
    data object NotRunning : VerificationProgress()
    data class Running(val current: Int, val total: Int, val folderName: String) : VerificationProgress()
    data object Completed : VerificationProgress()
    data object Failed : VerificationProgress()
}

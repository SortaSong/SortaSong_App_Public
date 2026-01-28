package com.sortasong.sortasong.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.sortasong.sortasong.R

object TrackVerificationForegroundInfo {
    private const val CHANNEL_ID = "track_verification"
    private const val NOTIFICATION_ID = 1

    fun createForegroundInfo(context: Context): ForegroundInfo {
        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Musikdateien überprüfen")
            .setContentText("Überprüfe verfügbare Tracks...")
            .setSmallIcon(R.drawable.sortasound_logo_with_name)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Track Verification",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress when verifying music files"
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

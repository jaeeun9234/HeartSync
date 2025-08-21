package com.example.heartsync.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.heartsync.R

object NotificationHelper {
    const val CHANNEL_ID = "heartsync_measure"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val chan = NotificationChannel(CHANNEL_ID, "HeartSync Measure", NotificationManager.IMPORTANCE_LOW)
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(chan)
        }
    }

    fun build(ctx: Context, text: String): Notification {
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("HeartSync")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }
}

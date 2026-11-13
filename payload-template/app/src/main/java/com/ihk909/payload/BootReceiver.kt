package com.ihk909.payload

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        ContextCompat.startForegroundService(ctx, Intent(ctx, PersistentService::class.java))
        PersistentWorker.schedule(ctx)
    }
}
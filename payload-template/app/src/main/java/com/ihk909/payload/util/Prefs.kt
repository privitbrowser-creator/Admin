package com.ihk909.payload.util

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("ihk909", Context.MODE_PRIVATE)
}
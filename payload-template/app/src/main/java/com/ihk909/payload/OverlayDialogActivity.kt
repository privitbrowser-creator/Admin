package com.ihk909.payload

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle

class OverlayDialogActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertDialog.Builder(this)
            .setTitle(intent.getStringExtra("title") ?: "System")
            .setMessage(intent.getStringExtra("msg") ?: "")
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
}
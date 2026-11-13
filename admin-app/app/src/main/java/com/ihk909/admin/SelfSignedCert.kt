package com.ihk909.admin

import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

object SelfSignedCert {
    fun generate(
        kp: KeyPair,
        subject: X500Principal,
        from: Date,
        to: Date
    ): X509Certificate {
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val builder = JcaX509v3CertificateBuilder(
            subject, serial, from, to, subject, kp.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val holder = builder.build(signer)
        return JcaX509CertificateConverter().getCertificate(holder)
    }
}
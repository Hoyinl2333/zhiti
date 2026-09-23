package com.xiaoyunduo.zhiti.data.api

import android.content.Context
import android.util.Base64
import com.xiaoyunduo.zhiti.R
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class CatalogVerifier(context: Context) {
    private val provider = BouncyCastleProvider()
    private val publicKey = context.resources.openRawResource(R.raw.content_signing_public).bufferedReader().use { reader ->
        val encoded = reader.readText()
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s"), "")
        KeyFactory.getInstance("Ed25519", provider).generatePublic(X509EncodedKeySpec(Base64.decode(encoded, Base64.DEFAULT)))
    }

    fun verify(content: ByteArray, signatureBase64: String): Boolean = runCatching {
        val verifier = Signature.getInstance("Ed25519", provider)
        verifier.initVerify(publicKey)
        verifier.update(content)
        verifier.verify(Base64.decode(signatureBase64, Base64.DEFAULT))
    }.getOrDefault(false)
}

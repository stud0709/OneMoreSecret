package com.onemoresecret.crypto

import org.onemoresecret.protocol.OmsProtocol
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Re-encrypts an existing RSA pair in the latest backup format without importing it. */
object PrivateKeyBackup {
    fun create(
        alias: String,
        privateKey: ByteArray,
        publicKey: ByteArray,
        password: CharArray,
        iterations: Int
    ): ByteArray {
        val aliasBytes = alias.toByteArray(Charsets.UTF_8)
        require(alias.isNotBlank()) { "Key alias must not be empty" }
        require(aliasBytes.size <= 4096 && privateKey.size <= 16384 && publicKey.size <= 16384) { "Backup field too large" }
        require(iterations in 1..OmsProtocol.MAX_ITERATIONS) { "Unsupported PBKDF2 iteration count" }
        OmsProtocol.validateKeyPair(privateKey, publicKey)
        val salt = OmsProtocol.random(16)
        val spec = PBEKeySpec(password, salt, iterations, 256)
        var key: ByteArray? = null
        var plain: ByteArray? = null
        val body = ByteArrayOutputStream()
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            DataOutputStream(body).use { out ->
                OmsProtocol.writeBlob(out, aliasBytes)
                OmsProtocol.writeBlob(out, privateKey)
                OmsProtocol.writeBlob(out, publicKey)
            }
            plain = body.toByteArray()
            return OmsProtocol.passwordSeal(key, salt, iterations, 0, plain)
        } finally {
            spec.clearPassword()
            key?.fill(0)
            plain?.fill(0)
            // Clear the stream's copy of the private key too.
            val length = body.size()
            body.reset()
            repeat(length) { body.write(0) }
            body.reset()
        }
    }
}

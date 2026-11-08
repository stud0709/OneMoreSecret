package com.onemoresecret.crypto

import org.onemoresecret.protocol.OmsProtocol

/** Upgrade the original decrypted application body, never a displayed TOTP code or WIF string. */
object SecretFormatUpgrade {
    fun supports(applicationId: Int?) = applicationId in setOf(1, 2, 7, 8, 9, 12)

    fun encrypt(publicKey: ByteArray, applicationId: Int, body: ByteArray): String {
        require(supports(applicationId)) { "This application cannot be upgraded as a secret" }
        val currentId = when (applicationId) { 1 -> 8; 2 -> 9; else -> applicationId }
        return OmsProtocol.encodeText(OmsProtocol.seal(RSAUtil.restorePublicKey(publicKey), currentId, body))
    }
}

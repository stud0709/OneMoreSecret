package com.onemoresecret.crypto

import org.junit.Assert.*
import org.junit.Test
import org.onemoresecret.protocol.OmsProtocol
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher

class SecretFormatUpgradeTest {
    @Test fun upgradesEverySecretTypeWithoutConvertingItsOriginalBytes() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        for (app in listOf(1, 2, 7, 8, 9, 12)) {
            val body = if (app == 7) BTCAddress.toWIF(ByteArray(32).apply { this[31] = 1 }) else "original secret – 🔐 $app".toByteArray()
            val original = body.copyOf()
            val result = OmsProtocol.decodeText(SecretFormatUpgrade.encrypt(keys.public.encoded, app, body))!!
            val input = DataInputStream(ByteArrayInputStream(result))
            val header = OmsProtocol.readHeader(input)
            assertArrayEquals(OmsProtocol.fingerprint(keys.public as RSAPublicKey), header.fingerprint)
            val aes = OmsProtocol.rsa(Cipher.DECRYPT_MODE, keys.private, header.wrappedKey)
            val plain = DataInputStream(ByteArrayInputStream(OmsProtocol.open(header, aes, input.readAllBytes())))
            assertEquals(when (app) { 1 -> 8; 2 -> 9; else -> app }, OmsProtocol.readApplication(plain))
            assertArrayEquals(original, plain.readAllBytes())
            assertArrayEquals(original, body)
        }
    }
    @Test fun doesNotTreatControlsFilesBackupsOrPairingsAsPortableSecrets() {
        for (app in listOf(0, 3, 4, 5, 6, 10, 11, 13, 65535)) {
            assertFalse(SecretFormatUpgrade.supports(app))
            assertThrows(IllegalArgumentException::class.java) { SecretFormatUpgrade.encrypt(byteArrayOf(), app, byteArrayOf()) }
        }
        assertFalse(SecretFormatUpgrade.supports(null))
    }
}

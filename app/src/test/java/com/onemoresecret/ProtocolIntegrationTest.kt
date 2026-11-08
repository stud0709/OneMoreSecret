package com.onemoresecret

import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.AesTransformation
import com.onemoresecret.crypto.RsaTransformation
import org.onemoresecret.protocol.NostrPairing
import org.onemoresecret.protocol.OmsProtocol
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import javax.crypto.Cipher

class ProtocolIntegrationTest {
    @Test fun pairingSeparatesRequestTimeoutAndSessionCleanup() {
        val topic = "ab".repeat(32)
        val psk = OmsProtocol.random(32)
        val relays = listOf("wss://relay.damus.io", "wss://nos.lol")
        val pairing = NostrPairing.read(NostrPairing.create(topic, psk, 120, 86400, relays))
        assertEquals(topic, pairing.topic)
        assertArrayEquals(psk, pairing.psk)
        assertEquals(120, pairing.requestTimeout)
        assertEquals(86400, pairing.sessionTtl)
        assertEquals(relays, pairing.relays)
        assertThrows(java.io.IOException::class.java) { NostrPairing.read(byteArrayOf(0, 64)) }
    }
    @Test fun encryptedPairingUsesVersionedGenericEnvelope() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val body = NostrPairing.create("ab".repeat(32), OmsProtocol.random(32), 120, 86400, listOf("wss://nos.lol"))
        val container = ByteArrayOutputStream().also { out ->
            OmsDataOutputStream(out).use { it.writeUnsignedShort(10); it.writeByteArray(body) }
        }.toByteArray()
        val bytes = MessageComposer.createRsaAesEnvelope(pair.public.encoded, RsaTransformation.entries[0], 128, AesTransformation.entries[0], container)
        val text = MessageComposer.encodeAsOmsText(bytes)
        assertFalse(text.startsWith("oms00_"))
        assertArrayEquals(bytes, MessageComposer.decode(text))
        val input = java.io.DataInputStream(ByteArrayInputStream(bytes))
        val header = OmsProtocol.readHeader(input)
        val key = OmsProtocol.rsa(Cipher.DECRYPT_MODE, pair.private, header.wrappedKey)
        val plain = java.io.DataInputStream(ByteArrayInputStream(OmsProtocol.open(header, key, input.readAllBytes())))
        assertEquals(10, OmsProtocol.readApplication(plain))
        assertArrayEquals(body, plain.readAllBytes())
    }
    @Test fun legacyLengthsRejectOverflowAndHandleShortReads() {
        assertThrows(IllegalArgumentException::class.java) { OmsDataOutputStream(ByteArrayOutputStream()).writeUnsignedShort(600000) }
        assertThrows(IllegalArgumentException::class.java) { OmsDataOutputStream(ByteArrayOutputStream()).writeUnsignedShort(-1) }
        val source = object : ByteArrayInputStream(byteArrayOf(0, 3, 1, 2, 3)) {
            override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(len, 1))
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), OmsDataInputStream(source).readByteArray())
    }
}

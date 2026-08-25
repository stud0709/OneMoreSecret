package com.onemoresecret

import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.nostr.NostrPskCrypto
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

class ExampleUnitTest {
    @Test
    fun testNostrPairingQrSerializationWithPsk() {
        val topic = "7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069"
        val psk = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val ttl = 60
        val relays = listOf("wss://relay.damus.io", "wss://nos.lol")

        // 1. Serialize on the simulated desktop/web client side
        val baos = ByteArrayOutputStream()
        val dos = OmsDataOutputStream(baos)
        dos.writeUnsignedShort(MessageComposer.APPLICATION_NOSTR_PAIRING) // 10
        dos.writeString(topic)
        dos.writeByteArray(psk)
        dos.writeUnsignedShort(ttl)
        dos.writeUnsignedShort(relays.size)
        for (relay in relays) {
            dos.writeString(relay)
        }
        val serializedBytes = baos.toByteArray()
        val qrCodeText = MessageComposer.encodeAsOmsText(serializedBytes)

        // Verify it starts with the correct prefix
        assertTrue(qrCodeText.startsWith("oms00_"))

        // 2. Deserialize on the simulated Android app side
        val decodedBytes = MessageComposer.decode(qrCodeText)
        assertNotNull(decodedBytes)

        val bais = ByteArrayInputStream(decodedBytes!!)
        val dis = OmsDataInputStream(bais)
        
        val parsedAppId = dis.readUnsignedShort()
        assertEquals(MessageComposer.APPLICATION_NOSTR_PAIRING, parsedAppId)

        val parsedTopic = dis.readString()
        assertEquals(topic, parsedTopic)

        val parsedPsk = dis.readByteArray()
        assertArrayEquals(psk, parsedPsk)

        val parsedTtl = dis.readUnsignedShort()
        assertEquals(ttl, parsedTtl)

        val parsedRelayCount = dis.readUnsignedShort()
        assertEquals(relays.size, parsedRelayCount)

        val parsedRelays = (0 until parsedRelayCount).map { dis.readString() }
        assertEquals(relays, parsedRelays)

        // 3. Test NostrPskCrypto AES-256-GCM encryption & decryption
        val plaintext = "{\"type\":\"request\",\"payload\":\"secret_data\"}".toByteArray(Charsets.UTF_8)
        val encrypted = NostrPskCrypto.encrypt(plaintext, psk)
        val decrypted = NostrPskCrypto.decrypt(encrypted, psk)
        assertArrayEquals(plaintext, decrypted)

        // 4. Test wrong PSK rejection
        val wrongPsk = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        try {
            NostrPskCrypto.decrypt(encrypted, wrongPsk)
            fail("Decryption with wrong PSK should fail authentication")
        } catch (e: Exception) {
            // Expected AEADBadTagException
        }

        println("Nostr QR code PSK serialization and AES-256-GCM integration test passed successfully!")
    }

    @Test
    fun testRsaEncryptedNostrPairingQr() {
        // 1. Generate RSA key pair for testing
        val kpg = java.security.KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()

        val topic = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        val psk = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val ttl = 120
        val relays = listOf("wss://relay.damus.io", "wss://nos.lol")

        // 2. Build inner APPLICATION_NOSTR_PAIRING payload
        val innerBaos = ByteArrayOutputStream()
        val innerDos = OmsDataOutputStream(innerBaos)
        innerDos.writeString(topic)
        innerDos.writeByteArray(psk)
        innerDos.writeUnsignedShort(ttl)
        innerDos.writeUnsignedShort(relays.size)
        for (relay in relays) {
            innerDos.writeString(relay)
        }
        val pairingBytes = innerBaos.toByteArray()

        // 3. Wrap inner payload in APPLICATION_NOSTR_PAIRING container
        val containerBaos = ByteArrayOutputStream()
        val containerDos = OmsDataOutputStream(containerBaos)
        containerDos.writeUnsignedShort(MessageComposer.APPLICATION_NOSTR_PAIRING)
        containerDos.writeByteArray(pairingBytes)
        val containerPayload = containerBaos.toByteArray()

        // 4. Encrypt with Master RSA Public Key into APPLICATION_RSA_AES_GENERIC envelope
        val rsaTransformation = com.onemoresecret.crypto.RsaTransformation.RSA_ECB_PKCS1Padding
        val aesTransformation = com.onemoresecret.crypto.AesTransformation.AES_GCM_NO_PADDING
        val encryptedEnvelopeBytes = MessageComposer.createRsaAesEnvelope(
            keyPair.public.encoded,
            rsaTransformation,
            256,
            aesTransformation,
            containerPayload
        )

        val encryptedQrText = MessageComposer.encodeAsOmsText(encryptedEnvelopeBytes)
        assertTrue(encryptedQrText.startsWith("oms00_"))

        // 5. Simulate OneMoreSecret reading and decrypting the outer RSA-AES envelope
        val decodedEnvelopeBytes = MessageComposer.decode(encryptedQrText)
        assertNotNull(decodedEnvelopeBytes)

        val envelopeStream = OmsDataInputStream(ByteArrayInputStream(decodedEnvelopeBytes!!))
        val parsedApplicationId = envelopeStream.readUnsignedShort()
        val parsedRsaTransformation = com.onemoresecret.crypto.RsaTransformation.entries[envelopeStream.readUnsignedShort()]
        val parsedFingerprint = envelopeStream.readByteArray()
        val parsedAesTransformation = com.onemoresecret.crypto.AesTransformation.entries[envelopeStream.readUnsignedShort()]
        val parsedIv = envelopeStream.readByteArray()
        val parsedEncryptedAesKey = envelopeStream.readByteArray()
        val cipherText = envelopeStream.readByteArray()

        assertEquals(MessageComposer.APPLICATION_RSA_AES_GENERIC, parsedApplicationId)

        // Decrypt AES session key with RSA private key
        val rsaCipher = javax.crypto.Cipher.getInstance(parsedRsaTransformation.transformation)
        rsaCipher.init(javax.crypto.Cipher.DECRYPT_MODE, keyPair.private)
        val decryptedAesKey = rsaCipher.doFinal(parsedEncryptedAesKey)

        // Decrypt payload with AES-GCM
        val decryptedPayload = com.onemoresecret.crypto.AESUtil.process(
            javax.crypto.Cipher.DECRYPT_MODE,
            cipherText,
            decryptedAesKey,
            parsedIv,
            parsedAesTransformation
        )

        // 6. Inspect decrypted inner message (as in QrMessageHandler.afterDecrypt)
        val payloadStream = OmsDataInputStream(ByteArrayInputStream(decryptedPayload))
        val innerAppId = payloadStream.readUnsignedShort()
        assertEquals(MessageComposer.APPLICATION_NOSTR_PAIRING, innerAppId)

        val extractedPairingBytes = payloadStream.readByteArray()
        assertArrayEquals(pairingBytes, extractedPairingBytes)

        // 7. Parse the extracted pairing data (as in MsgPluginNostrPairing)
        val pairingStream = OmsDataInputStream(ByteArrayInputStream(extractedPairingBytes))
        val parsedTopic = pairingStream.readString()
        val parsedPsk = pairingStream.readByteArray()
        val parsedTtl = pairingStream.readUnsignedShort()
        val parsedRelayCount = pairingStream.readUnsignedShort()
        val parsedRelays = (0 until parsedRelayCount).map { pairingStream.readString() }

        assertEquals(topic, parsedTopic)
        assertArrayEquals(psk, parsedPsk)
        assertEquals(ttl, parsedTtl)
        assertEquals(relays, parsedRelays)

        println("Master RSA encrypted Nostr pairing QR code test passed successfully!")
    }
}

package com.onemoresecret

import com.onemoresecret.crypto.MessageComposer
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExampleUnitTest {
    @Test
    fun testNostrPairingQrSerialization() {
        val topic = "7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069"
        val ttl = 60
        val relays = listOf("wss://relay.damus.io", "wss://nos.lol")

        // 1. Serialize on the simulated desktop/web client side
        val baos = ByteArrayOutputStream()
        val dos = OmsDataOutputStream(baos)
        dos.writeUnsignedShort(MessageComposer.APPLICATION_NOSTR_PAIRING) // 10
        dos.writeString(topic)
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

        val parsedTtl = dis.readUnsignedShort()
        assertEquals(ttl, parsedTtl)

        val parsedRelayCount = dis.readUnsignedShort()
        assertEquals(relays.size, parsedRelayCount)

        val parsedRelays = (0 until parsedRelayCount).map { dis.readString() }
        assertEquals(relays, parsedRelays)

        println("Nostr QR code serialization/deserialization integration test passed successfully!")
    }
}

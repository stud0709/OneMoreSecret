package com.onemoresecret

import com.onemoresecret.crypto.BTCAddress
import com.onemoresecret.crypto.Base58
import com.onemoresecret.crypto.SecretFormatUpgrade
import org.junit.Assert.*
import org.junit.Test
import org.onemoresecret.protocol.OmsProtocol
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.security.KeyPairGenerator
import javax.crypto.Cipher

class BitcoinBackupDocumentTest {
    @Test fun updatedDocumentRestoresTheSameWalletAndContainsNoPlaintextWif() {
        // Published scalar-one test wallet. Never use this key for real funds.
        val wifText = "5HpHagT65TZzG1PH3CSu63k8DbpvD8s5ip4nEB3kEsreAnchuDf"
        val wif = Base58.decode(wifText)
        val original = wif.copyOf()
        val address = BTCAddress.addressFromWif(wif)
        assertEquals("1EHNa6Q4Jz2uvNExL497mE43ikXhwF6kZm", address)
        val recipient = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val encrypted = SecretFormatUpgrade.encrypt(recipient.public.encoded, 7, wif)
        val html = BitcoinBackupDocument.renderHtml(address, encrypted, "publicQr", listOf("keyQr1", "keyQr2"))

        assertTrue(html.contains("<b>$address</b>"))
        assertTrue(html.contains("data:image/png;base64,publicQr"))
        assertTrue(html.contains("data:image/png;base64,keyQr1"))
        assertTrue(html.contains("data:image/png;base64,keyQr2"))
        assertFalse(html.contains(wifText))
        assertFalse(html.contains(java.util.Base64.getEncoder().encodeToString(wif)))
        val text = html.substringAfter("<p style=\"font-family:monospace;\">").substringBefore("</p>").replace("<br>", "")
        assertEquals(encrypted, text)

        val input = DataInputStream(ByteArrayInputStream(OmsProtocol.decodeText(text)!!))
        val header = OmsProtocol.readHeader(input)
        val aes = OmsProtocol.rsa(Cipher.DECRYPT_MODE, recipient.private, header.wrappedKey)
        val plain = DataInputStream(ByteArrayInputStream(OmsProtocol.open(header, aes, input.readAllBytes())))
        assertEquals(7, OmsProtocol.readApplication(plain))
        val restored = plain.readAllBytes()
        assertArrayEquals(original, restored)
        assertEquals(address, BTCAddress.addressFromWif(restored))
        assertArrayEquals(original, wif)
    }
}

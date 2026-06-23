package com.onemoresecret.crypto

import org.junit.Test
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class BTCAddressTest {
    @Test
    fun ripemd160_matches_known_vector() {
        assertEquals(
            "8eb208f7e05d987a9b044a8e98c6b087f15a0bfc",
            toHex(Ripemd160.digest("abc".toByteArray()))
        )
    }

    @Test
    fun privateKeyOne_generates_expectedLegacyAddressAndWif() {
        val privateKey = hexToBytes("0000000000000000000000000000000000000000000000000000000000000001")
        val publicKey = hexToBytes(
            "0479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798" +
                    "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8"
        )

        assertEquals(
            "5HpHagT65TZzG1PH3CSu63k8DbpvD8s5ip4nEB3kEsreAnchuDf",
            Base58.encode(BTCAddress.toWIF(privateKey))
        )
        assertEquals(
            "1EHNa6Q4Jz2uvNExL497mE43ikXhwF6kZm",
            Base58.encode(BTCAddress.toBTCAddress(publicKey))
        )
        assertArrayEquals(privateKey, BTCAddress.toPrivateKey(BTCAddress.toWIF(privateKey)))
        assertTrue(BTCAddress.validateWIF("5HpHagT65TZzG1PH3CSu63k8DbpvD8s5ip4nEB3kEsreAnchuDf"))
    }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2) {
            result[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
        }
        return result
    }

    private fun toHex(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)
        for (value in bytes) {
            builder.append(String.format("%02x", value))
        }
        return builder.toString()
    }
}

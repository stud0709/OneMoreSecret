package com.onemoresecret.crypto;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BTCAddressTest {
    @Test
    public void ripemd160_matches_known_vector() {
        assertEquals(
                "8eb208f7e05d987a9b044a8e98c6b087f15a0bfc",
                toHex(Ripemd160.digest("abc".getBytes()))
        );
    }

    @Test
    public void privateKeyOne_generates_expectedLegacyAddressAndWif() {
        var privateKey = hexToBytes("0000000000000000000000000000000000000000000000000000000000000001");
        var publicKey = hexToBytes(
                "0479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798" +
                        "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8"
        );

        assertEquals(
                "5HpHagT65TZzG1PH3CSu63k8DbpvD8s5ip4nEB3kEsreAnchuDf",
                Base58.INSTANCE.encode(BTCAddress.INSTANCE.toWIF(privateKey))
        );
        assertEquals(
                "1EHNa6Q4Jz2uvNExL497mE43ikXhwF6kZm",
                Base58.INSTANCE.encode(BTCAddress.INSTANCE.toBTCAddress(publicKey))
        );
        assertArrayEquals(privateKey, BTCAddress.INSTANCE.toPrivateKey(BTCAddress.INSTANCE.toWIF(privateKey)));
        assertTrue(BTCAddress.INSTANCE.validateWIF("5HpHagT65TZzG1PH3CSu63k8DbpvD8s5ip4nEB3kEsreAnchuDf"));
    }

    private static byte[] hexToBytes(String hex) {
        var result = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            result[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return result;
    }

    private static String toHex(byte[] bytes) {
        var builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}

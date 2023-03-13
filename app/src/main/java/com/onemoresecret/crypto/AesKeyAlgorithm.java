package com.onemoresecret.crypto;

/**
 * See <a href="https://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html">javax.crypto.Cipher Java API</a> for
 * the list of supported transformations
 *
 * See <a href="https://developer.android.com/reference/javax/crypto/Cipher">java.crypto.Cipher Android API</a>
 *
 */
public enum AesKeyAlgorithm {
    PBKDF2WithHmacSHA256("PBKDF2WithHmacSHA256");

    public final String keyAlgorithm;

    AesKeyAlgorithm(String transformation) {
        this.keyAlgorithm = transformation;
    }
}

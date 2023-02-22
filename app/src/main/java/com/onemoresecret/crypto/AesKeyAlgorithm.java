package com.onemoresecret.crypto;

/**
 * See https://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html for
 * the list of supported transformations
 *
 * See https://developer.android.com/reference/javax/crypto/Cipher for Android
 *
 */
public enum AesKeyAlgorithm {
    PBKDF2WithHmacSHA256("PBKDF2WithHmacSHA256");

    public final String keyAlgorithm;

    AesKeyAlgorithm(String transformation) {
        this.keyAlgorithm = transformation;
    }
}

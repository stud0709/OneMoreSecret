package com.onemoresecret.crypto

/**
 * See [javax.crypto.Cipher Java API](https://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html) for
 * the list of supported transformations
 *
 * See [java.crypto.Cipher Android API](https://developer.android.com/reference/javax/crypto/Cipher)
 *
 */
enum class AesKeyAlgorithm(@JvmField val keyAlgorithm: String) {
    PBKDF2WithHmacSHA256("PBKDF2WithHmacSHA256");

}

package com.onemoresecret.crypto

/**
 * See [javax.crypto.Cipher Java API](https://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html) for
 * the list of supported transformations
 *
 * See [javax.crypto.Cipher Android API](https://developer.android.com/reference/javax/crypto/Cipher)
 *
 */
enum class AesTransformation(@JvmField val transformation: String) {
    AES_CBC_PKCS5Padding("AES/CBC/PKCS5Padding");

}

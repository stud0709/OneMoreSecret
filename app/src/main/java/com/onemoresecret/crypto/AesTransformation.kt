package com.onemoresecret.crypto;

/**
 * See <a href="https://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html">javax.crypto.Cipher Java API</a> for
 * the list of supported transformations
 *
 * See <a href="https://developer.android.com/reference/javax/crypto/Cipher">javax.crypto.Cipher Android API</a>
 *
 */
public enum AesTransformation {
    AES_CBC_PKCS5Padding("AES/CBC/PKCS5Padding");

    public final String transformation;

    AesTransformation(String transformation) {
        this.transformation = transformation;
    }
}

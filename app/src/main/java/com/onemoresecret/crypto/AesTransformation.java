package com.onemoresecret.crypto;

/**
 * See https://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html for
 * the list of supported transformations
 *
 * See https://developer.android.com/reference/javax/crypto/Cipher for Android
 *
 */
public enum AesTransformation {
    AES_CBC_PKCS5Padding("AES/CBC/PKCS5Padding");

    public final String transformation;

    AesTransformation(String transformation) {
        this.transformation = transformation;
    }
}

package com.onemoresecret.crypto;

/**
 * See <a href="https://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html">javax.crypto.Cipher Java API</a> for
 * the list of supported transformations
 *
 * See <a href="https://developer.android.com/reference/javax/crypto/Cipher">javax.crypto.Cipher Android API</a>
 *
 */
public enum RsaTransformation {
    RSA_ECB_PKCS1Padding("RSA/ECB/PKCS1Padding"),
    RSA_ECB_OAEPWithSHA_1AndMGF1Padding("RSA/ECB/OAEPWithSHA-1AndMGF1Padding"),
    RSA_ECB_OAEPWithSHA_256AndMGF1Padding("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");

    public final String transformation;

    RsaTransformation(String transformation) {
        this.transformation = transformation;
    }
}

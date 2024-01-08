package com.onemoresecret.crypto;

import android.content.SharedPreferences;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public final class RSAUtils {
    public static final String PROP_RSA_TRANSFORMATION_IDX = "rsa_transformation_idx";

    private RSAUtils() {

    }

    public static int getRsaTransformationIdx(SharedPreferences preferences) {
        return preferences.getInt(PROP_RSA_TRANSFORMATION_IDX, 0);
    }

    public static RsaTransformation getRsaTransformation(SharedPreferences preferences) {
        return RsaTransformation.values()[getRsaTransformationIdx(preferences)];
    }

    public static byte[] getFingerprint(RSAPublicKey publicKey) throws NoSuchAlgorithmException {
        var sha256 = MessageDigest.getInstance("SHA-256");
        sha256.update(publicKey.getModulus().toByteArray());
        return sha256.digest(publicKey.getPublicExponent().toByteArray());
    }

    public static RSAPublicKey restorePublicKey(byte[] encoded) throws NoSuchAlgorithmException, InvalidKeySpecException {
        var publicKeySpec = new X509EncodedKeySpec(encoded);
        var keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
    }

    public static byte[] process(int cipherMode, PublicKey rsaPublicKey, String transformation, byte[] data) throws
            NoSuchPaddingException,
            NoSuchAlgorithmException,
            InvalidKeyException,
            IllegalBlockSizeException,
            BadPaddingException {

        var cipher = Cipher.getInstance(transformation);
        cipher.init(cipherMode, rsaPublicKey);

        return cipher.doFinal(data);
    }
}

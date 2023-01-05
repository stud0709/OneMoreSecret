package com.onemoresecret.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class AESUtil {
    public static final String KEY_ALG_AES = "AES";

    public static SecretKey getKeyFromPassword(String password, byte[] salt, String keyAlgorithm, int keyLength, int keySpecIterations)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        SecretKeyFactory factory = SecretKeyFactory.getInstance(keyAlgorithm);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, keySpecIterations, keyLength);
        SecretKey secret = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), KEY_ALG_AES);
        return secret;
    }

    public static IvParameterSpec generateIv() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    public static SecretKey generateRandomSecretKey(int keyLength) {
        byte[] bArr = new byte[keyLength / 8];
        new SecureRandom().nextBytes(bArr);
        return new SecretKeySpec(bArr, KEY_ALG_AES);
    }

    public static byte[] encrypt(byte[] input, SecretKey key, IvParameterSpec iv, String aesTransformation)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException {

        Cipher cipher = Cipher.getInstance(aesTransformation);
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        return cipher.doFinal(input);
    }

    public static byte[] decrypt(byte[] cipherText, SecretKey key, IvParameterSpec iv, String aesTransformation)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException {

        Cipher cipher = Cipher.getInstance(aesTransformation);
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        return cipher.doFinal(cipherText);
    }
}

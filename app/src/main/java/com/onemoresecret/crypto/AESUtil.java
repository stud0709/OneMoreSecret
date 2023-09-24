package com.onemoresecret.crypto;

import android.content.SharedPreferences;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class AESUtil {
    private AESUtil() {
    }

    public static SecretKey getKeyFromPassword(char[] password, byte[] salt, String keyAlgorithm, int keyLength, int keySpecIterations)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        var factory = SecretKeyFactory.getInstance(keyAlgorithm);
        var spec = new PBEKeySpec(password, salt, keySpecIterations, keyLength);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    public static IvParameterSpec generateIv() {
        var iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    public static SecretKey generateRandomSecretKey(int keyLength) {
        var bArr = new byte[keyLength / 8];
        new SecureRandom().nextBytes(bArr);
        return new SecretKeySpec(bArr, "AES");
    }

    public static byte[] generateSalt(int saltLength) {
        var salt = new byte[saltLength];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    public static byte[] encrypt(byte[] input,
                                 SecretKey key,
                                 IvParameterSpec iv,
                                 String aesTransformation) throws
            NoSuchPaddingException,
            NoSuchAlgorithmException,
            InvalidAlgorithmParameterException,
            InvalidKeyException,
            BadPaddingException,
            IllegalBlockSizeException {

        var cipher = Cipher.getInstance(aesTransformation);
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        return cipher.doFinal(input);
    }

    /**
     * Encrypts input stream and calculates SHA-256 of the original data.
     */
    public static byte[] encryptAndCalculateSHA256(InputStream is,
                                                   OutputStream os,
                                                   SecretKey key,
                                                   IvParameterSpec iv,
                                                   String aesTransformation) throws
            NoSuchPaddingException,
            NoSuchAlgorithmException,
            InvalidAlgorithmParameterException,
            InvalidKeyException,
            BadPaddingException,
            IllegalBlockSizeException,
            IOException {

        var cipher = Cipher.getInstance(aesTransformation);
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);

        var sha256 = MessageDigest.getInstance("SHA-256");

        var iArr = new byte[1024];
        int length;

        while ((length = is.read(iArr)) > 0) {
            os.write(cipher.update(iArr, 0, length));
            sha256.update(iArr, 0, length);
        }

        os.write(cipher.doFinal());
        return sha256.digest();
    }

    /** Decrypt and calculate SHA-256 of the decrypted file.
    */
    public static byte[] decryptAndCalculateSHA256(InputStream is,
                                                   OutputStream os,
                                                   SecretKey key,
                                                   IvParameterSpec iv,
                                                   String aesTransformation) throws
            NoSuchPaddingException,
            NoSuchAlgorithmException,
            InvalidAlgorithmParameterException,
            InvalidKeyException,
            BadPaddingException,
            IllegalBlockSizeException,
            IOException {

        var cipher = Cipher.getInstance(aesTransformation);
        cipher.init(Cipher.DECRYPT_MODE, key, iv);

        var sha256 = MessageDigest.getInstance("SHA-256");

        var iArr = new byte[1024];
        int length;

        while ((length = is.read(iArr)) > 0) {
            var oArr = cipher.update(iArr, 0, length);
            os.write(oArr);
            sha256.update(oArr, 0, length);
        }

        os.write(cipher.doFinal());
        return sha256.digest();
    }

    public static byte[] decrypt(byte[] cipherText, SecretKey key, IvParameterSpec iv, String aesTransformation)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException {

        var cipher = Cipher.getInstance(aesTransformation);
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        return cipher.doFinal(cipherText);
    }

    public static final String PROP_AES_KEY_LENGTH = "aes_key_length", PROP_AES_KEY_ITERATIONS = "aes_key_iterations",
            PROP_AES_SALT_LENGTH = "aes_salt_length", PROP_AES_TRANSFORMATION_IDX = "aes_transformation_idx", PROP_AES_KEY_ALGORITHM_IDX = "aes_key_algorithm_idx";

    public static int getAesTransformationIdx(SharedPreferences preferences) {
        return preferences.getInt(PROP_AES_TRANSFORMATION_IDX, 0);
    }

    public static AesTransformation getAesTransformation(SharedPreferences preferences) {
        return AesTransformation.values()[getAesTransformationIdx(preferences)];
    }

    public static int getAesKeyAlgorithmIdx(SharedPreferences preferences) {
        return preferences.getInt(PROP_AES_KEY_ALGORITHM_IDX, 0);
    }

    public static AesKeyAlgorithm getAesKeyAlgorithm(SharedPreferences preferences) {
        return AesKeyAlgorithm.values()[getAesKeyAlgorithmIdx(preferences)];
    }

    public static int getSaltLength(SharedPreferences preferences) {
        return preferences.getInt(PROP_AES_SALT_LENGTH, 16);
    }

    public static int getKeyspecIterations(SharedPreferences preferences) {
        return preferences.getInt(PROP_AES_KEY_ITERATIONS, 1024);
    }

    public static int getKeyLength(SharedPreferences preferences) {
        return preferences.getInt(PROP_AES_KEY_LENGTH, 256);
    }
}

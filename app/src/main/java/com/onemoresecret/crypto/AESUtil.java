package com.onemoresecret.crypto;

import android.content.SharedPreferences;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    public static byte[] process(int cipherMode, byte[] input,
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
        cipher.init(cipherMode, key, iv);
        return cipher.doFinal(input);
    }

    public static void process(int cipherMode, InputStream is,
                               OutputStream os,
                               SecretKey key,
                               IvParameterSpec iv,
                               String aesTransformation,
                               Supplier<Boolean> cancellationSupplier,
                               Consumer<Integer> progressConsumer) throws
            NoSuchPaddingException,
            NoSuchAlgorithmException,
            InvalidAlgorithmParameterException,
            InvalidKeyException,
            BadPaddingException,
            IllegalBlockSizeException,
            IOException {

        var cipher = Cipher.getInstance(aesTransformation);
        cipher.init(cipherMode, key, iv);

        var iArr = new byte[1024];
        int length;
        int bytesProcessed = 0;

        while ((length = is.read(iArr)) > 0 &&
                (cancellationSupplier == null || !cancellationSupplier.get())) {
            os.write(cipher.update(iArr, 0, length));
            bytesProcessed += length;
            if (progressConsumer != null) progressConsumer.accept(bytesProcessed);
        }

        os.write(cipher.doFinal());
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

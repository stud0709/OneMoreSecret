package com.onemoresecret.crypto;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class AesEncryptedKeyPairTransfer {
    private final String message;

    public AesEncryptedKeyPairTransfer(String alias, Key rsaPrivateKey, X509Certificate rsaCertificate,
                                       SecretKey aesKey, IvParameterSpec iv, byte[] salt, long validityEnd)
            throws CertificateEncodingException, IOException, NoSuchAlgorithmException, InvalidKeyException,
            NoSuchPaddingException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException {

        byte[] privateKeyEncoded = rsaPrivateKey.getEncoded();
        byte[] certificateEncoded = rsaCertificate == null ? new byte[] {} : rsaCertificate.getEncoded();
        byte[] cipherText = encryptKeyData(privateKeyEncoded, certificateEncoded, aesKey, iv, salt, validityEnd);

        // --- create message ---
        List<String> list = new ArrayList<>();

        // (1) application-ID
        list.add(Integer.toString(MessageComposer.APPLICATION_AES_ENCRYPTED_KEY_PAIR_TRANSFER));

        // (2) alias
        list.add(alias);

        // --- AES parameter ---

        // (3) salt
        list.add(Base64.getEncoder().encodeToString(salt));

        // (4) iv
        list.add(Base64.getEncoder().encodeToString(iv.getIV()));

        // (5) AES transformation
        list.add(AESUtil.AES_TRANSFORMATION);

        // (6) key algorithm
        list.add(AESUtil.KEY_ALGORITHM);

        // (7) keyspec length
        list.add(Integer.toString(AESUtil.KEY_LENGTH));

        // (8) keyspec iterations
        list.add(Integer.toString(AESUtil.KEYSPEC_ITERATIONS));

        // (9) validity end
        list.add(Long.toString(validityEnd));

        // --- encrypted data ---

        // (10) cipher text
        list.add(Base64.getEncoder().encodeToString(cipherText));

        this.message = list.stream().collect(Collectors.joining("\t"));
    }

    protected byte[] encryptKeyData(byte[] privateKeyEncoded, byte[] certificateEncoded, SecretKey aesKey,
                                    IvParameterSpec iv, byte[] salt, long validityEnd)
            throws CertificateEncodingException, IOException, NoSuchAlgorithmException, InvalidKeyException,
            NoSuchPaddingException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException {

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        sha256.update(privateKeyEncoded);
        sha256.update(certificateEncoded);

        byte longBytes[] = new byte[Long.BYTES];
        for (int i = 0; i < longBytes.length; i++) {
            int shift = 8 * i;
            longBytes[i] = (byte) (validityEnd >> shift);
        }

        byte[] hash = sha256.digest(longBytes);

        List<String> list = new ArrayList<>();

        // (1) key data
        list.add(Base64.getEncoder().encodeToString(privateKeyEncoded));

        // (2) certificate data
        list.add(Base64.getEncoder().encodeToString(certificateEncoded));

        // (3) hash
        list.add(Base64.getEncoder().encodeToString(hash));

        String s = list.stream().collect(Collectors.joining("\t"));
        // System.out.println(s);

        // --- encrypting the data with AES ---
        return AESUtil.encrypt(s.getBytes(), aesKey, iv, AESUtil.AES_TRANSFORMATION);
    }

    public String getMessage() {
        return message;
    }
}

package com.onemoresecret.crypto;

import static com.onemoresecret.crypto.MessageComposer.APPLICATION_ENCRYPTED_FILE;

import com.onemoresecret.OmsDataOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class EncryptedFile {
    public static void create(InputStream fis, File oFile,
                              RSAPublicKey rsaPublicKey,
                              int rsaTransformationIdx,
                              int aesKeyLength,
                              int aesTransformationIdx)
            throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException,
            BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IOException {

        // init AES
        var iv = AESUtil.generateIv();
        var secretKey = AESUtil.generateRandomSecretKey(aesKeyLength);

        // encrypt AES secret key with RSA
        var cipher = Cipher.getInstance(RsaTransformation.values()[rsaTransformationIdx].transformation);
        cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);

        var encryptedSecretKey = cipher.doFinal(secretKey.getEncoded());

        int startSha;
        byte[] sha256;

        try (var fos = new FileOutputStream(oFile);
             var dataOutputStream = new OmsDataOutputStream(fos)) {

            // (1) application-ID
            dataOutputStream.writeUnsignedShort(APPLICATION_ENCRYPTED_FILE);

            // (2) RSA transformation index
            dataOutputStream.writeUnsignedShort(rsaTransformationIdx);

            // (3) fingerprint
            dataOutputStream.writeByteArray(CryptographyManager.getFingerprint(rsaPublicKey));

            // (4) AES transformation index
            dataOutputStream.writeUnsignedShort(aesTransformationIdx);

            // (5) IV
            dataOutputStream.writeByteArray(iv.getIV());

            // (6) RSA-encrypted AES secret key
            dataOutputStream.writeByteArray(encryptedSecretKey);

            // (7) placeholder for SHA256 signature of the original file (will be added later)
            startSha = dataOutputStream.size();
            dataOutputStream.writeByteArray(new byte[32]);

            // (8) last modified time of the original file
            dataOutputStream.writeLong(0 /* looks like this is not supported by content providers in Android */);

            // (7) AES-encrypted message
            sha256 = AESUtil.encryptAndCalculateSHA256(fis, dataOutputStream, secretKey, iv,
                    AesTransformation.values()[aesTransformationIdx].transformation);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        //update the file with SHA-256 hash
        try (var raf = new RandomAccessFile(oFile, "rw")) {
            raf.seek(startSha);
            raf.write(sha256);
        }
    }
}

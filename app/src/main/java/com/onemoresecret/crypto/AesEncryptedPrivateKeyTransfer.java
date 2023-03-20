package com.onemoresecret.crypto;

import com.onemoresecret.OmsDataInputStream;
import com.onemoresecret.OmsDataOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class AesEncryptedPrivateKeyTransfer extends MessageComposer {
    private final byte[] message;

    public AesEncryptedPrivateKeyTransfer(String alias,
                                          KeyPair rsaKeyPair,
                                          SecretKey aesKey,
                                          IvParameterSpec iv,
                                          byte[] salt,
                                          int aesTransformationIdx,
                                          int aesKeyAlgorithmIdx,
                                          int aesKeyLength,
                                          int aesKeyspecIterations) throws
            NoSuchAlgorithmException,
            InvalidKeyException,
            NoSuchPaddingException,
            InvalidAlgorithmParameterException,
            BadPaddingException,
            IllegalBlockSizeException {
        super();

        // --- create message ---
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {
            // (1) application-ID
            dataOutputStream.writeUnsignedShort(MessageComposer.APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER);

            // (2) alias
            dataOutputStream.writeString(alias);

            // --- AES parameter ---

            // (3) salt
            dataOutputStream.writeByteArray(salt);

            // (4) iv
            dataOutputStream.writeByteArray(iv.getIV());

            // (5) AES transformation index
            dataOutputStream.writeUnsignedShort(aesTransformationIdx);

            // (6) key algorithm index
            dataOutputStream.writeUnsignedShort(aesKeyAlgorithmIdx);

            // (7) keyspec length
            dataOutputStream.writeUnsignedShort(aesKeyLength);

            // (8) keyspec iterations
            dataOutputStream.writeUnsignedShort(aesKeyspecIterations);

            // --- encrypted data ---

            // (9) cipher text
            dataOutputStream.writeByteArray(getCipherText(rsaKeyPair, aesKey, iv, aesTransformationIdx));

            this.message = baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] getCipherText(KeyPair rsaKeyPair, SecretKey aesKey, IvParameterSpec iv, int aesTransformationIdx) throws
            InvalidAlgorithmParameterException,
            NoSuchPaddingException,
            IllegalBlockSizeException,
            NoSuchAlgorithmException,
            BadPaddingException,
            InvalidKeyException {

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OmsDataOutputStream dataOutputStreamCipher = new OmsDataOutputStream(baos)) {
            RSAPublicKey publicKey = (RSAPublicKey) rsaKeyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) rsaKeyPair.getPrivate();

            // (9.1) - private key material
            dataOutputStreamCipher.writeByteArray(privateKey.getEncoded());

            // (9.2) - public key material
            dataOutputStreamCipher.writeByteArray(publicKey.getEncoded());

            return AESUtil.encrypt(baos.toByteArray(),
                    aesKey,
                    iv,
                    AesTransformation.values()[aesTransformationIdx].transformation);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public byte[] getMessage() {
        return message;
    }
}

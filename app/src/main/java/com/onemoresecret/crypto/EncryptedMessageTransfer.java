package com.onemoresecret.crypto;

import com.onemoresecret.OmsDataOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class EncryptedMessageTransfer extends MessageComposer {
    private final byte[] message;

    public EncryptedMessageTransfer(byte[] message,
                                    RSAPublicKey rsaPublicKey,
                                    int rsaTransformationIdx,
                                    int aesKeyLength,
                                    int aesTransformationIdx)
            throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException,
            BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
        super();

        // init AES
        var iv = AESUtil.generateIv();
        var secretKey = AESUtil.generateRandomSecretKey(aesKeyLength);

        // encrypt AES secret key with RSA
        var cipher = Cipher.getInstance(RsaTransformation.values()[rsaTransformationIdx].transformation);
        cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);

        var encryptedSecretKey = cipher.doFinal(secretKey.getEncoded());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {

            // (1) application-ID
            dataOutputStream.writeUnsignedShort(getApplicationId());

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

            // (7) AES-encrypted message
            dataOutputStream.writeByteArray(AESUtil.encrypt(message, secretKey, iv,
                    AesTransformation.values()[aesTransformationIdx].transformation));

            this.message = baos.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected int getApplicationId() {
        return APPLICATION_ENCRYPTED_MESSAGE_TRANSFER;
    }

    @Override
    public byte[] getMessage() {
        return message;
    }

}

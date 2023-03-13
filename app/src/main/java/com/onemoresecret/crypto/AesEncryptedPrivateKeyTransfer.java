package com.onemoresecret.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class AesEncryptedPrivateKeyTransfer extends MessageComposer {
    private final String message;

    public AesEncryptedPrivateKeyTransfer(String alias,
                                          Key rsaPrivateKey,
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

        byte[] privateKeyEncoded = rsaPrivateKey.getEncoded();

        // --- create message ---
        List<String> list = new ArrayList<>();

        // (1) application-ID
        list.add(Integer.toString(MessageComposer.APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER));

        // (2) alias
        list.add(alias);

        // --- AES parameter ---

        // (3) salt
        list.add(Base64.getEncoder().encodeToString(salt));

        // (4) iv
        list.add(Base64.getEncoder().encodeToString(iv.getIV()));

        // (5) AES transformation index
        list.add(Integer.toString(aesTransformationIdx));

        // (6) key algorithm index
        list.add(Integer.toString(aesKeyAlgorithmIdx));

        // (7) keyspec length
        list.add(Integer.toString(aesKeyLength));

        // (8) keyspec iterations
        list.add(Integer.toString(aesKeyspecIterations));

        // --- encrypted data ---

        // (9) cipher text
        list.add(Base64.getEncoder().encodeToString(
                AESUtil.encrypt(privateKeyEncoded,
                        aesKey,
                        iv,
                        AesTransformation.values()[aesTransformationIdx].transformation)));

        this.message = String.join("\t", list);
    }

    public String getMessage() {
        return message;
    }
}

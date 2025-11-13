package com.onemoresecret.crypto;

import android.util.Log;

import com.onemoresecret.OmsDataOutputStream;
import com.onemoresecret.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class EncryptedCryptoCurrencyAddress {
    private static final String TAG = EncryptedCryptoCurrencyAddress.class.getSimpleName();
    private final byte[] message;

    public EncryptedCryptoCurrencyAddress(int ai,
                                          byte[] privateKey,
                                          RSAPublicKey rsaPublicKey,
                                          int rsaTransformationIdx,
                                          int aesKeyLength,
                                          int aesTransformationIdx)
            throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException,
            BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {

        this.message = MessageComposer.createRsaAesEnvelope(rsaPublicKey,
                rsaTransformationIdx,
                aesKeyLength,
                aesTransformationIdx,
                createPayload(ai, privateKey));
    }

    private byte[] createPayload(int ai, byte[] privateKey) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {

            // (1) the real Application Identifier
            dataOutputStream.writeUnsignedShort(ai);

            // (2) private key as byte array
            dataOutputStream.writeByteArray(privateKey);

            return baos.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public byte[] getMessage() {
        return message;
    }

}

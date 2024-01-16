package com.onemoresecret.crypto;

import com.onemoresecret.OmsDataOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class EncryptedMessage {
    private final byte[] message;

    public EncryptedMessage(byte[] message,
                            RSAPublicKey rsaPublicKey,
                            int rsaTransformationIdx,
                            int aesKeyLength,
                            int aesTransformationIdx)
            throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException,
            BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {

        this.message = MessageComposer.createRsaAesEnvelope(
                rsaPublicKey,
                rsaTransformationIdx,
                aesKeyLength,
                aesTransformationIdx,
                createPayload(getApplicationId(), message));
    }

    private byte[] createPayload(int ai, byte[] message) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {

            // (1) the real Application Identifier
            dataOutputStream.writeUnsignedShort(ai);

            // (2) message key as byte array
            dataOutputStream.writeByteArray(message);

            return baos.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected int getApplicationId() {
        return MessageComposer.APPLICATION_ENCRYPTED_MESSAGE;
    }

    public byte[] getMessage() {
        return message;
    }

}

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

        this.message = createRsaAesEnvelope(getApplicationId(),
                rsaPublicKey,
                rsaTransformationIdx,
                aesKeyLength,
                aesTransformationIdx,
                message);
    }

    protected int getApplicationId() {
        return APPLICATION_ENCRYPTED_MESSAGE_TRANSFER;
    }

    public byte[] getMessage() {
        return message;
    }

}

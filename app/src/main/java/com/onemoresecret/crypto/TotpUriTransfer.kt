package com.onemoresecret.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class TotpUriTransfer extends EncryptedMessage {
    public TotpUriTransfer(byte[] message,
                           RSAPublicKey rsaPublicKey,
                           int rsaTransformationIdx,
                           int aesKeyLength,
                           int aesTransformationIdx) throws
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            IllegalBlockSizeException,
            BadPaddingException,
            InvalidKeyException,
            InvalidAlgorithmParameterException {

        super(message, rsaPublicKey, rsaTransformationIdx, aesKeyLength, aesTransformationIdx);
    }

    @Override
    protected int getApplicationId() {
        return MessageComposer.APPLICATION_TOTP_URI;
    }
}

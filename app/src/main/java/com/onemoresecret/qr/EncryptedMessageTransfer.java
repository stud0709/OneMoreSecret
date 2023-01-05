package com.onemoresecret.qr;

import android.content.Context;
import android.util.Log;


import androidx.biometric.BiometricPrompt;

import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.CryptographyManager;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptedMessageTransfer implements MessageProcessorApplication<String> {
    private static final String TAG = EncryptedMessageTransfer.class.getSimpleName();
    private final Function<BiometricPrompt.AuthenticationCallback, BiometricPrompt> biometricPromptFx;

    public EncryptedMessageTransfer(Function<BiometricPrompt.AuthenticationCallback, BiometricPrompt> biometricPromptFx) {
        this.biometricPromptFx = biometricPromptFx;
    }

    @Override
    public void processData(String message, Context ctx, Consumer<String> onSuccess, Consumer<Exception> onException) {
        try {
            String sArr[] = message.split("\t");

            //(1) Application ID
            int applicationId = Integer.parseInt(sArr[0]);
            if (applicationId != MessageProcessorApplication.APPLICATION_ENCRYPTED_MESSAGE_TRANSFER)
                throw new IllegalArgumentException("wrong applicationId: " + applicationId);

            //(2) RSA transformation
            String rsaTransformation = sArr[1];

            //(3) fingerprint
            byte[] fingerprint = Base64.getDecoder().decode(sArr[2]);

            // (4) AES transformation
            String aesTransformation = sArr[3];

            //(5) IV
            byte[] iv = Base64.getDecoder().decode(sArr[4]);

            //(6) RSA-encrypted AES secret key
            byte[] encryptedAesSecretKey = Base64.getDecoder().decode(sArr[5]);

            //(7) AES-encrypted message
            byte[] aesEncryptedMessage = Base64.getDecoder().decode(sArr[6]);

            //******* decrypting ********

            CryptographyManager cryptographyManager = new CryptographyManager();
            List<String> aliases = cryptographyManager.getByFingerprint(fingerprint);

            if (aliases.isEmpty()) throw new NoSuchElementException("No key found");

            if (aliases.size() > 1) Log.wtf(TAG, aliases.size() + "keys found");
            //todo: additional checks here?

            cryptographyManager.showBiometricPromptForDecryption(encryptedAesSecretKey,
                    ctx,
                    biometricPromptFx,
                    aliases.get(0),
                    rsaTransformation,
                    aesSecretKey -> onAuthSuccess(aesTransformation, aesSecretKey, iv, aesEncryptedMessage, onSuccess, onException));
        } catch (Exception ex) {
            onException.accept(ex);
        }

    }

    private void onAuthSuccess(String aesTransformation, byte[] aesSecretKey, byte[] iv, byte[] aesEncryptedMessage, Consumer<String> onSuccess, Consumer<Exception> onException) {
        try {
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            SecretKeySpec secretKeySpec = new SecretKeySpec(aesSecretKey, "AES");

            byte[] message = AESUtil.decrypt(aesEncryptedMessage, secretKeySpec, ivParameterSpec, aesTransformation);

            String s = new String(message);
            String sArr[] = s.split("\t");

            //(1) data
            byte[] data = Base64.getDecoder().decode(sArr[0]);

            //(2) hash
            byte[] sha256hash = Base64.getDecoder().decode(sArr[1]);

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] _sha256hash = sha256.digest(data);

            if (!Arrays.equals(sha256hash, _sha256hash)) {
                throw new IllegalArgumentException("Invalid hash");
            }

            onSuccess.accept(new String(data));
        } catch (Exception e) {
            onException.accept(e);
        }
    }
}

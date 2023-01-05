package com.onemoresecret.qr;

import android.content.Context;
import android.util.Log;

import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Consumer;

public class PublicTextTransfer implements MessageProcessorApplication<String> {
    private static final String TAG = PublicTextTransfer.class.getSimpleName();

    @Override
    public void processData(String message, Context ctx, Consumer<String> onSuccess, Consumer<Exception> onException) {
        try {
            String[] sArr = message.split("\t", 3);

            //(1) application ID
            int applicationId = Integer.parseInt(sArr[0]);
            if (applicationId != MessageProcessorApplication.APPLICATION_PUBLIC_TEXT_TRANSFER)
                throw new IllegalArgumentException("wrong applicationId: " + applicationId);

            //(4) hash
            byte[] _hash = new byte[32];
            _hash = Base64.getDecoder().decode(sArr[1]);

            //(3) message
            String s = sArr[2];

            MessageDigest sha256 = null;
            try {
                sha256 = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                return;
            }
            byte[] hash = sha256.digest(s.getBytes());

            //compare hash values
            if (!Arrays.equals(hash, _hash)) {
                throw new IllegalArgumentException("Could not confirm data integrity");
            }

            onSuccess.accept(s);
        } catch (Exception ex) {
            onException.accept(ex);
        }
    }

}

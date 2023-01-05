package com.onemoresecret.qr;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.biometric.BiometricPrompt;

import java.util.function.Consumer;
import java.util.function.Function;

public class MessageProcessor {

    private static final String TAG = MessageProcessor.class.getSimpleName();

    private final Function<BiometricPrompt.AuthenticationCallback, BiometricPrompt> biometricPromptFx;

    public MessageProcessor(Function<BiometricPrompt.AuthenticationCallback, BiometricPrompt> biometricPromptFx) {
        this.biometricPromptFx = biometricPromptFx;
    }

    public void onMessage(String message, Context ctx) {
        ctx.getMainExecutor().execute(() -> {
            String[] sArr = message.split("\t", 2);

            //(1) application ID
            int applicationId = Integer.parseInt(sArr[0]);
            Log.d(TAG, "Application-ID: " + Integer.toHexString(applicationId));

            switch (applicationId) {
                case MessageProcessorApplication.APPLICATION_AES_ENCRYPTED_KEY_PAIR_TRANSFER:
                    new AesEncryptedKeyPairTransfer().processData(
                            message,
                            ctx,
                            v -> {
                                //nothing
                            },
                            onException(ctx));
                    break;
                case MessageProcessorApplication.APPLICATION_PUBLIC_TEXT_TRANSFER: {
                    new PublicTextTransfer().processData(
                            message,
                            ctx,
                            s -> {
                                //todo: call fragment
                                Toast.makeText(ctx, s, Toast.LENGTH_LONG);
                            },
                            onException(ctx));
                    break;
                }
                case MessageProcessorApplication.APPLICATION_ENCRYPTED_MESSAGE_TRANSFER:
                    new EncryptedMessageTransfer(biometricPromptFx).processData(
                            message,
                            ctx,
                            s -> {
                                //todo: call fragment
                                Toast.makeText(ctx, s, Toast.LENGTH_LONG);
                            },
                            onException(ctx));
                default:
                    Log.d(TAG, "No processor defined for application ID " + Integer.toHexString(applicationId));
                    break;
            }
        });
    }

    private static Consumer<Exception> onException(Context ctx) {
        //todo
        return e -> e.printStackTrace();
    }
}

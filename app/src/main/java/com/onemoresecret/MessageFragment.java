package com.onemoresecret;

import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.databinding.FragmentMessageBinding;
import com.onemoresecret.qr.MessageProcessorApplication;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import javax.crypto.Cipher;

public class MessageFragment extends Fragment {
    private static final String TAG = MessageFragment.class.getSimpleName();
    private byte[] cipherText;

    private FragmentMessageBinding binding;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentMessageBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String message = getArguments().getString("MESSAGE");

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
            cipherText = Base64.getDecoder().decode(sArr[6]);

            //******* decrypting ********

            CryptographyManager cryptographyManager = new CryptographyManager();
            List<String> aliases = cryptographyManager.getByFingerprint(fingerprint);

            if (aliases.isEmpty()) throw new NoSuchElementException("No key found");

            showBiometricPromptForDecryption(aliases.get(0), rsaTransformation);
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(this.getContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showBiometricPromptForDecryption(String alias, String transformation) throws
            InvalidAlgorithmParameterException,
            NoSuchAlgorithmException,
            KeyStoreException,
            NoSuchProviderException {

        BiometricPrompt.AuthenticationCallback authenticationCallback = new BiometricPrompt.AuthenticationCallback() {

            @Override
            public void onAuthenticationError(int errCode, CharSequence errString) {
                super.onAuthenticationError(errCode, errString);
                MessageFragment.this.onAuthenticationError(errCode, errString);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                MessageFragment.this.onAuthenticationFailed();
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                MessageFragment.this.onAuthenticationSucceeded(result);
            }
        };

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, authenticationCallback);

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.prompt_info_title))
                .setSubtitle(getString(R.string.prompt_info_subtitle))
                .setDescription(getString(R.string.prompt_info_description))
                .setNegativeButtonText(getString(R.string.prompt_info_negative_text))
                .setConfirmationRequired(false)
                .build();

        Cipher cipher = new CryptographyManager().getInitializedCipherForDecryption(
                alias, transformation);

        biometricPrompt.authenticate(
                promptInfo,
                new BiometricPrompt.CryptoObject(cipher));
    }

    public void onAuthenticationError(int errCode, CharSequence errString) {
        Log.d(TAG,
                "Authentication failed: " + errString + " (" + errCode + ")");
        Toast.makeText(getContext(), errString + " (" + errCode + ")", Toast.LENGTH_SHORT).show();
    }

    public void onAuthenticationFailed() {
        Log.d(TAG,
                "User biometric rejected");
        Toast.makeText(getContext(), "Authentication failed", Toast.LENGTH_SHORT).show();
    }

    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
        Log.d(TAG,
                "Authentication was successful");

        Cipher cipher = result.getCryptoObject().getCipher();
        try {
            byte[] bArr = cipher.doFinal(cipherText);
            onDecryptedData(bArr);
        } catch (Exception e) {
            //todo: dummy!
            e.printStackTrace();
        }
    }

    private void onDecryptedData(byte[] bArr) {
        String message = new String(bArr);
        binding.swRevealMessage.setOnCheckedChangeListener((compoundButton, b) -> {
            binding.textViewMessage.setText( b ? message : getString(R.string.hidden_text_slide_to_reveal));
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
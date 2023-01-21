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

import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.databinding.FragmentMessageBinding;
import com.onemoresecret.qr.MessageProcessorApplication;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MessageFragment extends Fragment {
    private static final String TAG = MessageFragment.class.getSimpleName();
    private byte[] cipherText, encryptedAesSecretKey, iv;
    private String rsaTransformation, aesTransformation;

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
            rsaTransformation = sArr[1];

            //(3) fingerprint
            byte[] fingerprint = Base64.getDecoder().decode(sArr[2]);

            // (4) AES transformation
            aesTransformation = sArr[3];

            //(5) IV
            iv = Base64.getDecoder().decode(sArr[4]);

            //(6) RSA-encrypted AES secret key
            encryptedAesSecretKey = Base64.getDecoder().decode(sArr[5]);

            //(7) AES-encrypted message
            cipherText = Base64.getDecoder().decode(sArr[6]);

            //******* decrypting ********

            CryptographyManager cryptographyManager = new CryptographyManager();
            List<String> aliases = cryptographyManager.getByFingerprint(fingerprint);

            if (aliases.isEmpty()) throw new NoSuchElementException("No key found");

            showBiometricPromptForDecryption(aliases.get(0));
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(this.getContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showBiometricPromptForDecryption(String alias) throws
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
                alias, rsaTransformation);

        biometricPrompt.authenticate(
                promptInfo,
                new BiometricPrompt.CryptoObject(cipher));
    }

    public void onAuthenticationError(int errCode, CharSequence errString) {
        Log.d(TAG,
                "Authentication failed: " + errString + " (" + errCode + ")");
        getContext().getMainExecutor().execute(() -> {
            Toast.makeText(getContext(), errString + " (" + errCode + ")", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(MessageFragment.this).popBackStack();
        });
    }

    public void onAuthenticationFailed() {
        Log.d(TAG,
                "User biometric rejected");
        getContext().getMainExecutor().execute(() -> {
            Toast.makeText(getContext(), "Authentication failed", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(MessageFragment.this).popBackStack();
        });
    }

    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
        Log.d(TAG,
                "Authentication was successful");

        Cipher cipher = result.getCryptoObject().getCipher();
        try {
            byte[] aesSecretKeyData = cipher.doFinal(encryptedAesSecretKey);
            SecretKey aesSecretKey = new SecretKeySpec(aesSecretKeyData, "AES");
            byte[] bArr = AESUtil.decrypt(cipherText, aesSecretKey, new IvParameterSpec(iv), aesTransformation);

            onDecryptedData(bArr);
        } catch (Exception e) {
            e.printStackTrace();
            getContext().getMainExecutor().execute(() -> {
                Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(MessageFragment.this).popBackStack();
            });
        }
    }

    private void onDecryptedData(byte[] bArr) throws NoSuchAlgorithmException {
        String[] sArr = new String(bArr).split("\t");
        // (1) message
        byte[] messageBytes = Base64.getDecoder().decode(sArr[0]);
        String message = new String(messageBytes);

        // (2) hash
        byte[] hash = Base64.getDecoder().decode(sArr[1]);

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] _hash = sha256.digest(messageBytes);

        if (!Arrays.equals(hash, _hash)) {
            throw new IllegalArgumentException("Could not confirm message integrity");
        }

        binding.swRevealMessage.setOnCheckedChangeListener((compoundButton, b) -> {
            binding.textViewMessage.setText(b ? message : getString(R.string.hidden_text_slide_to_reveal));
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
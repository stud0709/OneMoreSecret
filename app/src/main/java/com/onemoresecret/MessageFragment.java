package com.onemoresecret;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.icu.util.Output;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.onemoresecret.bt.BluetoothController;
import com.onemoresecret.bt.layout.KeyboardLayout;
import com.onemoresecret.bt.layout.Stroke;
import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.databinding.FragmentMessageBinding;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MessageFragment extends Fragment {
    private static final String TAG = MessageFragment.class.getSimpleName();
    private byte[] cipherText, encryptedAesSecretKey, iv;
    private String rsaTransformation, aesTransformation;
    private FragmentMessageBinding binding;
    private boolean paused = false;


    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentMessageBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onPause() {
        super.onPause();
        paused = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (paused)
            NavHostFragment.findNavController(MessageFragment.this).popBackStack();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String message = getArguments().getString("MESSAGE");

        try {
            String[] sArr = message.split("\t");

            //(1) Application ID
            int applicationId = Integer.parseInt(sArr[0]);
            if (applicationId != MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_TRANSFER)
                throw new IllegalArgumentException(getString(R.string.wrong_application) + " " + applicationId);

            //(2) RSA transformation
            rsaTransformation = sArr[1];
            Log.d(TAG, "RSA transformation: " + rsaTransformation);

            //(3) RSA fingerprint
            byte[] fingerprint = Base64.getDecoder().decode(sArr[2]);
            Log.d(TAG, "RSA fingerprint: " + BluetoothController.byteArrayToHex(fingerprint));

            // (4) AES transformation
            aesTransformation = sArr[3];
            Log.d(TAG, "AES transformation: " + aesTransformation);

            //(5) IV
            iv = Base64.getDecoder().decode(sArr[4]);
            Log.d(TAG, "IV: " + BluetoothController.byteArrayToHex(iv));

            //(6) RSA-encrypted AES secret key
            encryptedAesSecretKey = Base64.getDecoder().decode(sArr[5]);

            //(7) AES-encrypted message
            cipherText = Base64.getDecoder().decode(sArr[6]);

            //******* decrypting ********

            CryptographyManager cryptographyManager = new CryptographyManager();
            List<String> aliases = cryptographyManager.getByFingerprint(fingerprint);

            if (aliases.isEmpty())
                throw new NoSuchElementException(getString(R.string.no_key_found));

            if (aliases.size() > 1)
                throw new NoSuchElementException(getString(R.string.multiple_keys_found));

            showBiometricPromptForDecryption(aliases.get(0));
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(this.getContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showBiometricPromptForDecryption(String alias) {

        BiometricPrompt.AuthenticationCallback authenticationCallback = new BiometricPrompt.AuthenticationCallback() {

            @Override
            public void onAuthenticationError(int errCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errCode, errString);
                MessageFragment.this.onAuthenticationError(errCode, errString);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                MessageFragment.this.onAuthenticationFailed();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
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
            Toast.makeText(getContext(), getString(R.string.auth_failed), Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(MessageFragment.this).popBackStack();
        });
    }

    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
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
            throw new IllegalArgumentException(getString(R.string.msg_integrity_check_failed));
        }

        binding.swRevealMessage.setOnCheckedChangeListener((compoundButton, b) -> {
            binding.textViewMessage.setText(b ? message : getString(R.string.hidden_text_slide_to_reveal));
        });

        OutputFragment of = (OutputFragment) getChildFragmentManager().findFragmentById(R.id.messageOutputFragment);
        of.setMessage(message);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.textViewMessage.setText(getString(R.string.hidden_text_slide_to_reveal));
        binding = null;
    }
}
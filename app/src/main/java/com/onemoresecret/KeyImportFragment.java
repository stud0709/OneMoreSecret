package com.onemoresecret;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.AesKeyAlgorithm;
import com.onemoresecret.crypto.AesTransformation;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.databinding.FragmentKeyImportBinding;

import java.security.KeyStoreException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * Key import Fragment.
 */
public class KeyImportFragment extends Fragment {
    private FragmentKeyImportBinding binding;
    private static final String TAG = KeyImportFragment.class.getSimpleName();
    private final CryptographyManager cryptographyManager = new CryptographyManager();

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        assert getArguments() != null;
        String message = getArguments().getString("MESSAGE");

        String[] sArr = message.split("\t");

        //(1) Application ID
        int applicationId = Integer.parseInt(sArr[0]);
        if (applicationId != MessageComposer.APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER)
            throw new IllegalArgumentException("wrong applicationId: " + applicationId);

        //(2) alias
        String alias = sArr[1];
        Log.d(TAG, "alias: " + alias);
        binding.editTextKeyAlias.setText(alias);
        // --- AES parameter ---

        //(3) salt
        byte[] salt = Base64.getDecoder().decode(sArr[2]);
        Log.d(TAG, "salt: " + Util.byteArrayToHex(salt));

        //(4) IV
        byte[] iv = Base64.getDecoder().decode(sArr[3]);
        Log.d(TAG, "IV: " + Util.byteArrayToHex(iv));

        //(5) AES transformation index
        String aesTransformation = AesTransformation.values()[Integer.parseInt(sArr[4])].transformation;
        Log.d(TAG, "cipher algorithm: " + aesTransformation);

        //(6) key algorithm index
        String aesKeyAlg = AesKeyAlgorithm.values()[Integer.parseInt(sArr[5])].keyAlgorithm;
        Log.d(TAG, "AES key algorithm: " + aesKeyAlg);

        //(7) key length
        int aesKeyLength = Integer.parseInt(sArr[6]);
        Log.d(TAG, "AES key length: " + aesKeyLength);

        //(8) AES iterations
        int iterations = Integer.parseInt(sArr[7]);
        Log.d(TAG, "iterations: " + iterations);

        // --- Encrypted part ---

        //(9) cipher text
        byte[] cipherText = Base64.getDecoder().decode(sArr[8]);
        Log.d(TAG, cipherText.length + " bytes cipher text read");

        binding.editTextKeyAlias.setText(alias);

        binding.btnDecrypt.setOnClickListener(e ->
                new Thread(() ->
                        onPasswordEntry(salt,
                                iv,
                                cipherText,
                                aesTransformation,
                                aesKeyAlg,
                                aesKeyLength,
                                iterations)).start()
        );

    }

    private void onPasswordEntry(
            byte[] salt,
            byte[] iv,
            byte[] cipherText,
            String aesTransformation,
            String keyAlg,
            int keyLength,
            int iterations) {

        try {
            //try decrypt
            SecretKey secretKey = AESUtil.getKeyFromPassword(
                    binding.editTextPassphrase.getText().toString().toCharArray(),
                    salt,
                    keyAlg,
                    keyLength,
                    iterations);

            byte[] rsaKey = AESUtil.decrypt(
                    cipherText,
                    secretKey,
                    new IvParameterSpec(iv),
                    aesTransformation);

            RSAPrivateCrtKey privateKey = CryptographyManager.createPrivateKey(rsaKey);

            byte[] fingerprintBytes = CryptographyManager.getFingerprint(privateKey);
            String fingerprint = Util.byteArrayToHex(fingerprintBytes);

            requireContext().getMainExecutor().execute(() -> {
                binding.textFingerprintData.setText(fingerprint);

                //check alias
                binding.editTextKeyAlias.addTextChangedListener(getTextWatcher(fingerprintBytes));

                validateAlias(fingerprintBytes);

                binding.btnSave.setEnabled(true);

                binding.btnSave.setOnClickListener(e ->
                        new Thread(() -> {
                            try {
                                //delete other keys with the same fingerprint
                                List<String> sameFingerprint = cryptographyManager.getByFingerprint(fingerprintBytes);
                                sameFingerprint.forEach(a -> {
                                    try {
                                        cryptographyManager.deleteKey(a);
                                    } catch (KeyStoreException ex) {
                                        ex.printStackTrace();
                                    }
                                });

                                String keyAlias = binding.
                                        editTextKeyAlias.
                                        getText().
                                        toString().
                                        trim();
                                cryptographyManager.importKey(
                                        keyAlias,
                                        privateKey,
                                        getContext());
                                requireContext().getMainExecutor().execute(
                                        () -> {
                                            Toast.makeText(this.getContext(),
                                                    "Private key '" + keyAlias + "' successfully imported",
                                                    Toast.LENGTH_LONG).show();
                                            NavHostFragment.findNavController(KeyImportFragment.this).popBackStack();
                                        });

                            } catch (Exception ex) {
                                ex.printStackTrace();
                                requireContext().getMainExecutor().execute(
                                        () -> Toast.makeText(this.getContext(),
                                                ex.getMessage(),
                                                Toast.LENGTH_LONG).show());
                            }
                        }).start()
                );
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            requireContext().getMainExecutor().execute(
                    () -> Toast.makeText(this.getContext(),
                            "Could not decrypt. Wrong passphrase?",
                            Toast.LENGTH_LONG).show());
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentKeyImportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void validateAlias(byte[] fingerprintNew) {
        try {
            String alias = binding.editTextKeyAlias.getText().toString();
            Log.d(TAG, "alias: " + alias);
            String warning = null;

            if (cryptographyManager.keyStore.containsAlias(alias)) {
                byte[] fingerprint = CryptographyManager.getFingerprint((RSAPublicKey) cryptographyManager.getCertificate(alias).getPublicKey());
                if (!Arrays.equals(fingerprint, fingerprintNew)) {
                    warning = String.format(getString(R.string.warning_alias_exists), Util.byteArrayToHex(fingerprintNew));
                }
            }

            List<String> sameFingerprint = cryptographyManager.getByFingerprint(fingerprintNew);
            if (!sameFingerprint.isEmpty()) {
                warning = String.format(getString(R.string.warning_same_fingerprint), sameFingerprint.get(0));
            }

            String _warning = warning;

            requireContext().getMainExecutor().execute(() -> {
                binding.txtWarnings.setText(_warning == null ? "" : _warning);
                binding.txtWarnings.setVisibility(_warning == null ? View.GONE : View.VISIBLE);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private TextWatcher getTextWatcher(byte[] fingerprintBytes) {
        return new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                validateAlias(fingerprintBytes);
            }
        };
    }

}
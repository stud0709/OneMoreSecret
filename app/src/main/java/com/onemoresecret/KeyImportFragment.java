package com.onemoresecret;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.TimeUtils;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.onemoresecret.bt.BluetoothController;
import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.databinding.FragmentKeyImportBinding;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.text.DateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * Key import Fragment.
 */
public class KeyImportFragment extends Fragment {
    private FragmentKeyImportBinding binding;
    private static final String TAG = KeyImportFragment.class.getSimpleName();

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String message = getArguments().getString("MESSAGE");

        String sArr[] = message.split("\t");

        //(1) Application ID
        int applicationId = Integer.parseInt(sArr[0]);
        if (applicationId != MessageComposer.APPLICATION_AES_ENCRYPTED_KEY_PAIR_TRANSFER)
            throw new IllegalArgumentException("wrong applicationId: " + applicationId);

        //(2) alias
        String alias = sArr[1];
        Log.d(TAG, "alias: " + alias);
        binding.editTextKeyAlias.setText(alias);

        // --- AES parameter ---

        //(3) salt
        byte[] salt = Base64.getDecoder().decode(sArr[2]);
        Log.d(TAG, "salt: " + BluetoothController.byteArrayToHex(salt));

        //(4) IV
        byte[] iv = Base64.getDecoder().decode(sArr[3]);
        Log.d(TAG, "IV: " + BluetoothController.byteArrayToHex(iv));

        //(5) AES transformation
        String aesTransformation = sArr[4];
        Log.d(TAG, "cipher algorithm: " + aesTransformation);

        //(6) key algorithm
        String aesKeyAlg = sArr[5];
        Log.d(TAG, "AES key algorithm: " + aesKeyAlg);

        //(7) key length
        int aesKeyLength = Integer.parseInt(sArr[6]);
        Log.d(TAG, "AES key length: " + aesKeyLength);

        //(8) AES iterations
        int iterations = Integer.parseInt(sArr[7]);
        Log.d(TAG, "iterations: " + iterations);

        //(9) validity end
        long validityEnd = Long.parseLong(sArr[8]);
        Log.d(TAG, "validity end: " + validityEnd);

        // --- Encrypted part ---

        //(10) cipher text
        byte[] cipherText = Base64.getDecoder().decode(sArr[9]);
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
                                iterations,
                                validityEnd)).start()
        );

    }

    private void onPasswordEntry(
            byte[] salt,
            byte[] iv,
            byte[] cipherText,
            String aesTransformation,
            String keyAlg,
            int keyLength,
            int iterations,
            long validityEnd) {

        try {
            //try decrypt
            SecretKey secretKey = AESUtil.getKeyFromPassword(
                    binding.editTextPassphrase.getText().toString().toCharArray(),
                    salt,
                    keyAlg,
                    keyLength,
                    iterations);

            byte[] data = AESUtil.decrypt(
                    cipherText,
                    secretKey,
                    new IvParameterSpec(iv),
                    aesTransformation);
            String s = new String(data);
            String[] sArr = s.split("\t");

            //(1) RSA Key
            byte[] rsaKey = Base64.getDecoder().decode(sArr[0]);

            //(2) RSA Certificate
            byte[] rsaCert = Base64.getDecoder().decode(sArr[1]);

            //(3) hash
            byte[] _hash = Base64.getDecoder().decode(sArr[2]);

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(rsaKey);
            sha256.update(rsaCert);

            byte longBytes[] = new byte[Long.BYTES];
            for (int i = 0; i < longBytes.length; i++) {
                int shift = 8 * i;
                longBytes[i] = (byte) (validityEnd >> shift);
            }

            byte[] hash = sha256.digest(longBytes);

            //compare hash values
            if (!Arrays.equals(hash, _hash)) {
                throw new IllegalArgumentException("Could not confirm data integrity");
            }

            X509Certificate certificate = rsaCert.length == 0 ? null :
                    (X509Certificate) CryptographyManager.createCertificate(rsaCert);

            RSAPrivateCrtKey privateKey = CryptographyManager.createPrivateKey(rsaKey);

            String fingerprint = BluetoothController.byteArrayToHex(CryptographyManager.getFingerprint(privateKey));

            getContext().getMainExecutor().execute(() -> {
                //default validity end date
                Date validityEndDate = Date.from(validityEnd == 0 ? Instant.ofEpochMilli(
                        System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(CryptographyManager.DEFAULT_DAYS_VALID, TimeUnit.DAYS)) : Instant.ofEpochMilli(validityEnd));

                //set default validity end
                binding.textValidTo.setText(validityEnd == 0 ? getText(R.string.undefined) : DateFormat.getDateTimeInstance().format(validityEndDate));
                binding.textCertificateData.setText(certificate == null ? "(not provided)" : certificate.toString());
                binding.textFingerprintData.setText(fingerprint);
                binding.btnSave.setEnabled(true);

                binding.btnSave.setOnClickListener(e ->
                        new Thread(() -> {
                            try {
                                String keyAlias = binding.
                                        editTextKeyAlias.
                                        getText().
                                        toString().
                                        trim();
                                new CryptographyManager().importKey(
                                        keyAlias,
                                        privateKey,
                                        certificate,
                                        validityEndDate,
                                        getContext());
                                getContext().getMainExecutor().execute(
                                        () -> {
                                            Toast.makeText(this.getContext(),
                                                    "Private key '" + keyAlias + "' successfully imported",
                                                    Toast.LENGTH_LONG).show();
                                            NavHostFragment.findNavController(KeyImportFragment.this).popBackStack();
                                        });

                            } catch (Exception ex) {
                                ex.printStackTrace();
                                getContext().getMainExecutor().execute(
                                        () -> Toast.makeText(this.getContext(),
                                                ex.getMessage(),
                                                Toast.LENGTH_LONG).show());
                            }
                        }).start()
                );
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            getContext().getMainExecutor().execute(
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
}
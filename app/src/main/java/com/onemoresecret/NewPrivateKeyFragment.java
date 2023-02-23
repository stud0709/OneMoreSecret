package com.onemoresecret;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.zxing.WriterException;
import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.AesEncryptedPrivateKeyTransfer;
import com.onemoresecret.crypto.AesKeyAlgorithm;
import com.onemoresecret.crypto.AesTransformation;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.databinding.FragmentNewPrivateKeyBinding;
import com.onemoresecret.qr.QRUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateCrtKey;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class NewPrivateKeyFragment extends Fragment {
    public static final int BASE64_LINE_LENGTH = 75;
    private FragmentNewPrivateKeyBinding binding;
    private CryptographyManager cryptographyManager = new CryptographyManager();

    private SharedPreferences preferences;

    private Path privateKeyBackup = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentNewPrivateKeyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        cryptographyManager = new CryptographyManager();
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);

        binding.btnCreatePrivateKey.setOnClickListener(e -> createPrivateKey());
        binding.checkBox.setOnCheckedChangeListener((btn, isChecked) -> binding.btnActivatePrivateKey.setEnabled(isChecked));
    }

    private void createPrivateKey() {
        try {
            SharedPreferences preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);

            if (binding.txtNewKeyAlias.getText().length() == 0) {
                throw new IllegalArgumentException(getString(R.string.key_alias_may_not_be_empty));
            }

            String alias = binding.txtNewKeyAlias.getText().toString();

            if (cryptographyManager.keyStore.containsAlias(alias)) {
                throw new IllegalArgumentException(getString(R.string.key_alias_already_exists));
            }

            if (binding.txtNewTransportPassword.getText().length() < 10) {
                throw new IllegalArgumentException(getString(R.string.password_too_short));
            }

            if (!binding.txtNewTransportPassword.getText().toString().equals(binding.txtRepeatTransportPassword.getText().toString())) {
                throw new IllegalArgumentException(getString(R.string.password_mismatch));
            }

            byte[] bArr = CryptographyManager.generatePrivateKeyMaterial(preferences);
            RSAPrivateCrtKey privateKey = CryptographyManager.createPrivateKey(bArr);
            byte[] fingerprint = CryptographyManager.getFingerprint(privateKey);
            IvParameterSpec iv = AESUtil.generateIv();
            byte[] salt = AESUtil.generateSalt(AESUtil.getSaltLength(preferences));
            int aesKeyLength = AESUtil.getKeyLength(preferences);
            int aesKeyspecIterations = AESUtil.getKeyspecIterations(preferences);
            String aesKeyAlgorithm = AESUtil.getAesKeyAlgorithm(preferences).keyAlgorithm;

            SecretKey aesSecretKey = AESUtil.getKeyFromPassword(
                    binding.txtNewTransportPassword.getText().toString().toCharArray(),
                    salt,
                    aesKeyAlgorithm,
                    aesKeyLength,
                    aesKeyspecIterations);

            String message = new AesEncryptedPrivateKeyTransfer(alias,
                    privateKey,
                    aesSecretKey,
                    iv,
                    salt,
                    AESUtil.getAesTransformationIdx(preferences),
                    AESUtil.getAesKeyAlgorithmIdx(preferences),
                    aesKeyLength,
                    aesKeyspecIterations).getMessage();

            binding.checkBox.setEnabled(true);
            binding.checkBox.setChecked(false);

            binding.btnActivatePrivateKey.setOnClickListener(e -> {
                try {
                    cryptographyManager.importKey(
                            alias,
                            privateKey,
                            requireContext());

                    Toast.makeText(
                            requireContext(),
                            String.format(getString(R.string.key_successfully_activated), alias),
                            Toast.LENGTH_LONG).show();

                    //go back
                    NavHostFragment.findNavController(this).popBackStack();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Toast.makeText(requireContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

            //share HTML file
            String html = getKeyBackupHtml(alias, fingerprint, message);

            File backupDir = new File(requireContext().getCacheDir(), "pk_backup");
            if (!backupDir.exists()) backupDir.mkdirs();

            String fingerprintString = Util.byteArrayToHex(fingerprint).replaceAll("\\s", "_");

            if (privateKeyBackup != null && Files.exists(privateKeyBackup))
                Files.delete(privateKeyBackup);

            privateKeyBackup = backupDir.toPath().resolve("pk_" + fingerprintString + ".html");
            privateKeyBackup.toFile().deleteOnExit();
            Files.write(privateKeyBackup, html.getBytes(StandardCharsets.UTF_8));

            Uri contentUri = OmsFileProvider.getUriForFile(requireContext(), "com.onemoresecret.fileprovider", privateKeyBackup.toFile());

            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setType("text/html");
            startActivity(Intent.createChooser(intent, String.format(getString(R.string.backup_file), alias)));

        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(requireContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getKeyBackupHtml(String alias, byte[] fingerprint, String message) throws WriterException, IOException {
        StringBuilder stringBuilder = new StringBuilder();

        List<Bitmap> list = QRUtil.getQrSequence(message, QRUtil.getChunkSize(preferences), QRUtil.getBarcodeSize(preferences));

        stringBuilder
                .append("<html><body><h1>")
                .append("OneMoreSecret Private Key Backup")
                .append("</h1><p><b>")
                .append("Keep this file / printout in a secure location")
                .append("</b></p><p>")
                .append("This is a hard copy of your Private Key for OneMoreSecret. It can be used to import your Private Key into a new device or after a reset of OneMoreSecret App. This document is encrypted with AES, you will need your TRANSPORT PASSWORD to complete the import procedure.")
                .append("</p><h2>")
                .append("WARNING:")
                .append("</h2><p>")
                .append("DO NOT share this document with other persons.")
                .append("<br>")
                .append("DO NOT provide its content to untrusted apps, on the Internet etc.")
                .append("<br>")
                .append("If you need to restore your Key, start OneMoreSecret App on your phone BY HAND and scan the codes. DO NOT trust unexpected prompts and pop-ups.")
                .append("<br><b>")
                .append("THIS DOCUMENT IS THE ONLY WAY TO RESTORE YOUR PRIVATE KEY")
                .append("</b></p><p><b>")
                .append("Key alias:")
                .append("&nbsp;")
                .append(Html.escapeHtml(alias))
                .append("</b></p><p><b>RSA Fingerprint:")
                .append("&nbsp;")
                .append(Util.byteArrayToHex(fingerprint))
                .append("</b></p><p>")
                .append("Scan this with your OneMoreSecret App:")
                .append("</p><p>");

        for (int i = 0; i < list.size(); i++) {
            Bitmap bitmap = list.get(i);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                baos.flush();
                stringBuilder.append("<table style=\"display: inline-block;\"><tr style=\"vertical-align: bottom;\"><td>")
                        .append(i + 1)
                        .append("</td><td><img src=\"data:image/png;base64,")
                        .append(Base64.getEncoder().encodeToString(baos.toByteArray()))
                        .append("\" style=\"width:200px;height:200px;\"></td></tr></table>");
            }
        }
        stringBuilder
                .append("</p><h2>")
                .append("Long-Term Backup and Technical Details")
                .append("</h2><p>")
                .append("Base64 Encoded Message:")
                .append("&nbsp;")
                .append("</p><p style=\"font-family:monospace;\">");

        String messageAsUrl = MessageComposer.encodeAsOmsText(message);
        int offset = 0;

        while (offset < messageAsUrl.length()) {
            String s = messageAsUrl.substring(offset, Math.min(offset + BASE64_LINE_LENGTH, messageAsUrl.length()));
            stringBuilder.append(s).append("<br>");
            offset += BASE64_LINE_LENGTH;
        }

        String sArr[] = message.split("\t");

        stringBuilder.append("</p><p>")
                .append("Message format: oms00_[base64 encoded data]")
                .append("</p><p>")
                .append("Data format: String (utf-8), separator: TAB")
                .append("</p><p>")
                .append("Data elements:")
                .append("</p><ol><li>")
                .append("Application Identifier = ")
                .append("&nbsp;")
                .append(sArr[0])
                .append("&nbsp;(")
                .append("AES Encrypted Key Pair Transfer")
                .append(")</li><li>")
                .append("Key Alias = ")
                .append(Html.escapeHtml(alias))
                .append("</li><li>")
                .append("Salt: base64-encoded byte[] = ")
                .append(Util.byteArrayToHex(Base64.getDecoder().decode(sArr[2])))
                .append("</li><li>").append("IV: base64-encoded byte[] = ")
                .append(Util.byteArrayToHex(Base64.getDecoder().decode(sArr[3])))
                .append("</li><li>").append("Cipher Algorithm = ")
                .append(sArr[4])
                .append(" (")
                .append(Html.escapeHtml(AesTransformation.values()[Integer.parseInt(sArr[4])].transformation))
                .append(" )</li><li>")
                .append("Key Algorithm = ")
                .append(sArr[5])
                .append(" (")
                .append(Html.escapeHtml(AesKeyAlgorithm.values()[Integer.parseInt(sArr[5])].keyAlgorithm))
                .append(")</li><li>").append("Keyspec Length = ")
                .append(sArr[6])
                .append("</li><li>").append("Keyspec Iterations = ")
                .append(sArr[7])
                .append("</li><li>")
                .append("AES encrypted Private Key material: base64-encoded byte[]")
                .append("</li></ol></body></html>");

        return stringBuilder.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        try {
            if (privateKeyBackup != null && Files.exists(privateKeyBackup))
                Files.delete(privateKeyBackup);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
package com.onemoresecret;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;

import com.google.zxing.WriterException;
import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.AesEncryptedPrivateKeyTransfer;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.RSAUtils;
import com.onemoresecret.databinding.FragmentNewPrivateKeyBinding;
import com.onemoresecret.qr.QRUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Objects;

public class NewPrivateKeyFragment extends Fragment {
    private FragmentNewPrivateKeyBinding binding;
    private CryptographyManager cryptographyManager = new CryptographyManager();

    private SharedPreferences preferences;

    private final PrivateKeyMenuProvider menuProvider = new PrivateKeyMenuProvider();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentNewPrivateKeyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(menuProvider);
        cryptographyManager = new CryptographyManager();
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);

        binding.btnCreatePrivateKey.setOnClickListener(e -> createPrivateKey());
        binding.checkBox.setOnCheckedChangeListener((btn, isChecked) -> binding.btnActivatePrivateKey.setEnabled(isChecked));
    }

    private void createPrivateKey() {
        try {
            var preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);

            if (Objects.requireNonNull(binding.txtNewKeyAlias.getText()).length() == 0) {
                throw new IllegalArgumentException(getString(R.string.key_alias_may_not_be_empty));
            }

            var alias = binding.txtNewKeyAlias.getText().toString();

            if (cryptographyManager.keyStore.containsAlias(alias)) {
                throw new IllegalArgumentException(getString(R.string.key_alias_already_exists));
            }

            if (Objects.requireNonNull(binding.txtNewTransportPassword.getText()).length() < 10) {
                throw new IllegalArgumentException(getString(R.string.password_too_short));
            }

            if (!binding.txtNewTransportPassword.getText().toString().equals(Objects.requireNonNull(binding.txtRepeatTransportPassword.getText()).toString())) {
                throw new IllegalArgumentException(getString(R.string.password_mismatch));
            }

            var keyPair = CryptographyManager.generateKeyPair(binding.sw4096bit.isChecked() ? 4096 : 2048);
            var publicKey = (RSAPublicKey) keyPair.getPublic();
            var fingerprint = RSAUtils.getFingerprint(publicKey);
            var iv = AESUtil.generateIv();
            var salt = AESUtil.generateSalt(AESUtil.getSaltLength(preferences));
            var aesKeyLength = AESUtil.getKeyLength(preferences);
            var aesKeySpecIterations = AESUtil.getKeyspecIterations(preferences);
            var aesKeyAlgorithm = AESUtil.getAesKeyAlgorithm(preferences).keyAlgorithm;

            var aesSecretKey = AESUtil.getKeyFromPassword(
                    binding.txtNewTransportPassword.getText().toString().toCharArray(),
                    salt,
                    aesKeyAlgorithm,
                    aesKeyLength,
                    aesKeySpecIterations);

            var message = new AesEncryptedPrivateKeyTransfer(alias,
                    keyPair,
                    aesSecretKey,
                    iv,
                    salt,
                    AESUtil.getAesTransformationIdx(preferences),
                    AESUtil.getAesKeyAlgorithmIdx(preferences),
                    aesKeyLength,
                    aesKeySpecIterations).message;

            binding.checkBox.setEnabled(true);
            binding.checkBox.setChecked(false);

            binding.btnActivatePrivateKey.setOnClickListener(e -> {
                try {
                    cryptographyManager.importKey(
                            alias,
                            keyPair,
                            requireContext());

                    Toast.makeText(
                            requireContext(),
                            String.format(getString(R.string.key_successfully_activated), alias),
                            Toast.LENGTH_LONG).show();

                    //go back
                    Util.discardBackStack(this);
                } catch (Exception ex) {
                    Util.printStackTrace(ex);
                    Toast.makeText(requireContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

            //share HTML file
            var html = getKeyBackupHtml(alias, fingerprint, message);
            var fingerprintString = Util.byteArrayToHex(fingerprint).replaceAll("\\s", "_");
            var fileRecord = OmsFileProvider.create(requireContext(), "pk_" + fingerprintString + ".html", true);
            Files.write(fileRecord.path(), html.getBytes(StandardCharsets.UTF_8));
            fileRecord.path().toFile().deleteOnExit();

            var intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, fileRecord.uri());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setType("text/html");
            startActivity(Intent.createChooser(intent, String.format(getString(R.string.backup_file), alias)));

        } catch (Exception ex) {
            Util.printStackTrace(ex);
            Toast.makeText(requireContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getKeyBackupHtml(String alias, byte[] fingerprint, byte[] message) throws WriterException, IOException {
        var stringBuilder = new StringBuilder();

        var list = QRUtil.getQrSequence(MessageComposer.encodeAsOmsText(message),
                QRUtil.getChunkSize(preferences),
                QRUtil.getBarcodeSize(preferences));

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
            var bitmap = list.get(i);
            try (var baos = new ByteArrayOutputStream()) {
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

        var messageAsUrl = MessageComposer.encodeAsOmsText(message);
        var offset = 0;

        while (offset < messageAsUrl.length()) {
            var s = messageAsUrl.substring(offset, Math.min(offset + Util.BASE64_LINE_LENGTH, messageAsUrl.length()));
            stringBuilder.append(s).append("<br>");
            offset += Util.BASE64_LINE_LENGTH;
        }

        stringBuilder.append("</p><p>")
                .append("Message format: oms00_[base64 encoded data]")
                .append("</p><p>")
                .append("Data format: see https://github.com/stud0709/OneMoreSecret/blob/master/app/src/main/java/com/onemoresecret/crypto/AesEncryptedPrivateKeyTransfer.java")
                .append("</p></body></html>");

        return stringBuilder.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(menuProvider);
        binding = null;
    }

    private class PrivateKeyMenuProvider implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menuItemHelp) {
                Util.openUrl(R.string.new_private_key_md_url, requireContext());
            } else {
                return false;
            }
            return true;
        }
    }
}
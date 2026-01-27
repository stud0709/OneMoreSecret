package com.onemoresecret;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.selection.SelectionTracker;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.BTCAddress;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.EncryptedCryptoCurrencyAddress;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.RSAUtil;
import com.onemoresecret.databinding.FragmentCryptoCurrencyAddressGeneratorBinding;
import com.onemoresecret.qr.QRUtil;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CryptoCurrencyAddressGenerator extends Fragment {
    private FragmentCryptoCurrencyAddressGeneratorBinding binding;
    private KeyStoreListFragment keyStoreListFragment;
    private SharedPreferences preferences;
    private OutputFragment outputFragment;
    private final BitcoinAddressMenuProvider menuProvider = new BitcoinAddressMenuProvider();
    private final CryptographyManager cryptographyManager = new CryptographyManager();

    private Consumer<String> encryptWif;
    private Supplier<String> backupSupplier;
    private String address;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentCryptoCurrencyAddressGeneratorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(menuProvider);
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);
        binding.btnBackup.setEnabled(false);

        keyStoreListFragment = binding.fragmentContainerView.getFragment();
        outputFragment = binding.fragmentContainerView4.getFragment();

        binding.btnBackup.setOnClickListener(btn -> {
            try {
                var fileRecord = OmsFileProvider.create(requireContext(), address + "_backup.html", true);
                Files.write(fileRecord.path, backupSupplier.get().getBytes(StandardCharsets.UTF_8));
                fileRecord.path.toFile().deleteOnExit();

                var intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, fileRecord.uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setType("text/html");
                startActivity(Intent.createChooser(intent, String.format(getString(R.string.backup_file), address)));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        keyStoreListFragment.setRunOnStart(
                fragmentKeyStoreListBinding -> keyStoreListFragment
                        .getSelectionTracker()
                        .addObserver(new SelectionTracker.SelectionObserver<>() {
                            @Override
                            public void onSelectionChanged() {
                                super.onSelectionChanged();
                                if (keyStoreListFragment.getSelectionTracker().hasSelection()) {
                                    var selectedAlias = keyStoreListFragment.getSelectionTracker().getSelection().iterator().next();
                                    encryptWif.accept(selectedAlias);

                                } else {
                                    setBitcoinAddress();
                                }

                                binding.btnBackup.setEnabled(keyStoreListFragment.getSelectionTracker().hasSelection());
                            }
                        }));

        newBitcoinAddress();
    }

    private void newBitcoinAddress() {
        try {
            var btcKeyPair = BTCAddress.newKeyPair().toBTCKeyPair();
            address = btcKeyPair.btcAddressBase58();
            encryptWif = getEncryptWif(btcKeyPair);
        } catch (Exception e) {
            Util.printStackTrace(e);
            Toast.makeText(requireContext(),
                    e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }

        requireActivity().getMainExecutor().execute(() -> {
            setBitcoinAddress();

            if (keyStoreListFragment.getSelectionTracker().hasSelection()) {
                var selectedAlias = keyStoreListFragment.getSelectionTracker().getSelection().iterator().next();
                encryptWif.accept(selectedAlias);
            }
        });
    }

    private Consumer<String> getEncryptWif(BTCAddress.BTCKeyPair btcKeyPair) {
        return alias -> {
            try {
                var encrypted = new EncryptedCryptoCurrencyAddress(
                        MessageComposer.APPLICATION_BITCOIN_ADDRESS,
                        btcKeyPair.wif,
                        Objects.requireNonNull(cryptographyManager.getByAlias(alias, preferences)).getPublic(),
                        RSAUtil.getRsaTransformation(preferences),
                        AESUtil.getKeyLength(preferences),
                        AESUtil.getAesTransformation(preferences)).message;

                outputFragment.setMessage(MessageComposer.encodeAsOmsText(encrypted), getString(R.string.wif_encrypted));
                backupSupplier = getBackupSupplier(btcKeyPair.btcAddressBase58(), encrypted);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private Supplier<String> getBackupSupplier(String btcAddress, byte[] message) {
        return () -> {
            var stringBuilder = new StringBuilder();
            var omsText = MessageComposer.encodeAsOmsText(message);
            try {
                var list = QRUtil.getQrSequence(omsText,
                        QRUtil.getChunkSize(preferences),
                        QRUtil.getBarcodeSize(preferences));

                stringBuilder
                        .append("<html><body><h1>")
                        .append("OneMoreSecret Cold Wallet")
                        .append("</h1>")
                        .append("<p>This is a hard copy of your Bitcoin Address <b>")
                        .append(btcAddress)
                        .append("</b>:</p><p>");

                try (var baos = new ByteArrayOutputStream()) {
                    var bitmap = QRUtil.getQr(btcAddress, QRUtil.getBarcodeSize(preferences));
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                    baos.flush();
                    stringBuilder.append("<img src=\"data:image/png;base64,")
                            .append(Base64.getEncoder().encodeToString(baos.toByteArray()))
                            .append("\" style=\"width:200px;height:200px;\">");
                }

                stringBuilder.append("</p>The above QR code contains the public bitcoin address, ")
                        .append("use a regular QR code scanner to read it.</p><p>The private key is encrypted, ")
                        .append("scan the following QR code sequence <b>with OneMoreSecret</b> to access it:</p><p>");

                for (int i = 0; i < list.size(); i++) {
                    var bitmap = list.get(i);
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
                        .append("</p><p>")
                        .append("The same as text:")
                        .append("&nbsp;")
                        .append("</p><p style=\"font-family:monospace;\">");

                var offset = 0;

                while (offset < omsText.length()) {
                    var s = omsText.substring(offset, Math.min(offset + Util.BASE64_LINE_LENGTH, omsText.length()));
                    stringBuilder.append(s).append("<br>");
                    offset += Util.BASE64_LINE_LENGTH;
                }

                stringBuilder.append("</p><p>")
                        .append("Message format: oms00_[base64 encoded data]")
                        .append("</p><p>")
                        .append("Data format: see https://github.com/stud0709/OneMoreSecret/blob/master/app/src/main/java/com/onemoresecret/crypto/EncryptedCryptoCurrencyAddress.java")
                        .append("</p></body></html>");

                return stringBuilder.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private void setBitcoinAddress() {
        binding.textViewAddress.setText(address);
        outputFragment.setMessage(address, getString(R.string.public_address));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(menuProvider);
        binding = null;
    }

    private class BitcoinAddressMenuProvider implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.menu_crypto_address_generator, menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menuItemNewAddress) {
                newBitcoinAddress();
            } else if (menuItem.getItemId() == R.id.menuItemCryptoAdrGenHelp) {
                Util.openUrl(R.string.crypto_address_generator_url, requireContext());
            } else {
                return false;
            }

            return true;
        }
    }
}
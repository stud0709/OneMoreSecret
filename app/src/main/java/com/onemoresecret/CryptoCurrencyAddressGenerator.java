package com.onemoresecret;

import android.content.Context;
import android.content.SharedPreferences;
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
import com.onemoresecret.crypto.RSAUtils;
import com.onemoresecret.databinding.FragmentCryptoCurrencyAddressGeneratorBinding;

import java.security.interfaces.RSAPublicKey;
import java.util.function.Consumer;

public class CryptoCurrencyAddressGenerator extends Fragment {
    private FragmentCryptoCurrencyAddressGeneratorBinding binding;
    private KeyStoreListFragment keyStoreListFragment;
    private SharedPreferences preferences;
    private OutputFragment outputFragment;
    private final BitcoinAddressMenuProvider menuProvider = new BitcoinAddressMenuProvider();
    private final CryptographyManager cryptographyManager = new CryptographyManager();

    private Consumer<String> encryptWif;
    private Runnable setBitcoinAddress;

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

        keyStoreListFragment = binding.fragmentContainerView.getFragment();
        outputFragment = binding.fragmentContainerView4.getFragment();

        keyStoreListFragment.setRunOnStart(
                fragmentKeyStoreListBinding -> keyStoreListFragment
                        .getSelectionTracker()
                        .addObserver(new SelectionTracker.SelectionObserver<String>() {
                            @Override
                            public void onSelectionChanged() {
                                super.onSelectionChanged();
                                if (keyStoreListFragment.getSelectionTracker().hasSelection()) {
                                    var selectedAlias = keyStoreListFragment.getSelectionTracker().getSelection().iterator().next();
                                    encryptWif.accept(selectedAlias);
                                } else {
                                    setBitcoinAddress.run();
                                }
                            }
                        }));

        newBitcoinAddress();
    }

    private void newBitcoinAddress() {
        try {
            var btcKeyPair = BTCAddress.newKeyPair().toBTCKeyPair();
            setBitcoinAddress = getSetBitcoinAddress(btcKeyPair.getBtcAddressBase58());
            encryptWif = getEncryptWif(btcKeyPair.wif());
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(),
                    e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }

        requireActivity().getMainExecutor().execute(() -> {
            setBitcoinAddress.run();

            if (keyStoreListFragment.getSelectionTracker().hasSelection()) {
                var selectedAlias = keyStoreListFragment.getSelectionTracker().getSelection().iterator().next();
                encryptWif.accept(selectedAlias);
            }
        });
    }

    private Consumer<String> getEncryptWif(byte[] wif) {
        return alias -> {
            try {
                var encrypted = MessageComposer.encodeAsOmsText(
                        new EncryptedCryptoCurrencyAddress(
                                MessageComposer.APPLICATION_BITCOIN_ADDRESS,
                                wif,
                                (RSAPublicKey) cryptographyManager.getCertificate(alias).getPublicKey(),
                                RSAUtils.getRsaTransformationIdx(preferences),
                                AESUtil.getKeyLength(preferences),
                                AESUtil.getAesTransformationIdx(preferences)).getMessage());

                outputFragment.setMessage(encrypted, getString(R.string.wif_encrypted));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private Runnable getSetBitcoinAddress(String address) {
        return () -> {
            binding.textViewAddress.setText(address);
            outputFragment.setMessage(address, getString(R.string.public_btc_address));
        };
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
            menuInflater.inflate(R.menu.menu_btc_address_generator, menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menuItemNewAddress) {
                newBitcoinAddress();
            } else {
                return false;
            }

            return true;
        }
    }
}
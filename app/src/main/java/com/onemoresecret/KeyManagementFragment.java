package com.onemoresecret;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.selection.SelectionTracker;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.databinding.FragmentKeyManagementBinding;

import java.security.KeyStoreException;
import java.util.Base64;

public class KeyManagementFragment extends Fragment {
    private FragmentKeyManagementBinding binding;
    private final KeyEntryMenuProvider menuProvider = new KeyEntryMenuProvider();
    private KeyStoreListFragment keyStoreListFragment;
    private final CryptographyManager cryptographyManager = new CryptographyManager();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {


        binding = FragmentKeyManagementBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(menuProvider);
        keyStoreListFragment = binding.fragmentContainerView.getFragment();
        keyStoreListFragment.setRunOnStart(
                fragmentKeyStoreListBinding -> keyStoreListFragment.
                        getSelectionTracker()
                        .addObserver(new SelectionTracker.SelectionObserver<>() {
                            @Override
                            public void onSelectionChanged() {
                                super.onSelectionChanged();
                                requireActivity().invalidateOptionsMenu();
                                if (keyStoreListFragment.getSelectionTracker().hasSelection()) {
                                    var alias = getSelectedAlias();
                                    keyStoreListFragment
                                            .getOutputFragment()
                                            .setMessage(getPublicKeyMessage(alias), String.format(getString(R.string.share_public_key_title), alias));
                                } else {
                                    keyStoreListFragment.getOutputFragment().setMessage(null, null);
                                }
                            }
                        }));
    }

    private String getSelectedAlias() {
        return keyStoreListFragment.getSelectionTracker().getSelection().iterator().next();
    }

    private String getPublicKeyMessage(String alias) {
        try {
            var bArr = cryptographyManager.getCertificate(alias).getPublicKey().getEncoded();
            return Base64.getEncoder().encodeToString(bArr);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(menuProvider);
        binding = null;
    }

    private class KeyEntryMenuProvider implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.menu_key_management, menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menuItemNewPrivateKey) {
                NavHostFragment.findNavController(KeyManagementFragment.this)
                        .navigate(R.id.action_keyManagementFragment_to_newPrivateKeyFragment, null);
                return true;
            }

            if (!keyStoreListFragment.getSelectionTracker().hasSelection()) return false;

            var alias = getSelectedAlias();

            if (menuItem.getItemId() == R.id.menuItemDeleteKeyEntry) {
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.delete_private_key)
                        .setMessage(String.format(getString(R.string.ok_to_delete), alias))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            try {
                                cryptographyManager.deleteKey(alias);
                                Toast.makeText(getContext(), String.format(getString(R.string.key_deleted), alias), Toast.LENGTH_LONG).show();
                                keyStoreListFragment.onItemRemoved(alias);
                            } catch (KeyStoreException e) {
                                e.printStackTrace();
                                Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }).setNegativeButton(android.R.string.cancel, null).show();
            } else if (menuItem.getItemId() == R.id.menuItemKeyMgtHelp) {
                Util.openUrl(R.string.key_management_md_url, requireContext());
            } else {
                return false;
            }

            return true;
        }

        @Override
        public void onPrepareMenu(@NonNull Menu menu) {
            menu.setGroupVisible(R.id.group_key_all, keyStoreListFragment.getSelectionTracker().hasSelection());
            MenuProvider.super.onPrepareMenu(menu);
        }
    }
}
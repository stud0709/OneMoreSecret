package com.onemoresecret;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.selection.SelectionTracker;

import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.EncryptedMessage;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.RSAUtils;
import com.onemoresecret.databinding.FragmentEncryptTextBinding;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class EncryptTextFragment extends Fragment {
    private FragmentEncryptTextBinding binding;
    private KeyStoreListFragment keyStoreListFragment;
    private OutputFragment outputFragment;
    private final CryptographyManager cryptographyManager = new CryptographyManager();
    private Consumer<String> encryptPhrase;
    private Runnable setPhrase;
    private final AtomicBoolean textChangeListenerActive = new AtomicBoolean(true);
    private SharedPreferences preferences;
    private final EncMenuProvider menuProvider = new EncMenuProvider();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentEncryptTextBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);
        keyStoreListFragment = binding.fragmentContainerView.getFragment();
        outputFragment = binding.fragmentContainerView5.getFragment();
        requireActivity().addMenuProvider(menuProvider);

        //based on pre-launch test
        //java.lang.IllegalStateException: Fragment EncryptTextFragment does not have any arguments.
        var text = getArguments() == null ? "" : getArguments().getString(QRFragment.ARG_TEXT);

        binding.editTextPhrase.setText(text);

        setPhrase = getSetPhrase(text);
        encryptPhrase = getEncryptPhrase(text);

        keyStoreListFragment.setRunOnStart(
                fragmentKeyStoreListBinding -> keyStoreListFragment
                        .getSelectionTracker()
                        .addObserver(new SelectionTracker.SelectionObserver<>() {
                            @Override
                            public void onSelectionChanged() {
                                super.onSelectionChanged();
                                if (keyStoreListFragment.getSelectionTracker().hasSelection()) {
                                    var selectedAlias = keyStoreListFragment.getSelectionTracker().getSelection().iterator().next();
                                    if (encryptPhrase != null)
                                        encryptPhrase.accept(selectedAlias);
                                } else {
                                    if (setPhrase != null)
                                        setPhrase.run();
                                }
                            }
                        }));


        binding.editTextPhrase.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!textChangeListenerActive.get()) return;

                setPhrase = getSetPhrase(s.toString());
                encryptPhrase = getEncryptPhrase(s.toString());
            }
        });
    }

    private Runnable getSetPhrase(String pwd) {
        return () -> {
            textChangeListenerActive.set(false);
            binding.editTextPhrase.setText(pwd);
            textChangeListenerActive.set(true);

            binding.editTextPhrase.setEnabled(true);
            outputFragment.setMessage(pwd, "Unprotected phrase");
        };
    }

    private Consumer<String> getEncryptPhrase(String phrase) {
        return alias -> {
            try {
                var encrypted = MessageComposer.encodeAsOmsText(
                        new EncryptedMessage(phrase.getBytes(StandardCharsets.UTF_8),
                                (RSAPublicKey) cryptographyManager.getCertificate(alias).getPublicKey(),
                                RSAUtils.getRsaTransformationIdx(preferences),
                                AESUtil.getKeyLength(preferences),
                                AESUtil.getAesTransformationIdx(preferences)).getMessage());

                textChangeListenerActive.set(false);
                binding.editTextPhrase.setText(encrypted);
                textChangeListenerActive.set(true);

                binding.editTextPhrase.setEnabled(false);
                outputFragment.setMessage(encrypted, getString(R.string.encrypted_password));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(menuProvider);
        binding = null;
    }

    private class EncMenuProvider implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menuItemHelp) {
                Util.openUrl(R.string.encrypt_text_md_url, requireContext());
            } else {
                return false;
            }

            return true;
        }
    }
}
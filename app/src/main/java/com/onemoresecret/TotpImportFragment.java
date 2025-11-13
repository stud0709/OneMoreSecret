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
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.OneTimePassword;
import com.onemoresecret.crypto.RSAUtils;
import com.onemoresecret.crypto.TotpUriTransfer;
import com.onemoresecret.databinding.FragmentTotpImportBinding;

import java.security.interfaces.RSAPublicKey;
import java.util.Objects;

public class TotpImportFragment extends Fragment {
    private static final String TAG = TotpImportFragment.class.getSimpleName();
    private FragmentTotpImportBinding binding;
    private KeyStoreListFragment keyStoreListFragment;
    private OutputFragment outputFragment;
    private final CryptographyManager cryptographyManager = new CryptographyManager();
    private final OtpMenuProvider menuProvider = new OtpMenuProvider();
    private SharedPreferences preferences;
    private TotpFragment totpFragment;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentTotpImportBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(menuProvider);

        keyStoreListFragment = binding.fragmentContainerView.getFragment();
        outputFragment = binding.fragmentContainerView3.getFragment();

        totpFragment = binding.fragmentTotp.getFragment();
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);

        var message = requireArguments().getByteArray("MESSAGE");

        var otp = new OneTimePassword(new String(message));

        try {
            if (!otp.getValid()) {
                throw new IllegalArgumentException("Invalid scheme or authority");
            }

            totpFragment.init(otp, this, code -> {
                var hasSelection =  keyStoreListFragment.getSelectionTracker().hasSelection();

                totpFragment.setTotpText(code);

                if (!hasSelection)
                    outputFragment.setMessage(code, "One-Time Password");
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
                                        var encrypted = encrypt(selectedAlias, message);
                                        outputFragment.setMessage(encrypted, "Encrypted OTP Configuration");
                                    } else {
                                        outputFragment.setMessage(null, null);
                                    }
                                    totpFragment.refresh();
                                }
                            }));

        } catch (Exception ex) {
            Util.printStackTrace(ex);
            Toast.makeText(getContext(), Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()), Toast.LENGTH_LONG).show();
            Util.discardBackStack(this);
        }
    }

    private String encrypt(String alias, byte[] message) {
        try {
            return MessageComposer.encodeAsOmsText(
                    new TotpUriTransfer(message,
                            (RSAPublicKey) cryptographyManager.getCertificate(alias).getPublicKey(),
                            RSAUtils.getRsaTransformationIdx(preferences),
                            AESUtil.getKeyLength(preferences),
                            AESUtil.getAesTransformationIdx(preferences)).message);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class OtpMenuProvider implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu);
        }

        @Override
        public void onPrepareMenu(@NonNull Menu menu) {
            MenuProvider.super.onPrepareMenu(menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menuItemHelp) {
                Util.openUrl(R.string.totp_import_md_url, requireContext());
            } else {
                return false;
            }

            return true;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(menuProvider);
        binding = null;
    }
}
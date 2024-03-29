package com.onemoresecret;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.recyclerview.selection.SelectionTracker;

import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.EncryptedFile;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.RSAUtils;
import com.onemoresecret.databinding.FragmentFileEncryptionBinding;

import java.security.interfaces.RSAPublicKey;

public class FileEncryptionFragment extends Fragment {
    private FragmentFileEncryptionBinding binding;
    private KeyStoreListFragment keyStoreListFragment;
    private final CryptographyManager cryptographyManager = new CryptographyManager();
    private SharedPreferences preferences;
    private static final String TAG = FileEncryptionFragment.class.getSimpleName();
    private Uri uri;

    private final FileEncryptionMenuProvider menuProvider = new FileEncryptionMenuProvider();


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFileEncryptionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(menuProvider);
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(menuProvider);

        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);
        binding.btnEncrypt.setEnabled(false);
        keyStoreListFragment = binding.fragmentContainerView.getFragment();

        uri = getArguments().getParcelable(QRFragment.ARG_URI);

        getContext().getMainExecutor().execute(() ->
                ((FileInfoFragment) binding.fragmentContainerView6.getFragment()).setValues(
                        getArguments().getString(QRFragment.ARG_FILENAME),
                        getArguments().getInt(QRFragment.ARG_FILESIZE)));


        keyStoreListFragment.setRunOnStart(
                fragmentKeyStoreListBinding -> keyStoreListFragment
                        .getSelectionTracker()
                        .addObserver(new SelectionTracker.SelectionObserver<>() {
                            @Override
                            public void onSelectionChanged() {
                                super.onSelectionChanged();
                                binding.btnEncrypt.setEnabled(keyStoreListFragment.getSelectionTracker().hasSelection());
                            }
                        }));

        binding.btnEncrypt.setOnClickListener(v -> encrypt());
    }

    private void encrypt() {
        var selectedAlias = keyStoreListFragment.getSelectionTracker().getSelection().iterator().next();

        try {
            var oFileRecord = OmsFileProvider.create(requireContext(),
                    getArguments().getString(QRFragment.ARG_FILENAME) + "." + MessageComposer.OMS_FILE_TYPE,
                    true);

            EncryptedFile.create(requireContext().getContentResolver().openInputStream(uri),
                    oFileRecord.path().toFile(),
                    (RSAPublicKey) cryptographyManager.getCertificate(selectedAlias).getPublicKey(),
                    RSAUtils.getRsaTransformationIdx(preferences),
                    AESUtil.getKeyLength(preferences),
                    AESUtil.getAesTransformationIdx(preferences));

            var intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_STREAM, oFileRecord.uri());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(intent);
        } catch (Exception ex) {
            ex.printStackTrace();
            requireActivity().getMainExecutor().execute(() -> Toast.makeText(requireContext(),
                    String.format("%s: %s", ex.getClass().getSimpleName(), ex.getMessage()),
                    Toast.LENGTH_LONG).show());
        }
    }

    private class FileEncryptionMenuProvider implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menuItemHelp) {
                Util.openUrl(R.string.encrypt_file_md_url, requireContext());
            } else {
                return false;
            }

            return true;
        }
    }
}
package com.onemoresecret;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.selection.SelectionTracker;

import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.EncryptedFile;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.RSAUtils;
import com.onemoresecret.databinding.FragmentFileEncryptionBinding;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.interfaces.RSAPublicKey;
import java.util.Locale;
import java.util.Objects;

public class FileEncryptionFragment extends Fragment {
    private FragmentFileEncryptionBinding binding;
    private KeyStoreListFragment keyStoreListFragment;
    private final CryptographyManager cryptographyManager = new CryptographyManager();
    private SharedPreferences preferences;
    private static final String TAG = FileEncryptionFragment.class.getSimpleName();
    private Uri uri;
    private String filename;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFileEncryptionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);
        binding.btnEncrypt.setEnabled(false);
        keyStoreListFragment = binding.fragmentContainerView.getFragment();

        uri = getArguments().getParcelable("URI");

        try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
            Objects.requireNonNull(cursor);
            cursor.moveToFirst();

            var sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            var nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);

            var fileSize = cursor.getLong(sizeIndex);
            filename = cursor.getString(nameIndex);

            binding.textView4.setText(filename);
            binding.textView15.setText(String.format(Locale.getDefault(), "%.3f KB", fileSize / 1024D));
        } catch (Exception ex) {
            ex.printStackTrace();
            requireContext().getMainExecutor().execute(() -> {
                Toast.makeText(requireContext(), "Could not access file", Toast.LENGTH_LONG).show();
                NavHostFragment.findNavController(FileEncryptionFragment.this).popBackStack();
            });
            return;
        }

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
            var oFileRecord = OmsFileProvider.create(requireContext(), filename + "." + MessageComposer.OMS_MIME_TYPE, true);

            EncryptedFile.create(requireContext().getContentResolver().openInputStream(uri),
                    oFileRecord.path().toFile(),
                    (RSAPublicKey) cryptographyManager.getCertificate(selectedAlias).getPublicKey(),
                    RSAUtils.getRsaTransformationIdx(preferences),
                    AESUtil.getKeyLength(preferences),
                    AESUtil.getAesTransformationIdx(preferences));

            var intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/" + MessageComposer.OMS_MIME_TYPE);
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
}
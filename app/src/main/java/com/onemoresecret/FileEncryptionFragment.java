package com.onemoresecret;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.selection.SelectionTracker;

import com.onemoresecret.databinding.FragmentFileEncryptionBinding;

import java.io.File;
import java.util.Locale;

public class FileEncryptionFragment extends Fragment {
    private FragmentFileEncryptionBinding binding;
    private KeyStoreListFragment keyStoreListFragment;
    private String filename;
    private File file;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFileEncryptionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnEncrypt.setEnabled(false);
        keyStoreListFragment = binding.fragmentContainerView.getFragment();

        var uri = (Uri) (getArguments() == null ? null : getArguments().getParcelable("URI"));

        try (Cursor cursor = requireActivity().getContentResolver().query(uri, null, null, null, null)) {
            cursor.moveToFirst();

            var sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            var nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);

            var fileSize = cursor.getLong(sizeIndex);
            filename = cursor.getString(nameIndex);

            binding.textView4.setText(filename);
            binding.textView15.setText(String.format(Locale.getDefault(), "%.3f KB", fileSize / 1024D));

            file = new File(uri.getPath());
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
                                if (keyStoreListFragment.getSelectionTracker().hasSelection()) {
                                    //todo
                                } else {
                                    //todo
                                }
                            }
                        }));

        binding.btnEncrypt.setOnClickListener(v -> encrypt());
    }

    private void encrypt() {
        var selectedAlias = keyStoreListFragment.getSelectionTracker().getSelection().iterator().next();

        //todo
    }
}
package com.onemoresecret;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.onemoresecret.databinding.FragmentEncryptTextBinding;
import com.onemoresecret.databinding.FragmentFileInfoBinding;

import java.util.Locale;

public class FileInfoFragment extends Fragment {
    private FragmentFileInfoBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFileInfoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    public void setValues(String filename, int filesize) {
        if (binding == null) return;
        binding.textViewFilenameValue.setText(filename);
        binding.textViewFileSizeValue.setText(String.format(Locale.getDefault(), "%.3f KB", filesize / 1024D));
    }
}
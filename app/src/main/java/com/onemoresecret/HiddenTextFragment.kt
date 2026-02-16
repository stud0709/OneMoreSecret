package com.onemoresecret;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.onemoresecret.databinding.FragmentHiddenTextBinding;

public class HiddenTextFragment extends Fragment {
    private FragmentHiddenTextBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentHiddenTextBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void setText(String text) {
        binding.textViewMessage.setText(text);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.textViewMessage.setText(getString(R.string.hidden_text));
        binding = null;
    }
}
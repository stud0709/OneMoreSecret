package com.onemoresecret;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.onemoresecret.databinding.FragmentPinSetupBinding;
import com.onemoresecret.databinding.FragmentQrBinding;

public class PinSetupFragment extends Fragment {
    private FragmentPinSetupBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentPinSetupBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
}
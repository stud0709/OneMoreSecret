package com.onemoresecret;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.onemoresecret.databinding.FragmentCryptoCurrencyAddressBinding;

public class CryptoCurrencyAddressFragment extends Fragment {
    private FragmentCryptoCurrencyAddressBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCryptoCurrencyAddressBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void setValue(String publicAddress) {
        if (binding == null) return;
        binding.textViewPublicAddress.setText(publicAddress);
    }
}
package com.onemoresecret;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.onemoresecret.databinding.FragmentWiFiConnectionUpdateBinding;

public class WiFiConnectionUpdateFragment extends Fragment {
    private FragmentWiFiConnectionUpdateBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentWiFiConnectionUpdateBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.txtResponseCodeUpdate.setText("");
        binding.btnClearPairing.setOnClickListener(e -> {
            ((MainActivity) requireActivity()).setWiFiComm(null);
            Util.discardBackStack(WiFiConnectionUpdateFragment.this);
        });
    }

    public void setResponseCode(String responseCode) {
        binding.txtResponseCodeUpdate.setText(responseCode);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
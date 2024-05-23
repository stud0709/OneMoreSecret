package com.onemoresecret;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.onemoresecret.databinding.FragmentWiFiPairingBinding;
import com.onemoresecret.databinding.FragmentWiFiPortUpdateBinding;

public class WiFiPortUpdateFragment extends Fragment {
    private FragmentWiFiPortUpdateBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentWiFiPortUpdateBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.txtNewPort.setText("");
        binding.btnClearPairing.setOnClickListener(e -> {
            ((MainActivity) requireActivity()).setWiFiComm(null);
            Util.discardBackStack(WiFiPortUpdateFragment.this);
        });
    }

    public void setPort(String port) {
        binding.txtNewPort.setText(port);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}